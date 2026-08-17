package com.schoolsync.teacher.ui.redflags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.StudentFlag
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.repository.RedFlagRepository
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlagClassSection(
    val className: String,
    val section: String
) {
    val displayName: String get() = "$className - $section"
}

data class RedFlagUiState(
    val availableClasses: List<FlagClassSection> = emptyList(),
    val selectedClass: FlagClassSection? = null,
    val students: List<StudentInfo> = emptyList(),
    val flagsByStudent: Map<String, List<StudentFlag>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedStudentId: String? = null,
    val subjectsForClass: List<String> = emptyList(),
    /** Current teacher's Firebase UID — drives delete-button visibility. */
    val currentTeacherUid: String = "",
    /** ID of the most recent quick-flag write — drives the Undo snackbar. */
    val lastCreatedFlagId: String? = null,
    /** True while a quick-flag create is in flight — drives the sheet's
     *  submit spinner and keeps the sheet open until the write settles. */
    val savingFlag: Boolean = false,
    /** Flag IDs whose resolve/delete write is in flight — drives a per-row
     *  spinner + disabled state on the Resolve/Delete buttons. */
    val busyFlagIds: Set<String> = emptySet()
)

sealed class RedFlagEvent {
    data class Success(val message: String) : RedFlagEvent()
    data class Error(val message: String) : RedFlagEvent()
}

@HiltViewModel
class RedFlagTeacherViewModel @Inject constructor(
    private val redFlagRepository: RedFlagRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val tokenManager: TokenManager,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "RedFlagVM"
    }

    private val _uiState = MutableStateFlow(RedFlagUiState())
    val uiState: StateFlow<RedFlagUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RedFlagEvent>()
    val events = _events.asSharedFlow()

    private var allAssignments: List<ClassAssignment> = emptyList()

    /**
     * Active snapshot listener for flags in the currently-selected class.
     * Cancelled on every class change so we don't accumulate listeners.
     */
    private var flagsObserveJob: Job? = null

    /**
     * The outer roster-load + observe-setup coroutine. Tracked separately from
     * [flagsObserveJob] so a rapid class switch cancels the in-flight student
     * fetch too — otherwise two fetches race and the slower one can bind the
     * previous class's roster to the new class's observer.
     */
    private var studentsLoadJob: Job? = null

    init {
        // Stamp the current teacher's UID into state once at startup so the
        // screen can decide which delete buttons to render. Auth UID is the
        // only thing the Firestore rule will accept for soft-delete RBAC.
        _uiState.update { it.copy(currentTeacherUid = firebaseAuth.currentUser?.uid.orEmpty()) }
        // React to academic-session changes. When the admin switches the
        // school's active session, SchoolFirestoreRepository.observeSchool()
        // propagates the new value into TokenManager; we reload the teacher's
        // assigned classes + roster for that session so nothing shows stale
        // data from the previous session. The first (current) emission performs
        // the initial load. Mirrors AttendanceViewModel.
        viewModelScope.launch {
            tokenManager.session
                .distinctUntilChanged()
                .collect { session ->
                    if (!session.isNullOrBlank()) {
                        loadAssignedClasses()
                    }
                }
        }
    }

    private fun loadAssignedClasses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        allAssignments = assignments
                        val classSections = assignments
                            .map { FlagClassSection(it.className, it.section) }
                            .distinct()

                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                selectedClass = classSections.firstOrNull()
                            )
                        }

                        if (classSections.isNotEmpty()) {
                            updateSubjectsForClass(classSections.first())
                            loadStudentsAndFlags()
                        } else {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load assignments", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load classes", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun updateSubjectsForClass(classSection: FlagClassSection) {
        val subjects = allAssignments
            .filter { it.className == classSection.className && it.section == classSection.section }
            .map { it.subject }
            .distinct()
            .sorted()
        _uiState.update { it.copy(subjectsForClass = subjects) }
    }

    fun selectClass(classSection: FlagClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        _uiState.update {
            it.copy(
                selectedClass = classSection,
                selectedStudentId = null
            )
        }
        updateSubjectsForClass(classSection)
        loadStudentsAndFlags()
    }

    fun selectStudent(studentId: String?) {
        _uiState.update { it.copy(selectedStudentId = studentId) }
    }

    private fun loadStudentsAndFlags() {
        val classSection = _uiState.value.selectedClass ?: return

        // Cancel any prior in-flight load AND listener — switching classes must
        // not stack observers or let a slow previous-class roster fetch resolve
        // after the new one and bind the wrong students.
        studentsLoadJob?.cancel()
        flagsObserveJob?.cancel()

        studentsLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Students are static for the session — one-shot fetch.
                val studentsResult = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                // A roster-load FAILURE must surface as an error, not be
                // swallowed to an empty list — otherwise the screen shows a
                // false "no students" empty state and the teacher can't flag
                // anyone, with no hint that anything went wrong.
                val students = studentsResult.getOrElse { e ->
                    Log.e(TAG, "Failed to load students for class", e)
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Couldn't load students. Pull to retry.")
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        students = students,
                        flagsByStudent = emptyMap(),
                        isLoading = false
                    )
                }

                // Flags are live — observe so admin/peer resolves and new
                // flags from anywhere in the system reflect on this screen
                // without a manual reload. Mirrors the parent app's
                // snapshot-listener approach.
                flagsObserveJob = viewModelScope.launch {
                    redFlagRepository.observeFlagsForClass(students)
                        .catch { e ->
                            // Listener exceptions (missing index, rules
                            // denial, network drop) must not crash the
                            // app — surface as an error in UI state and
                            // let the screen render its existing data.
                            Log.e(TAG, "observeFlagsForClass failed", e)
                            _uiState.update { it.copy(error = e.message) }
                        }
                        .collect { flagsByStudent ->
                            Log.d(TAG, "observeFlagsForClass tick — ${students.size} students, " +
                                "${flagsByStudent.values.sumOf { it.size }} flags")
                            _uiState.update { it.copy(flagsByStudent = flagsByStudent) }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load flags", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Phase 6A — submit a flag built by the QuickFlagSheet.
     *
     * The sheet hands us a fully-populated [StudentFlag] minus the
     * teacher identity (which lives in TokenManager / FirebaseAuth).
     * We hydrate teacher fields here and dispatch to the repo.
     * Successful writes also seed a "lastCreatedFlagId" in state so
     * the screen can show a 5-second Undo snackbar.
     */
    fun submitQuickFlag(quickFlag: StudentFlag) {
        // Re-entrancy guard — ignore a second tap while a write is in flight.
        if (_uiState.value.savingFlag) return
        viewModelScope.launch {
            _uiState.update { it.copy(savingFlag = true) }
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
                        // lastCreatedFlagId drives BOTH the Undo snackbar and
                        // the sheet auto-dismiss (screen observes it). Set it
                        // only on success so a failed write leaves the sheet
                        // open for a retry.
                        _uiState.update { it.copy(lastCreatedFlagId = flagId) }
                        _events.emit(RedFlagEvent.Success(
                            "Flag raised for ${flag.studentName}"
                        ))
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Quick flag create failed", e)
                        _events.emit(RedFlagEvent.Error(
                            e.message ?: "Failed to raise flag"
                        ))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "submitQuickFlag failed", e)
                _events.emit(RedFlagEvent.Error(e.message ?: "Failed to raise flag"))
            } finally {
                _uiState.update { it.copy(savingFlag = false) }
            }
        }
    }

    /**
     * 5-second Undo path for a just-raised quick flag — soft-deletes
     * via the repo. Only valid for flags this teacher created
     * (rules block delete on others).
     */
    fun undoLastQuickFlag() {
        val flagId = _uiState.value.lastCreatedFlagId ?: return
        viewModelScope.launch {
            redFlagRepository.softDeleteFlag(flagId).fold(
                onSuccess = {
                    _uiState.update { it.copy(lastCreatedFlagId = null) }
                    _events.emit(RedFlagEvent.Success("Flag undone"))
                },
                onFailure = { e ->
                    Log.e(TAG, "Undo failed", e)
                    _events.emit(RedFlagEvent.Error(e.message ?: "Undo failed"))
                }
            )
        }
    }

    /** Caller invokes this after the snackbar timeout to drop the undo handle. */
    fun clearLastCreatedFlagId() {
        _uiState.update { it.copy(lastCreatedFlagId = null) }
    }

    /** Surface a one-line hint to the user via the existing snackbar host. */
    fun showHint(message: String) {
        viewModelScope.launch { _events.emit(RedFlagEvent.Success(message)) }
    }

    // --- Resolve flag ---

    /**
     * Soft-delete a flag. The screen guards the call site by only rendering
     * the delete button for flags the current teacher created — but we
     * defer the final authorization to the Firestore rule, which rejects
     * any cross-teacher or admin-created delete attempt with PERMISSION_DENIED.
     */
    fun deleteFlag(flagId: String) {
        if (flagId in _uiState.value.busyFlagIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyFlagIds = it.busyFlagIds + flagId) }
            try {
                redFlagRepository.softDeleteFlag(flagId).fold(
                    onSuccess = {
                        _events.emit(RedFlagEvent.Success("Flag deleted"))
                        // Live listener reflects the soft-delete; no reload.
                    },
                    onFailure = { e ->
                        _events.emit(RedFlagEvent.Error(e.message ?: "Failed to delete"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(RedFlagEvent.Error(e.message ?: "Failed to delete"))
            } finally {
                _uiState.update { it.copy(busyFlagIds = it.busyFlagIds - flagId) }
            }
        }
    }

    fun resolveFlag(studentId: String, flagId: String) {
        if (flagId in _uiState.value.busyFlagIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyFlagIds = it.busyFlagIds + flagId) }
            try {
                redFlagRepository.resolveFlag(flagId).fold(
                    onSuccess = {
                        _events.emit(RedFlagEvent.Success("Flag resolved"))
                        // Live listener reflects the resolve; no reload.
                    },
                    onFailure = { e ->
                        // Most common case: Firestore rule denies a teacher
                        // resolving someone else's flag.
                        _events.emit(RedFlagEvent.Error(e.message ?: "Failed to resolve"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(RedFlagEvent.Error(e.message ?: "Failed to resolve"))
            } finally {
                _uiState.update { it.copy(busyFlagIds = it.busyFlagIds - flagId) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadStudentsAndFlags()
    }
}
