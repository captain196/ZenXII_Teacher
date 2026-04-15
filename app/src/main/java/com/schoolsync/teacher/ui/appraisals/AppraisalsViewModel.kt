package com.schoolsync.teacher.ui.appraisals

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.AppraisalDoc
import com.schoolsync.teacher.data.repository.firestore.HRFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppraisalsUiState(
    val isLoading: Boolean = false,
    val appraisals: List<AppraisalDoc> = emptyList(),
    val expandedId: String? = null,
    val error: String? = null
)

@HiltViewModel
class AppraisalsViewModel @Inject constructor(
    private val hrRepo: HRFirestoreRepository
) : ViewModel() {

    companion object { private const val TAG = "AppraisalsVM" }

    private val _uiState = MutableStateFlow(AppraisalsUiState())
    val uiState: StateFlow<AppraisalsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            hrRepo.getMyAppraisals().fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            appraisals = list,
                            expandedId = it.expandedId ?: list.firstOrNull()?.id
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load appraisals", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
                }
            )
        }
    }

    fun toggleExpand(id: String) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }
}
