package com.schoolsync.teacher.ui.recruitment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.RecruitmentDoc
import com.schoolsync.teacher.data.repository.firestore.HRFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

data class RecruitmentUiState(
    val isLoading: Boolean = false,
    val openings: List<RecruitmentDoc> = emptyList(),
    val expandedId: String? = null,
    val error: String? = null
)

@HiltViewModel
class RecruitmentViewModel @Inject constructor(
    private val hrRepo: HRFirestoreRepository,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object { private const val TAG = "RecruitmentVM" }

    private val _uiState = MutableStateFlow(RecruitmentUiState())
    val uiState: StateFlow<RecruitmentUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            hrRepo.getRecruitmentOpenings().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(isLoading = false, openings = list) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load openings", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: appContext.localizedString(R.string.vm_load_failed)) }
                }
            )
        }
    }

    fun toggleExpand(id: String) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }
}
