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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class FlagFormState(
    val type: String = "homework",       // homework, behavior, performance
    val message: String = "",
    val subject: String = "",
    val severity: String = "low",        // low, medium, high
    val studentId: String = "",
    val studentName: String = "",
    val isSubmitting: Boolean = false
)

data class RedFlagUiState(
    val availableClasses: List<FlagClassSection> = emptyList(),
    val selectedClass: FlagClassSection? = null,
    val students: List<StudentInfo> = emptyList(),
    val flagsByStudent: Map<String, List<StudentFlag>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val formState: FlagFormState = FlagFormState(),
    val selectedStudentId: String? = null,
    val subjectsForClass: List<String> = emptyList(),
    /** Current teacher's Firebase UID — drives delete-button visibility. */
    val currentTeacherUid: String = ""
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

    init {
        // Stamp the current teacher's UID into state once at startup so the
        // screen can decide which delete buttons to render. Auth UID is the
        // only thing the Firestore rule will accept for soft-delete RBAC.
        _uiState.update { it.copy(currentTeacherUid = firebaseAuth.currentUser?.uid.orEmpty()) }
        loadAssignedClasses()
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

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Load students
                val studentsResult = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                val students = studentsResult.getOrNull() ?: emptyList()

                // Load flags for all students in the class
                val flagsResult = redFlagRepository.getFlagsForClass(students)
                val flagsByStudent = flagsResult.getOrNull() ?: emptyMap()

                Log.d(TAG, "Loaded ${students.size} students, ${flagsByStudent.values.sumOf { it.size }} total flags")

                _uiState.update {
                    it.copy(
                        students = students,
                        flagsByStudent = flagsByStudent,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load flags", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // --- Create flag ---

    fun showCreateDialog(studentId: String = "", studentName: String = "") {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                formState = FlagFormState(
                    studentId = studentId,
                    studentName = studentName,
                    subject = it.subjectsForClass.firstOrNull() ?: ""
                )
            )
        }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, formState = FlagFormState()) }
    }

    fun updateFormType(type: String) {
        _uiState.update { it.copy(formState = it.formState.copy(type = type)) }
    }

    fun updateFormMessage(message: String) {
        _uiState.update { it.copy(formState = it.formState.copy(message = message)) }
    }

    fun updateFormSubject(subject: String) {
        _uiState.update { it.copy(formState = it.formState.copy(subject = subject)) }
    }

    fun updateFormSeverity(severity: String) {
        _uiState.update { it.copy(formState = it.formState.copy(severity = severity)) }
    }

    fun updateFormStudent(studentId: String, studentName: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(studentId = studentId, studentName = studentName))
        }
    }

    fun createFlag() {
        val state = _uiState.value
        val form = state.formState

        if (form.studentId.isBlank()) {
            viewModelScope.launch { _events.emit(RedFlagEvent.Error("Select a student")) }
            return
        }
        if (form.message.isBlank()) {
            viewModelScope.launch { _events.emit(RedFlagEvent.Error("Message is required")) }
            return
        }

        // Hydrate denorm fields from the loaded class roster + selected class.
        // Admin RBAC and dashboard analytics depend on className/section/rollNo
        // being populated — empty strings break the teacher-can-access check.
        val classSection = state.selectedClass
        if (classSection == null) {
            viewModelScope.launch { _events.emit(RedFlagEvent.Error("No class selected")) }
            return
        }
        val student = state.students.firstOrNull { it.studentId == form.studentId }
        if (student == null) {
            viewModelScope.launch {
                _events.emit(RedFlagEvent.Error("Student not found in current class roster"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = true)) }
            try {
                val teacherId   = tokenManager.userId.firstOrNull().orEmpty()
                val teacherName = tokenManager.userName.firstOrNull().orEmpty()

                // Match the canonical "Class X" / "Section Y" prefixed form
                // that admin's _normalize_class_key/_normalize_section_key
                // produces, so dashboard filters match exactly.
                val classKey = if (classSection.className.startsWith("Class ", ignoreCase = true)) {
                    classSection.className
                } else "Class ${classSection.className}"
                val sectionKey = if (classSection.section.startsWith("Section ", ignoreCase = true)) {
                    classSection.section
                } else "Section ${classSection.section}"

                val flag = StudentFlag(
                    studentId   = student.studentId,
                    studentName = student.name.ifBlank { form.studentName },
                    rollNo      = student.rollNo,
                    fatherName  = student.fatherName,
                    className   = classKey,
                    section     = sectionKey,
                    type        = form.type,
                    message     = form.message.trim(),
                    subject     = form.subject.trim(),
                    teacherId   = teacherId,
                    teacherName = teacherName,
                    severity    = form.severity,
                    createdAtMs = System.currentTimeMillis(),
                    status      = "active"
                )

                redFlagRepository.createFlag(flag).fold(
                    onSuccess = { flagId ->
                        Log.d(TAG, "Flag created: $flagId")
                        _uiState.update {
                            it.copy(
                                showCreateDialog = false,
                                formState = FlagFormState(),
                                // Auto-focus the just-flagged student so the
                                // right panel immediately shows the new flag.
                                selectedStudentId = student.studentId
                            )
                        }
                        _events.emit(RedFlagEvent.Success("Flag created for ${student.name}"))
                        loadStudentsAndFlags()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to create flag", e)
                        _uiState.update {
                            it.copy(formState = it.formState.copy(isSubmitting = false))
                        }
                        _events.emit(RedFlagEvent.Error(e.message ?: "Failed to create flag"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create flag", e)
                _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = false)) }
                _events.emit(RedFlagEvent.Error(e.message ?: "Failed to create flag"))
            }
        }
    }

    // --- Resolve flag ---

    /**
     * Soft-delete a flag. The screen guards the call site by only rendering
     * the delete button for flags the current teacher created — but we
     * defer the final authorization to the Firestore rule, which rejects
     * any cross-teacher or admin-created delete attempt with PERMISSION_DENIED.
     */
    fun deleteFlag(flagId: String) {
        viewModelScope.launch {
            try {
                redFlagRepository.softDeleteFlag(flagId).fold(
                    onSuccess = {
                        _events.emit(RedFlagEvent.Success("Flag deleted"))
                        loadStudentsAndFlags()
                    },
                    onFailure = { e ->
                        _events.emit(RedFlagEvent.Error(e.message ?: "Failed to delete"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(RedFlagEvent.Error(e.message ?: "Failed to delete"))
            }
        }
    }

    fun resolveFlag(studentId: String, flagId: String) {
        viewModelScope.launch {
            try {
                redFlagRepository.resolveFlag(flagId).fold(
                    onSuccess = {
                        _events.emit(RedFlagEvent.Success("Flag resolved"))
                        loadStudentsAndFlags()
                    },
                    onFailure = { e ->
                        // Most common case: Firestore rule denies a teacher
                        // resolving someone else's flag.
                        _events.emit(RedFlagEvent.Error(e.message ?: "Failed to resolve"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(RedFlagEvent.Error(e.message ?: "Failed to resolve"))
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
