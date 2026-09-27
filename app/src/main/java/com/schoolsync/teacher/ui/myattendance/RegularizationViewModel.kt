package com.schoolsync.teacher.ui.myattendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.lifecycle.FailureKind
import com.schoolsync.teacher.data.lifecycle.SchoolLifecycle
import com.schoolsync.teacher.data.lifecycle.SchoolLifecycleRepository
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

/**
 * RegularizationViewModel — submit + history for the "Regularize Attendance"
 * bottom sheet. Submission writes directly to Firestore via
 * [AttendanceRegularizationRepository]; no backend call is needed to submit.
 */
@HiltViewModel
class RegularizationViewModel @Inject constructor(
    private val repo: AttendanceRegularizationRepository,
    private val schoolLifecycleRepository: SchoolLifecycleRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RegularizationUiState())
    val ui: StateFlow<RegularizationUiState> = _ui.asStateFlow()

    /**
     * TA-D1. A repository failure here can be a hand-authored validation
     * message ("No dates selected.") OR a raw system exception (a Firestore
     * PERMISSION_DENIED, or — as actually observed — a plain
     * java.net.ConnectException whose message is literally
     * "Failed to connect to localhost/127.0.0.1:8080"). The two must never be
     * shown the same way: a validation message IS the UI copy; a system
     * exception must never reach the screen verbatim.
     *
     * [IllegalArgumentException] is this repository's own convention for
     * "the message is already what the user should read" (see
     * AttendanceRegularizationRepository.submit()). Everything else is run
     * through the same classify()/messageFor() pipeline the rest of the app
     * uses for Firestore failures, so a school-state denial reads as a school
     * matter, a network failure reads as a network matter, and anything truly
     * unclassified falls back to [fallback] rather than its raw .message.
     */
    private suspend fun safeMessage(e: Throwable, fallback: String): String {
        if (e is IllegalArgumentException) return e.message ?: fallback

        val access = schoolLifecycleRepository.currentSchoolAccess()
        return when (val kind = SchoolLifecycle.classify(e, access)) {
            FailureKind.UNKNOWN -> fallback
            else -> SchoolLifecycle.messageFor(kind, access)
        }
    }

    fun loadMyRequests() {
        viewModelScope.launch {
            _ui.update { it.copy(loadingRequests = true, requestsError = null) }
            repo.getMyRequests()
                .onSuccess { list -> _ui.update { it.copy(loadingRequests = false, myRequests = list, requestsError = null) } }
                .onFailure { e ->
                    // Surface the failure now that "My Requests" renders it — previously
                    // swallowed, which left the list silently empty on a permission /
                    // network error (indistinguishable from "no requests yet").
                    val msg = safeMessage(e, "Couldn't load your requests.")
                    _ui.update { it.copy(loadingRequests = false, requestsError = msg) }
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
                    val msg = safeMessage(e, "Could not submit request.")
                    _ui.update { it.copy(submitting = false, error = msg) }
                }
        }
    }

    /**
     * Withdraw a pending request.
     *
     * The row is marked busy BY ID rather than with one global flag, so a slow
     * network cannot make every card in the list spin at once and two taps on
     * different rows do not fight over a single piece of state.
     */
    fun withdraw(docId: String) {
        if (docId.isBlank() || _ui.value.withdrawingId != null) return

        viewModelScope.launch {
            _ui.update { it.copy(withdrawingId = docId, error = null, withdrewCount = null) }
            repo.withdraw(docId)
                .onSuccess {
                    _ui.update { it.copy(withdrawingId = null, withdrewCount = 1) }
                    loadMyRequests()   // re-read; never assume the new status
                }
                .onFailure { e ->
                    val msg = safeMessage(e, "Could not withdraw the request.")
                    _ui.update {
                        it.copy(
                            withdrawingId = null,
                            error = msg,
                        )
                    }
                }
        }
    }

    /** Clear the one-shot submit result / error after the UI has shown it. */
    fun consume() = _ui.update { it.copy(submittedCount = null, error = null, withdrewCount = null) }
}

data class RegularizationUiState(
    val submitting: Boolean = false,
    val submittedCount: Int? = null,   // set once on a successful submit
    val error: String? = null,
    val loadingRequests: Boolean = false,
    val myRequests: List<RegularizationDoc> = emptyList(),
    val requestsError: String? = null,
    /** Id of the request currently being withdrawn, if any. */
    val withdrawingId: String? = null,
    val withdrewCount: Int? = null,   // one-shot success signal
)
