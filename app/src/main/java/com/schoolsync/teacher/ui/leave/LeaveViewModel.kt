package com.schoolsync.teacher.ui.leave

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.LeaveBalance as ModelLeaveBalance
import com.schoolsync.teacher.data.model.LeaveStatus as ModelLeaveStatus
import com.schoolsync.teacher.data.model.firestore.LeaveApplicationDoc
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.LeaveFirestoreRepository
import com.schoolsync.teacher.util.RoleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaveBalance(
    val type: String,
    val used: Double,
    val total: Double,
    // Live allocation from the school leave type (days_per_year) and any
    // carried-forward days from the BAL doc. `total` = allocated + carried.
    val allocated: Double = total,
    val carried: Double = 0.0,
    val remaining: Double = total - used
)

data class LeaveRequest(
    val requestId: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val reason: String,
    val status: LeaveStatus,
    val appliedDate: String = "",
    val days: Double = 1.0,          // MEDIUM #5: Double so half-day = 0.5
    val remarks: String = ""
)

enum class LeaveStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    companion object {
        fun fromModel(model: ModelLeaveStatus): LeaveStatus = when (model) {
            ModelLeaveStatus.PENDING -> PENDING
            ModelLeaveStatus.APPROVED -> APPROVED
            ModelLeaveStatus.REJECTED -> REJECTED
            ModelLeaveStatus.CANCELLED -> CANCELLED
        }

        fun fromString(status: String): LeaveStatus = when (status.lowercase()) {
            "approved" -> APPROVED
            "rejected" -> REJECTED
            "cancelled" -> CANCELLED
            else -> PENDING
        }
    }
}

data class LeaveUiState(
    val balances: List<LeaveBalance> = emptyList(),
    val leaveHistory: List<LeaveRequest> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,            // H11: history/general load error
    val balancesError: String? = null,    // H11: balance / leave-type load error
    val studentLeavesError: String? = null, // H11: student-leave load error
    val showApplyDialog: Boolean = false,
    val applyLeaveType: String = "",
    val applyStartDate: String = "",
    val applyEndDate: String = "",
    val applyReason: String = "",
    val applyHalfDay: Boolean = false,
    val applyHalfDayPeriod: String = "AM",   // "AM" | "PM"
    val isSubmitting: Boolean = false,
    val leaveTypes: List<String> = emptyList(),
    // Phase 10e: Student Leave tab
    val selectedTab: Int = 0,               // 0 = My Leave, 1 = Student Leave
    val isClassTeacher: Boolean = false,     // only class teachers see Student Leave tab
    val studentLeaves: List<LeaveApplicationDoc> = emptyList(),
    val isLoadingStudentLeaves: Boolean = false,
    val processingLeaveId: String? = null    // which leave is being approved/rejected
)

sealed class LeaveEvent {
    data class SubmitSuccess(val message: String) : LeaveEvent()
    data class SubmitError(val message: String) : LeaveEvent()
}

@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val leaveFirestoreRepo: LeaveFirestoreRepository,
    private val teacherRepository: TeacherRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "LeaveVM"
    }

    private val _uiState = MutableStateFlow(LeaveUiState())
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LeaveEvent>()
    val events = _events.asSharedFlow()

    private var cachedAssignments: List<ClassAssignment> = emptyList()

    // CR-3 cross-system: name → paid map captured at loadLeaveData time so
    // submitLeaveRequest can snapshot the paid flag on the leave doc.
    private val leaveTypeNameToPaidCache = mutableMapOf<String, Boolean>()

    // MEDIUM #9: throttle the ON_RESUME full-reload. Seeded to "now" at
    // construction so the first resume right after init doesn't re-run the
    // multi-read load that init already kicked off.
    private var lastResumeRefresh: Long = System.currentTimeMillis()

    init {
        loadLeaveData()
        checkClassTeacherStatus()
    }

    fun loadLeaveData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, balancesError = null) }
            // H11: capture the previously-swallowed leave-type / balance load
            // failures so the UI can show an error+retry instead of a blank
            // "not configured" panel.
            var panelError: String? = null
            try {
                // Resolve staff gender once up-front so we can use it
                // for BOTH the Apply Leave type dropdown and the balances
                // list. Previously the gender filter only applied to
                // balances, so a male teacher still saw "Maternity Leave"
                // in the Apply dialog.
                val schoolCodeForLookup = tokenManager.schoolCode.firstOrNull()
                    ?: tokenManager.schoolId.firstOrNull() ?: ""
                val teacherIdForLookup = tokenManager.userId.firstOrNull() ?: ""
                val staffGenderShared: String = try {
                    if (schoolCodeForLookup.isNotBlank() && teacherIdForLookup.isNotBlank()) {
                        leaveFirestoreRepo.getStaffGender("${schoolCodeForLookup}_${teacherIdForLookup}")
                            ?.lowercase() ?: ""
                    } else ""
                } catch (_: Exception) { "" }

                // Load leave types from Firestore schools doc.
                //
                // Two separate concerns here:
                //   1. `leaveTypeIdToName` — used to resolve a typeId
                //      (e.g. LT0004) into a display name on the Balance
                //      list. Must include EVERY type the school has ever
                //      defined, including currently-inactive ones, or a
                //      legacy BAL entry against a retired type renders as
                //      its raw ID ("LT0004") in the UI.
                //   2. `leaveTypeNames` — used to populate the Apply
                //      dialog's Type dropdown. Filtered to Active types
                //      and the staff member's gender.
                var leaveTypeNames = emptyList<String>()
                var leaveTypeIdToName = mutableMapOf<String, String>()
                // Raw school leaveTypes map, held so the balance panel can
                // derive `allocated` LIVE from each type's days_per_year
                // instead of trusting the stored BAL doc's allocation.
                var schoolLeaveTypesRaw: Map<String, Any>? = null
                // CR-3 cross-system: re-populate class-level cache from fresh
                // school config every loadLeaveData; submit reads it later.
                leaveTypeNameToPaidCache.clear()
                val leaveTypeNameToPaid = leaveTypeNameToPaidCache
                try {
                    if (schoolCodeForLookup.isNotBlank()) {
                        val schoolDoc = leaveFirestoreRepo.getSchoolLeaveTypes(schoolCodeForLookup)
                        schoolLeaveTypesRaw = schoolDoc
                        if (schoolDoc != null) {
                            // Pass 1: build the FULL id → name map (no
                            // status / gender filter) for balance lookup.
                            for ((id, v) in schoolDoc) {
                                if (v is Map<*, *>) {
                                    val name = v["name"]?.toString() ?: v["code"]?.toString()
                                    if (!name.isNullOrBlank()) {
                                        leaveTypeIdToName[id] = name
                                        // Read paid flag (accepts bool, "true", "1")
                                        val paidRaw = v["paid"]
                                        leaveTypeNameToPaid[name] = when (paidRaw) {
                                            is Boolean -> paidRaw
                                            is String  -> paidRaw == "true" || paidRaw == "1"
                                            is Number  -> paidRaw.toInt() != 0
                                            else       -> true   // safe default
                                        }
                                    }
                                }
                            }
                            // Pass 2: pick the Apply-dropdown subset.
                            leaveTypeNames = schoolDoc.mapNotNull { (_, v) ->
                                if (v is Map<*, *>) {
                                    val name = v["name"]?.toString() ?: v["code"]?.toString()
                                    val status = v["status"]?.toString() ?: "Active"
                                    if (status.equals("Active", ignoreCase = true) && !name.isNullOrBlank()) {
                                        if (staffGenderShared == "male" && name.contains("Maternity", true)) return@mapNotNull null
                                        if (staffGenderShared == "female" && name.contains("Paternity", true)) return@mapNotNull null
                                        name
                                    } else null
                                } else null
                            }
                        }
                    }
                } catch (e: Exception) {
                    panelError = e.message ?: "Failed to load leave types"
                }
                if (leaveTypeNames.isNotEmpty()) {
                    _uiState.update { it.copy(
                        leaveTypes = leaveTypeNames,
                        applyLeaveType = if (it.applyLeaveType.isBlank()) leaveTypeNames.first() else it.applyLeaveType
                    ) }
                }

                // Load balance — one card per APPLICABLE leave type, with
                // allocation derived LIVE from the school's leaveTypes config
                // (days_per_year). The BAL doc only supplies used/carried; it
                // is no longer the source of the type list or the allocation.
                // Effect: a teacher with no BAL doc still sees every type, and
                // an admin editing days_per_year is reflected immediately.
                var balances = emptyList<LeaveBalance>()
                try {
                    // schoolCode stores the schoolId path (SCH_xxx), schoolId may store legacy name
                    val schoolCode = tokenManager.schoolCode.firstOrNull()
                        ?: tokenManager.schoolId.firstOrNull() ?: ""
                    val teacherId = tokenManager.userId.firstOrNull() ?: ""
                    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()

                    // Overlay doc for used/carried (may be absent → all zero).
                    var balDoc: Map<String, Any>? = null
                    if (schoolCode.isNotBlank() && teacherId.isNotBlank()) {
                        balDoc = leaveFirestoreRepo.getBalanceDoc("${schoolCode}_BAL_${teacherId}_$year")
                        if (balDoc == null) {
                            val altSchool = tokenManager.schoolId.firstOrNull() ?: ""
                            if (altSchool.isNotBlank() && altSchool != schoolCode) {
                                balDoc = leaveFirestoreRepo.getBalanceDoc("${altSchool}_BAL_${teacherId}_$year")
                            }
                        }
                    }

                    fun asDouble(v: Any?): Double = when (v) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    val priorityOrder = listOf("Casual", "Sick", "Earned", "Academic", "Comp", "Paternity", "Maternity")
                    val schoolTypes = schoolLeaveTypesRaw
                    if (schoolTypes != null) {
                        balances = schoolTypes.mapNotNull { (typeId, v) ->
                            if (v !is Map<*, *>) return@mapNotNull null
                            val name = v["name"]?.toString() ?: v["code"]?.toString()
                            if (name.isNullOrBlank()) return@mapNotNull null
                            // Status filter — same as the Apply dropdown.
                            val status = v["status"]?.toString() ?: "Active"
                            if (!status.equals("Active", ignoreCase = true)) return@mapNotNull null
                            // Gender filter — identical name-based rule the VM
                            // already applies for the dropdown.
                            if (staffGenderShared == "male" && name.contains("Maternity", true)) return@mapNotNull null
                            if (staffGenderShared == "female" && name.contains("Paternity", true)) return@mapNotNull null

                            // LIVE allocation from the type; used/carried from BAL.
                            val allocated = asDouble(v["days_per_year"])
                            val entry = balDoc?.get(typeId) as? Map<*, *>
                            val carried = asDouble(entry?.get("carried"))
                            val used = asDouble(entry?.get("used"))
                            val total = allocated + carried
                            LeaveBalance(
                                type = name,
                                used = used,
                                total = total,
                                allocated = allocated,
                                carried = carried,
                                remaining = total - used
                            )
                        }.sortedBy { bal ->
                            val idx = priorityOrder.indexOfFirst { bal.type.contains(it, ignoreCase = true) }
                            if (idx >= 0) idx else 99
                        }
                    }
                } catch (e: Exception) {
                    panelError = e.message ?: "Failed to load leave balances"
                }

                // Firestore-only contract: empty result == no balances. The
                // legacy RTDB fallback (`leaveRepository.getLeaveBalance()`)
                // was removed because admin no longer writes that path —
                // calling it would surface stale or seeded data and violate
                // the absolute NO-RTDB rule. `LeaveRepository` itself stays
                // as orphaned code per the prior "dead code stays" decision.
                //
                // Apply-dropdown alignment: the dropdown should only offer
                // the leave types this teacher has been allocated. Earlier
                // we surfaced every Active type from the school config,
                // which produced 8 options against 5 balance rows and
                // confused the user. Re-derive `leaveTypes` from the
                // computed balances list so the two views always match.
                // If balances is empty (no allocation) we keep the
                // school-config list as a fallback so the dropdown isn't
                // empty during initial setup.
                val dropdownTypes = if (balances.isNotEmpty()) {
                    balances.map { it.type }
                } else {
                    leaveTypeNames
                }
                _uiState.update { st ->
                    st.copy(
                        balances = balances,
                        // H11: only surface a balance-panel error when we truly
                        // have nothing to show; a partial failure that still
                        // produced balances shouldn't nag the user.
                        balancesError = if (balances.isEmpty()) panelError else null,
                        leaveTypes = dropdownTypes,
                        applyLeaveType = if (st.applyLeaveType in dropdownTypes) st.applyLeaveType
                            else (dropdownTypes.firstOrNull() ?: "")
                    )
                }

                // Load history — Firestore-first
                leaveFirestoreRepo.getLeaveHistory().fold(
                    onSuccess = { fsDocs ->
                        val requests = fsDocs.map { doc ->
                            LeaveRequest(
                                requestId = doc.id,
                                type = doc.leaveType,
                                startDate = doc.startDate,
                                endDate = doc.endDate,
                                reason = doc.reason,
                                status = LeaveStatus.fromString(doc.status),
                                days = doc.numberOfDays,
                                remarks = doc.remarks
                            )
                        }
                        _uiState.update {
                            it.copy(leaveHistory = requests, isLoading = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load leave data", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun showApplyDialog() {
        _uiState.update {
            it.copy(
                showApplyDialog = true,
                applyLeaveType = it.leaveTypes.firstOrNull() ?: "",
                applyStartDate = "",
                applyEndDate = "",
                applyReason = "",
                applyHalfDay = false,
                applyHalfDayPeriod = "AM"
            )
        }
    }

    fun setApplyHalfDay(half: Boolean) {
        _uiState.update {
            // Hardening rules:
            //   • Toggle ON  → force end == start (single-date enforcement) and
            //                  guarantee a valid period (default AM) so submit
            //                  can never see a blank/invalid value.
            //   • Toggle OFF → reset period to "AM" so a previous PM selection
            //                  doesn't silently carry into the next ON cycle.
            //                  End date is left as-is; the user re-picks via
            //                  the now-restored picker.
            it.copy(
                applyHalfDay = half,
                applyEndDate = if (half) it.applyStartDate else it.applyEndDate,
                applyHalfDayPeriod = if (half) {
                    if (it.applyHalfDayPeriod == "AM" || it.applyHalfDayPeriod == "PM") it.applyHalfDayPeriod
                    else "AM"
                } else "AM"
            )
        }
    }

    fun setApplyHalfDayPeriod(period: String) {
        val p = period.uppercase()
        if (p == "AM" || p == "PM") {
            _uiState.update { it.copy(applyHalfDayPeriod = p) }
        }
    }

    fun hideApplyDialog() {
        _uiState.update { it.copy(showApplyDialog = false) }
    }

    fun setLeaveType(type: String) {
        _uiState.update { it.copy(applyLeaveType = type) }
    }

    fun setStartDate(date: String) {
        _uiState.update {
            // If half-day is on, keep end == start automatically.
            if (it.applyHalfDay) it.copy(applyStartDate = date, applyEndDate = date)
            else it.copy(applyStartDate = date)
        }
    }

    fun setEndDate(date: String) {
        _uiState.update {
            // Defense in depth: ignore any end-date write while half-day is
            // active. The UI already disables the picker, but a stale tap
            // event or programmatic call must not be allowed to break the
            // single-date invariant.
            if (it.applyHalfDay) it
            else it.copy(applyEndDate = date)
        }
    }

    fun setReason(reason: String) {
        _uiState.update { it.copy(applyReason = reason) }
    }

    fun submitLeaveRequest() {
        val state = _uiState.value
        if (state.applyStartDate.isBlank()) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Start date is required")) }
            return
        }
        if (state.applyEndDate.isBlank()) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("End date is required")) }
            return
        }
        // MEDIUM #6: reason must be non-empty once trimmed, capped at 1000 chars.
        val trimmedReason = state.applyReason.trim()
        if (trimmedReason.isEmpty()) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Reason is required")) }
            return
        }
        if (trimmedReason.length > 1000) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Reason is too long (max 1000 characters)")) }
            return
        }
        // Phase 4 cross-system: half-day leave must be a single date. Backend
        // rejects multi-day half-day requests; surface clearly here so the
        // user sees the error before the round-trip.
        if (state.applyHalfDay && state.applyStartDate != state.applyEndDate) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Half-day leave must be for a single date")) }
            return
        }
        // Hardening: period must be present and valid when half-day is on.
        // setApplyHalfDay normalises to AM/PM, but a defensive check here
        // prevents a malformed payload reaching the backend if state ever
        // drifts (e.g. process restoration with an old persisted value).
        if (state.applyHalfDay && state.applyHalfDayPeriod !in listOf("AM", "PM")) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Select Morning (AM) or Afternoon (PM) for half-day leave")) }
            return
        }
        // Phase 9b: date validation
        try {
            val start = java.time.LocalDate.parse(state.applyStartDate)
            val end = java.time.LocalDate.parse(state.applyEndDate)
            val today = java.time.LocalDate.now()
            if (start.isBefore(today)) {
                viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Start date cannot be in the past")) }
                return
            }
            if (end.isBefore(start)) {
                viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("End date must be on or after start date")) }
                return
            }
            // MEDIUM #6: bound the span. Contract: staff <= 366 days inclusive.
            val spanDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
            if (spanDays > 366) {
                viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Leave span cannot exceed 366 days")) }
                return
            }
            // MEDIUM #6: overlap/duplicate guard — reject if the new range
            // overlaps an existing pending/approved leave already in history.
            val overlap = state.leaveHistory.any { existing ->
                if (existing.status != LeaveStatus.PENDING && existing.status != LeaveStatus.APPROVED) {
                    return@any false
                }
                val es = try { java.time.LocalDate.parse(existing.startDate) } catch (_: Exception) { return@any false }
                val ee = try { java.time.LocalDate.parse(existing.endDate) } catch (_: Exception) { return@any false }
                // ranges overlap when start <= ee && es <= end
                !start.isAfter(ee) && !es.isAfter(end)
            }
            if (overlap) {
                viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("This range overlaps an existing pending or approved leave")) }
                return
            }
        } catch (_: Exception) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Invalid date format")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                // Phase 9a: calculate actual number of days from date range
                val days = try {
                    val start = java.time.LocalDate.parse(state.applyStartDate)
                    val end = java.time.LocalDate.parse(state.applyEndDate)
                    (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
                } catch (_: Exception) { 1 }

                // CR-3 cross-system: snapshot paid status from current school
                // config so payroll classification survives type deletion.
                val typePaidSnapshot = leaveTypeNameToPaidCache[state.applyLeaveType]
                // Firestore-first: use LeaveFirestoreRepository.
                // MEDIUM #5: half-day stores 0.5, otherwise the whole-day span.
                leaveFirestoreRepo.submitLeave(
                    leaveType = state.applyLeaveType,
                    startDate = state.applyStartDate,
                    endDate = state.applyEndDate,
                    numberOfDays = if (state.applyHalfDay) 0.5 else days.toDouble(),
                    reason = trimmedReason,
                    typePaid = typePaidSnapshot,
                    // Phase 4: half-day support now live in teacher UI.
                    halfDay = state.applyHalfDay,
                    halfDayPeriod = if (state.applyHalfDay) state.applyHalfDayPeriod else ""
                ).fold(
                    onSuccess = {
                        _uiState.update { it.copy(isSubmitting = false, showApplyDialog = false) }
                        _events.emit(LeaveEvent.SubmitSuccess("Leave request submitted"))
                        loadLeaveData()
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isSubmitting = false) }
                        _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to submit"))
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false) }
                _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to submit"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadLeaveData()
        if (_uiState.value.isClassTeacher) loadStudentLeaves()
    }

    /**
     * MEDIUM #9: called on ON_RESUME. Throttled to at most one full reload
     * per 30s so returning from a brief background trip (e.g. a permission
     * dialog) doesn't fire the whole multi-read load again.
     */
    fun onScreenResumed() {
        val now = System.currentTimeMillis()
        if (now - lastResumeRefresh < 30_000L) return
        lastResumeRefresh = now
        refresh()
    }

    /**
     * MEDIUM #7: cancel the teacher's OWN pending leave. Rules allow the owner
     * to move their own pending/approved leave to cancelled. No client-side
     * balance write is attempted (service-account-only under the new rules).
     */
    fun cancelLeave(requestId: String) {
        viewModelScope.launch {
            leaveFirestoreRepo.cancelLeave(requestId).fold(
                onSuccess = {
                    _events.emit(LeaveEvent.SubmitSuccess("Leave cancelled"))
                    loadLeaveData()
                },
                onFailure = { e ->
                    _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to cancel leave"))
                }
            )
        }
    }

    // ── Phase 10e: Student Leave tab ──────────────────────────────

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == 1 && _uiState.value.studentLeaves.isEmpty()) {
            loadStudentLeaves()
        }
    }

    private fun checkClassTeacherStatus() {
        viewModelScope.launch {
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        cachedAssignments = assignments
                        val isClassTeacher = assignments.any { it.classTeacher }
                        _uiState.update { it.copy(isClassTeacher = isClassTeacher) }
                        if (isClassTeacher) loadStudentLeaves()
                    },
                    onFailure = { /* not a class teacher or network error */ }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check class teacher status", e)
            }
        }
    }

    fun loadStudentLeaves() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStudentLeaves = true, studentLeavesError = null) }
            try {
                // H9: push class/section scoping into the query — run one
                // bounded query per distinct class-teacher section instead of a
                // whole-school scan. Merge + dedup by doc id.
                val sections = cachedAssignments
                    .filter { it.classTeacher && it.className.isNotBlank() && it.section.isNotBlank() }
                    .map { it.className to it.section }
                    .distinct()

                val merged = LinkedHashMap<String, LeaveApplicationDoc>()
                var lastError: Throwable? = null
                var anySuccess = false

                if (sections.isEmpty()) {
                    // Fallback: no resolved sections — bounded unscoped query,
                    // filtered client-side (defense in depth).
                    leaveFirestoreRepo.getStudentLeaveRequests().fold(
                        onSuccess = { leaves ->
                            anySuccess = true
                            leaves.filter { leave ->
                                cachedAssignments.any { a ->
                                    a.classTeacher &&
                                    leave.className.isNotBlank() && leave.className == a.className &&
                                    leave.section.isNotBlank() && leave.section == a.section
                                }
                            }.forEach { merged[it.id] = it }
                        },
                        onFailure = { lastError = it }
                    )
                } else {
                    for ((cls, sec) in sections) {
                        leaveFirestoreRepo.getStudentLeaveRequests(cls, sec).fold(
                            onSuccess = { leaves ->
                                anySuccess = true
                                // Client-side scoping kept as defense.
                                leaves.filter {
                                    it.className == cls && it.section == sec
                                }.forEach { merged[it.id] = it }
                            },
                            onFailure = { lastError = it }
                        )
                    }
                }

                val result = merged.values.sortedByDescending { it.startDate }
                // H11: only surface an error when every query failed (nothing
                // to show). A partial success still renders what we got.
                val errorMsg = if (!anySuccess && lastError != null) {
                    Log.e(TAG, "Failed to load student leaves", lastError)
                    lastError?.message ?: "Failed to load student leave requests"
                } else null
                _uiState.update { it.copy(
                    studentLeaves = result,
                    isLoadingStudentLeaves = false,
                    studentLeavesError = errorMsg
                )}
            } catch (e: Exception) {
                Log.e(TAG, "Student leaves exception", e)
                _uiState.update { it.copy(
                    isLoadingStudentLeaves = false,
                    studentLeavesError = e.message ?: "Failed to load student leave requests"
                )}
            }
        }
    }

    fun approveStudentLeave(leaveId: String, remarks: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingLeaveId = leaveId) }
            try {
                leaveFirestoreRepo.approveStudentLeave(leaveId, remarks).fold(
                    onSuccess = {
                        _events.emit(LeaveEvent.SubmitSuccess("Leave approved"))
                        loadStudentLeaves()
                    },
                    onFailure = { e ->
                        _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to approve"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to approve"))
            }
            _uiState.update { it.copy(processingLeaveId = null) }
        }
    }

    fun rejectStudentLeave(leaveId: String, remarks: String) {
        if (remarks.isBlank()) {
            viewModelScope.launch {
                _events.emit(LeaveEvent.SubmitError("Remarks are required when rejecting"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(processingLeaveId = leaveId) }
            try {
                leaveFirestoreRepo.rejectStudentLeave(leaveId, remarks).fold(
                    onSuccess = {
                        _events.emit(LeaveEvent.SubmitSuccess("Leave rejected"))
                        loadStudentLeaves()
                    },
                    onFailure = { e ->
                        _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to reject"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(LeaveEvent.SubmitError(e.message ?: "Failed to reject"))
            }
            _uiState.update { it.copy(processingLeaveId = null) }
        }
    }
}
