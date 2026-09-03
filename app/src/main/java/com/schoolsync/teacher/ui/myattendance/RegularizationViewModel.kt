package com.schoolsync.teacher.ui.myattendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.repository.firestore.AttendanceRegularizationRepository
import com.schoolsync.teacher.data.repository.firestore.RegularizationDoc
import com.schoolsync.teacher.data.repository.firestore.RegularizationEntry
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

/**
 * RegularizationViewModel — submit + history for the "Regularize Attendance"
 * bottom sheet. Submission writes directly to Firestore via
 * [AttendanceRegularizationRepository]; no backend call is needed to submit.
 */
@HiltViewModel
class RegularizationViewModel @Inject constructor(
    private val repo: AttendanceRegularizationRepository,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(RegularizationUiState())
    val ui: StateFlow<RegularizationUiState> = _ui.asStateFlow()

    fun loadMyRequests() {
        viewModelScope.launch {
            _ui.update { it.copy(loadingRequests = true, requestsError = null) }
            repo.getMyRequests()
                .onSuccess { list -> _ui.update { it.copy(loadingRequests = false, myRequests = list, requestsError = null) } }
                .onFailure { e ->
                    // Surface the failure now that "My Requests" renders it — previously
                    // swallowed, which left the list silently empty on a permission /
                    // network error (indistinguishable from "no requests yet").
                    _ui.update { it.copy(loadingRequests = false, requestsError = e.message ?: appContext.localizedString(R.string.vm_reg_load_failed)) }
                }
        }
    }

    fun submit(entries: List<RegularizationEntry>, reason: String) {
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, error = null, submittedCount = null) }
            repo.submit(entries, reason.trim())
                .onSuccess {
                    _ui.update { it.copy(submitting = false, submittedCount = entries.size) }
                    loadMyRequests()
                }
                .onFailure { e ->
                    _ui.update { it.copy(submitting = false, error = e.message ?: appContext.localizedString(R.string.vm_reg_submit_failed)) }
                }
        }
    }

    /** Clear the one-shot submit result / error after the UI has shown it. */
    fun consume() = _ui.update { it.copy(submittedCount = null, error = null) }
}

data class RegularizationUiState(
    val submitting: Boolean = false,
    val submittedCount: Int? = null,   // set once on a successful submit
    val error: String? = null,
    val loadingRequests: Boolean = false,
    val myRequests: List<RegularizationDoc> = emptyList(),
    val requestsError: String? = null,
)
