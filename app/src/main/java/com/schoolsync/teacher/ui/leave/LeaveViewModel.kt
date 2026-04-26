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
import com.schoolsync.teacher.data.repository.LeaveRepository
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
    private val leaveRepository: LeaveRepository,
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

    init {
        loadLeaveData()
        checkClassTeacherStatus()
    }

    fun loadLeaveData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Load leave types from Firestore schools doc
                var leaveTypeNames = emptyList<String>()
                var leaveTypeIdToName = mutableMapOf<String, String>() // LT0001 → "Casual Leave"
                try {
                    val schoolCode = tokenManager.schoolCode.firstOrNull()
                        ?: tokenManager.schoolId.firstOrNull() ?: ""
                    if (schoolCode.isNotBlank()) {
                        val schoolDoc = leaveFirestoreRepo.getSchoolLeaveTypes(schoolCode)
                        if (schoolDoc != null) {
                            leaveTypeNames = schoolDoc.mapNotNull { (id, v) ->
                                if (v is Map<*, *>) {
                                    val name = v["name"]?.toString() ?: v["code"]?.toString()
                                    val status = v["status"]?.toString() ?: "Active"
                                    if (status == "Active" && name != null) {
                                        leaveTypeIdToName[id] = name
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
                                    val typeName = leaveTypeIdToName[typeId] ?: typeId
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

                // RTDB fallback if Firestore returned nothing
                if (balances.isEmpty()) {
                    leaveRepository.getLeaveBalance().fold(
                        onSuccess = { modelBalances ->
                            balances = modelBalances.map {
                                LeaveBalance(
                                    type = it.leaveType,
                                    used = it.used,
                                    total = it.total,
                                    remaining = it.remaining
                                )
                            }
                        },
                        onFailure = { /* empty list */ }
                    )
                }
                _uiState.update { it.copy(balances = balances) }

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
                applyReason = ""
            )
        }
    }

    fun hideApplyDialog() {
        _uiState.update { it.copy(showApplyDialog = false) }
    }

    fun setLeaveType(type: String) {
        _uiState.update { it.copy(applyLeaveType = type) }
    }

    fun setStartDate(date: String) {
        _uiState.update { it.copy(applyStartDate = date) }
    }

    fun setEndDate(date: String) {
        _uiState.update { it.copy(applyEndDate = date) }
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

                // Firestore-first: use LeaveFirestoreRepository
                leaveFirestoreRepo.submitLeave(
                    leaveType = state.applyLeaveType,
                    startDate = state.applyStartDate,
                    endDate = state.applyEndDate,
                    numberOfDays = days,
                    reason = state.applyReason
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
