package com.schoolsync.teacher.ui.leave

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.LeaveBalance as ModelLeaveBalance
import com.schoolsync.teacher.data.model.LeaveRequest as ModelLeaveRequest
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
    val used: Int,
    val total: Int,
    val remaining: Int = total - used
)

data class LeaveRequest(
    val requestId: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val reason: String,
    val status: LeaveStatus,
    val appliedDate: String = "",
    val days: Int = 1,
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
    val error: String? = null,
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

    init {
        loadLeaveData()
        checkClassTeacherStatus()
    }

    fun loadLeaveData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
                // CR-3 cross-system: re-populate class-level cache from fresh
                // school config every loadLeaveData; submit reads it later.
                leaveTypeNameToPaidCache.clear()
                val leaveTypeNameToPaid = leaveTypeNameToPaidCache
                try {
                    if (schoolCodeForLookup.isNotBlank()) {
                        val schoolDoc = leaveFirestoreRepo.getSchoolLeaveTypes(schoolCodeForLookup)
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
                } catch (_: Exception) {}
                if (leaveTypeNames.isNotEmpty()) {
                    _uiState.update { it.copy(
                        leaveTypes = leaveTypeNames,
                        applyLeaveType = if (it.applyLeaveType.isBlank()) leaveTypeNames.first() else it.applyLeaveType
                    ) }
                }

                // Load balance — Firestore-first via leaveApplications BAL docs
                var balances = emptyList<LeaveBalance>()
                try {
                    // schoolCode stores the schoolId path (SCH_xxx), schoolId may store legacy name
                    val schoolCode = tokenManager.schoolCode.firstOrNull()
                        ?: tokenManager.schoolId.firstOrNull() ?: ""
                    val teacherId = tokenManager.userId.firstOrNull() ?: ""
                    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
                    if (schoolCode.isNotBlank() && teacherId.isNotBlank()) {
                        val balDocId = "${schoolCode}_BAL_${teacherId}_$year"
                        var balDoc = leaveFirestoreRepo.getBalanceDoc(balDocId)
                        // Try alternate school ID if first attempt failed
                        if (balDoc == null) {
                            val altSchool = tokenManager.schoolId.firstOrNull() ?: ""
                            if (altSchool.isNotBlank() && altSchool != schoolCode) {
                                balDoc = leaveFirestoreRepo.getBalanceDoc("${altSchool}_BAL_${teacherId}_$year")
                            }
                        }
                        if (balDoc != null) {
                            // Filter gender-specific leaves
                            val staffDocId = "${schoolCode}_${teacherId}"
                            val staffGender = try {
                                val staffDoc = leaveFirestoreRepo.getStaffGender(staffDocId)
                                staffDoc?.lowercase() ?: ""
                            } catch (_: Exception) { "" }

                            val priorityOrder = listOf("Casual", "Sick", "Earned", "Academic", "Comp", "Paternity", "Maternity")
                            balances = balDoc.entries.mapNotNull { (typeId, data) ->
                                if (data is Map<*, *>) {
                                    val alloc = (data["allocated"] as? Number)?.toInt() ?: 0
                                    if (alloc <= 0) return@mapNotNull null
                                    // Skip entries whose typeId no longer
                                    // resolves to a name — surface "LT0004"
                                    // raw to the user is worse than hiding
                                    // a stale BAL row entirely. The pass-1
                                    // map above includes inactive types,
                                    // so we only land here when the type
                                    // was outright deleted from the school.
                                    val typeName = leaveTypeIdToName[typeId] ?: return@mapNotNull null
                                    // Hide gender-specific leaves
                                    if (staffGender == "male" && typeName.contains("Maternity", true)) return@mapNotNull null
                                    if (staffGender == "female" && typeName.contains("Paternity", true)) return@mapNotNull null
                                    LeaveBalance(
                                        type = typeName,
                                        used = (data["used"] as? Number)?.toInt() ?: 0,
                                        total = alloc
                                    )
                                } else null
                            }.sortedBy { bal ->
                                val idx = priorityOrder.indexOfFirst { bal.type.contains(it, ignoreCase = true) }
                                if (idx >= 0) idx else 99
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Firestore-only contract: empty result == no balances. The
                // legacy RTDB fallback (`leaveRepository.getLeaveBalance()`)
                // was removed because admin no longer writes that path —
                // calling it would surface stale or seeded data and violate
                // the absolute NO-RTDB rule. `LeaveRepository` (RTDB) was
                // deleted outright in Phase 1 Logical Change 4A (2026-07-03).
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
        if (state.applyReason.isBlank()) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Reason is required")) }
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
        } catch (_: Exception) {
            viewModelScope.launch { _events.emit(LeaveEvent.SubmitError("Invalid date format")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: ""

                // Phase 9a: calculate actual number of days from date range
                val days = try {
                    val start = java.time.LocalDate.parse(state.applyStartDate)
                    val end = java.time.LocalDate.parse(state.applyEndDate)
                    (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
                } catch (_: Exception) { 1 }

                val request = ModelLeaveRequest(
                    teacherId = teacherId,
                    teacherName = teacherName,
                    leaveType = state.applyLeaveType,
                    startDate = state.applyStartDate,
                    endDate = state.applyEndDate,
                    reason = state.applyReason,
                    status = ModelLeaveStatus.PENDING,
                    numberOfDays = days
                )

                // CR-3 cross-system: snapshot paid status from current school
                // config so payroll classification survives type deletion.
                val typePaidSnapshot = leaveTypeNameToPaidCache[state.applyLeaveType]
                // Firestore-first: use LeaveFirestoreRepository
                leaveFirestoreRepo.submitLeave(
                    leaveType = state.applyLeaveType,
                    startDate = state.applyStartDate,
                    endDate = state.applyEndDate,
                    numberOfDays = if (state.applyHalfDay) 1 else days,
                    reason = state.applyReason,
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
            _uiState.update { it.copy(isLoadingStudentLeaves = true) }
            try {
                leaveFirestoreRepo.getStudentLeaveRequests().fold(
                    onSuccess = { leaves ->
                        // Filter to only show leaves for classes where this teacher IS the class teacher
                        val filtered = leaves.filter { leave ->
                            cachedAssignments.any { a ->
                                a.classTeacher &&
                                (leave.className.isNotBlank() && leave.className == a.className &&
                                 leave.section.isNotBlank() && leave.section == a.section)
                            }
                        }
                        _uiState.update { it.copy(
                            studentLeaves = filtered,
                            isLoadingStudentLeaves = false
                        )}
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load student leaves", e)
                        _uiState.update { it.copy(isLoadingStudentLeaves = false) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Student leaves exception", e)
                _uiState.update { it.copy(isLoadingStudentLeaves = false) }
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
