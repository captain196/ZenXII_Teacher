package com.schoolsync.teacher.ui.redflags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val subjectsForClass: List<String> = emptyList()
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
    private val tokenManager: TokenManager
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
        val form = _uiState.value.formState

        if (form.studentId.isBlank()) {
            viewModelScope.launch { _events.emit(RedFlagEvent.Error("Select a student")) }
            return
        }
        if (form.message.isBlank()) {
            viewModelScope.launch { _events.emit(RedFlagEvent.Error("Message is required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = true)) }
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: ""

                val flag = StudentFlag(
                    type = form.type,
                    message = form.message.trim(),
                    subject = form.subject.trim(),
                    teacherId = teacherId,
                    teacherName = teacherName,
                    severity = form.severity,
                    timestamp = System.currentTimeMillis(),
                    status = "active",
                    studentName = form.studentName
                )

                redFlagRepository.createFlag(form.studentId, flag).fold(
                    onSuccess = { flagId ->
                        Log.d(TAG, "Flag created: $flagId")
                        _uiState.update {
                            it.copy(showCreateDialog = false, formState = FlagFormState())
                        }
                        _events.emit(RedFlagEvent.Success("Flag created"))
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

    fun resolveFlag(studentId: String, flagId: String) {
        viewModelScope.launch {
            try {
                redFlagRepository.resolveFlag(studentId, flagId).fold(
                    onSuccess = {
                        _events.emit(RedFlagEvent.Success("Flag resolved"))
                        loadStudentsAndFlags()
                    },
                    onFailure = { e ->
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
