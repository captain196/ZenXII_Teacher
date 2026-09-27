package com.schoolsync.teacher.ui.recruitment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.RecruitmentDoc
import com.schoolsync.teacher.data.repository.firestore.HRFirestoreRepository
import com.schoolsync.teacher.util.BadgeBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecruitmentUiState(
    val isLoading: Boolean = false,
    val openings: List<RecruitmentDoc> = emptyList(),
    val expandedId: String? = null,
    val error: String? = null
)

@HiltViewModel
class RecruitmentViewModel @Inject constructor(
    private val hrRepo: HRFirestoreRepository,
    private val badgeBus: BadgeBus
) : ViewModel() {

    companion object {
        private const val TAG = "RecruitmentVM"

        /**
         * Must equal Route.Recruitment.route — the nav rail looks the count up
         * by that exact string, so a drifting literal silently shows no badge.
         */
        private const val BADGE_ROUTE = "recruitment"
    }

    private val _uiState = MutableStateFlow(RecruitmentUiState())
    val uiState: StateFlow<RecruitmentUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            hrRepo.getRecruitmentOpenings().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(isLoading = false, openings = list) }
                    // Nav-rail badge = currently open postings. The repository
                    // query already filters statusLower == "open", so the list
                    // size IS the open count.
                    badgeBus.setCount(BADGE_ROUTE, list.size)
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load openings", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
                    // Clear rather than leave a stale count standing: a badge
                    // that outlives the data it came from is a lie.
                    badgeBus.setCount(BADGE_ROUTE, 0)
                }
            )
        }
    }

    fun toggleExpand(id: String) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }
}
