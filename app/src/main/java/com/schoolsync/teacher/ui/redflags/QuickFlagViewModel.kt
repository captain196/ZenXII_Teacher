package com.schoolsync.teacher.ui.redflags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.StudentFlag
import com.schoolsync.teacher.data.repository.RedFlagRepository
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

/**
 * Phase 6A — shared submit + undo handler for the QuickFlagSheet.
 *
 * Hosted at every entry-point screen (attendance, homework, marks,
 * redflags, students) via `hiltViewModel<QuickFlagViewModel>()`. Each
 * screen owns its own instance, but the logic to populate teacher
 * identity, dispatch the create, and run the 5-second Undo lives here
 * so the 5 screens don't have to reimplement it five times.
 *
 * The host screen is responsible for:
 *   • Building the [QuickFlagSheetState] and calling `showFor(...)`.
 *   • Rendering the [QuickFlagSheet] composable with `onSubmit = vm::submit`.
 *   • Rendering the [QuickFlagUndoBanner] when `state.lastCreatedFlagId`
 *     is non-null and calling `vm::undo` from its UNDO action.
 *   • Surfacing toasts/snackbars for `events`.
 */
@HiltViewModel
class QuickFlagViewModel @Inject constructor(
    private val redFlagRepository: RedFlagRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    data class State(
        val isSubmitting: Boolean = false,
        /** Non-null while the just-created flag is still in its 5s Undo window. */
        val lastCreatedFlagId: String? = null,
        /** Friendly name of the just-flagged student, for the snackbar/banner. */
        val lastStudentName: String = ""
    )

    sealed class Event {
        data class Success(val message: String) : Event()
        data class Error(val message: String) : Event()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    /**
     * Submit a [StudentFlag] built by [QuickFlagSheet]. Hydrates teacher
     * identity from TokenManager before handing off to the repository.
     */
    fun submit(quickFlag: StudentFlag) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            try {
                val teacherId   = tokenManager.userId.firstOrNull().orEmpty()
                val teacherName = tokenManager.userName.firstOrNull().orEmpty()
                val flag = quickFlag.copy(
                    teacherId   = teacherId,
                    teacherName = teacherName
                )

                redFlagRepository.createFlag(flag).fold(
                    onSuccess = { flagId ->
                        Log.d(TAG, "Quick flag created: $flagId")
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                lastCreatedFlagId = flagId,
                                lastStudentName = flag.studentName
                            )
                        }
                        _events.emit(Event.Success("Flag raised for ${flag.studentName}"))
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Quick flag create failed", e)
                        _state.update { it.copy(isSubmitting = false) }
                        _events.emit(Event.Error(e.message ?: "Failed to raise flag"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                _state.update { it.copy(isSubmitting = false) }
                _events.emit(Event.Error(e.message ?: "Failed to raise flag"))
            }
        }
    }

    /** 5-second Undo path — soft-deletes the just-raised flag. */
    fun undo() {
        val flagId = _state.value.lastCreatedFlagId ?: return
        viewModelScope.launch {
            redFlagRepository.softDeleteFlag(flagId).fold(
                onSuccess = {
                    _state.update { it.copy(lastCreatedFlagId = null, lastStudentName = "") }
                    _events.emit(Event.Success("Flag undone"))
                },
                onFailure = { e ->
                    Log.e(TAG, "Undo failed", e)
                    _events.emit(Event.Error(e.message ?: "Undo failed"))
                }
            )
        }
    }

    /** Drops the undo handle once the 5s window has elapsed. */
    fun clearUndoWindow() {
        _state.update { it.copy(lastCreatedFlagId = null, lastStudentName = "") }
    }

    companion object { private const val TAG = "QuickFlagVM" }
}
