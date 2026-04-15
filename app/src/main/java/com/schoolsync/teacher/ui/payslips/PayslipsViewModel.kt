package com.schoolsync.teacher.ui.payslips

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.SalarySlipDoc
import com.schoolsync.teacher.data.repository.firestore.HRFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PayslipsUiState(
    val isLoading: Boolean = false,
    val payslips: List<SalarySlipDoc> = emptyList(),
    val expandedId: String? = null,
    val error: String? = null
)

@HiltViewModel
class PayslipsViewModel @Inject constructor(
    private val hrRepo: HRFirestoreRepository
) : ViewModel() {

    companion object { private const val TAG = "PayslipsVM" }

    private val _uiState = MutableStateFlow(PayslipsUiState())
    val uiState: StateFlow<PayslipsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            hrRepo.getMySalarySlips().fold(
                onSuccess = { slips ->
                    _uiState.update { it.copy(isLoading = false, payslips = slips) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load payslips", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
                }
            )
        }
    }

    fun toggleExpand(id: String) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }
}
