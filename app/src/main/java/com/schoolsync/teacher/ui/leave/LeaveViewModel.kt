package com.schoolsync.teacher.ui.leave

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.LeaveBalance as ModelLeaveBalance
import com.schoolsync.teacher.data.model.LeaveRequest as ModelLeaveRequest
import com.schoolsync.teacher.data.model.LeaveStatus as ModelLeaveStatus
import com.schoolsync.teacher.data.repository.LeaveRepository
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
    }
}

data class LeaveUiState(
    val balances: List<LeaveBalance> = emptyList(),
    val leaveHistory: List<LeaveRequest> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showApplyDialog: Boolean = false,
    val applyLeaveType: String = "Casual",
    val applyStartDate: String = "",
    val applyEndDate: String = "",
    val applyReason: String = "",
    val isSubmitting: Boolean = false,
    val leaveTypes: List<String> = listOf("Casual", "Sick", "Earned", "Other")
)

sealed class LeaveEvent {
    data class SubmitSuccess(val message: String) : LeaveEvent()
    data class SubmitError(val message: String) : LeaveEvent()
}

@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val leaveRepository: LeaveRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "LeaveVM"
    }

    private val _uiState = MutableStateFlow(LeaveUiState())
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LeaveEvent>()
    val events = _events.asSharedFlow()

    init {
        loadLeaveData()
    }

    fun loadLeaveData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Load balance
                leaveRepository.getLeaveBalance().fold(
                    onSuccess = { modelBalances ->
                        val balances = modelBalances.map {
                            LeaveBalance(
                                type = it.leaveType,
                                used = it.used,
                                total = it.total,
                                remaining = it.remaining
                            )
                        }
                        _uiState.update { it.copy(balances = balances) }
                    },
                    onFailure = { /* empty list */ }
                )

                // Load history
                leaveRepository.getLeaveHistory().fold(
                    onSuccess = { modelRequests ->
                        val requests = modelRequests.map { r ->
                            LeaveRequest(
                                requestId = r.leaveId,
                                type = r.leaveType,
                                startDate = r.startDate,
                                endDate = r.endDate,
                                reason = r.reason,
                                status = LeaveStatus.fromModel(r.status),
                                days = r.numberOfDays,
                                remarks = r.remarks
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
                applyLeaveType = "Casual",
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

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: ""

                val request = ModelLeaveRequest(
                    teacherId = teacherId,
                    teacherName = teacherName,
                    leaveType = state.applyLeaveType,
                    startDate = state.applyStartDate,
                    endDate = state.applyEndDate,
                    reason = state.applyReason,
                    status = ModelLeaveStatus.PENDING,
                    numberOfDays = 1 // TODO: calculate from date range
                )

                leaveRepository.submitLeaveRequest(request).fold(
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
    }
}
