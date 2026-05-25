package com.schoolsync.teacher.ui.marks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.MarksDoc
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.teacher.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamInfo(
    val examId: String,
    val examName: String
)

data class SubjectInfo(
    val subjectId: String,
    val subjectName: String,
    val maxTheory: Int = 80,
    val maxPractical: Int = 20,
    val maxTotal: Int = 100
)

data class StudentMark(
    val studentId: String,
    val rollNo: Int,
    val name: String,
    val theory: String = "",
    val practical: String = "",
    val total: String = "",
    val isAbsent: Boolean = false,
    val theoryError: String? = null,
    val practicalError: String? = null
)

data class MarksUiState(
    val availableExams: List<ExamInfo> = emptyList(),
    val selectedExam: ExamInfo? = null,
    val availableSubjects: List<SubjectInfo> = emptyList(),
    val selectedSubject: SubjectInfo? = null,
    val selectedClassName: String = "",
    val selectedSection: String = "",
    val availableClasses: List<Pair<String, String>> = emptyList(),
    val studentMarks: List<StudentMark> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val hasUnsavedChanges: Boolean = false
)

sealed class MarksEvent {
    data class SaveSuccess(val message: String) : MarksEvent()
    data class SaveError(val message: String) : MarksEvent()
}

@HiltViewModel
class MarksViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val examFirestoreRepo: ExamFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MarksVM"
    }

    private val _uiState = MutableStateFlow(MarksUiState())
    val uiState: StateFlow<MarksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MarksEvent>()
    val events = _events.asSharedFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load assigned classes from teacher profile
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        val classPairs = assignments
                            .map { it.className to it.section }
                            .distinct()
                        _uiState.update {
                            it.copy(
                                availableClasses = classPairs,
                                selectedClassName = classPairs.firstOrNull()?.first ?: "",
                                selectedSection = classPairs.firstOrNull()?.second ?: "",
                                isLoading = false
                            )
                        }
                        // Load exams for the first class
                        if (classPairs.isNotEmpty()) {
                            loadExams()
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectExam(exam: ExamInfo) {
        _uiState.update { it.copy(selectedExam = exam, selectedSubject = null, hasUnsavedChanges = false) }
        loadSubjects()
    }

    fun selectSubject(subject: SubjectInfo) {
        _uiState.update { it.copy(selectedSubject = subject, hasUnsavedChanges = false) }
        loadStudentMarks()
    }

    fun selectClass(className: String, section: String) {
        _uiState.update {
            it.copy(
                selectedClassName = className,
                selectedSection = section,
                selectedExam = null,
                selectedSubject = null,
                studentMarks = emptyList(),
                hasUnsavedChanges = false
            )
        }
        loadExams()
    }

    private fun loadExams() {
        val state = _uiState.value
        if (state.selectedClassName.isEmpty()) return

        viewModelScope.launch {
            try {
                // Primary: Firestore exams
                examFirestoreRepo.getExams().fold(
                    onSuccess = { examDocs ->
                        // Filter exams applicable to the selected class
                        val filtered = examDocs.filter { doc ->
                            doc.applicableClasses.isEmpty() ||
                                doc.applicableClasses.any { it.equals(state.selectedClassName, ignoreCase = true) }
                        }
                        val exams = filtered.map { ExamInfo(it.id, it.examName) }
                        _uiState.update { it.copy(availableExams = exams) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load exams: ${e.message}")
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun loadSubjects() {
        val state = _uiState.value
        val exam = state.selectedExam ?: return

        viewModelScope.launch {
            try {
                // Primary: Firestore exam schedule for subject details
                examFirestoreRepo.getExamSchedule(
                    examId = exam.examId,
                    className = state.selectedClassName,
                    section = state.selectedSection
                ).fold(
                    onSuccess = { scheduleDoc ->
                        if (scheduleDoc != null && scheduleDoc.subjects.isNotEmpty()) {
                            val subjects = scheduleDoc.subjects.map { subjectSchedule ->
                                SubjectInfo(
                                    subjectId = subjectSchedule.subjectName,
                                    subjectName = subjectSchedule.subjectName,
                                    maxTheory = subjectSchedule.maxTheory.toInt(),
                                    maxPractical = subjectSchedule.maxPractical.toInt(),
                                    maxTotal = subjectSchedule.maxTotal.toInt()
                                )
                            }
                            _uiState.update { it.copy(availableSubjects = subjects) }
                        } else {
                            _uiState.update { it.copy(availableSubjects = emptyList()) }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load exam schedule: ${e.message}")
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun loadStudentMarks() {
        val state = _uiState.value
        val exam = state.selectedExam ?: return
        val subject = state.selectedSubject ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val sectionKey = "${Constants.classKey(state.selectedClassName)}/${Constants.sectionKey(state.selectedSection)}"

                // Primary: Firestore student marks
                examFirestoreRepo.getStudentMarks(
                    examId = exam.examId,
                    sectionKey = sectionKey,
                    subject = subject.subjectId
                ).fold(
                    onSuccess = { marksDocs ->
                        if (marksDocs.isNotEmpty()) {
                            val marks = marksDocs.map { doc ->
                                StudentMark(
                                    studentId = doc.studentId,
                                    rollNo = doc.studentName.hashCode() % 1000, // Will be overwritten below
                                    name = doc.studentName,
                                    theory = if (!doc.absent) doc.theory.toInt().toString() else "",
                                    practical = if (!doc.absent) doc.practical.toInt().toString() else "",
                                    total = if (!doc.absent) doc.total.toInt().toString()
                                    else "AB",
                                    isAbsent = doc.absent
                                )
                            }.sortedBy { it.rollNo }
                            _uiState.update {
                                it.copy(studentMarks = marks, isLoading = false, hasUnsavedChanges = false)
                            }
                        } else {
                            _uiState.update { it.copy(studentMarks = emptyList(), isLoading = false) }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load student marks: ${e.message}")
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load marks", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateTheory(studentId: String, value: String) {
        updateMark(studentId) { mark ->
            val subject = _uiState.value.selectedSubject
            val numVal = value.toIntOrNull()
            val error = when {
                value.isNotEmpty() && numVal == null -> "Invalid"
                numVal != null && subject != null && numVal > subject.maxTheory -> "Max ${subject.maxTheory}"
                numVal != null && numVal < 0 -> "Invalid"
                else -> null
            }
            val newTotal = calculateTotal(value, mark.practical)
            mark.copy(theory = value, total = newTotal, theoryError = error, isAbsent = false)
        }
    }

    fun updatePractical(studentId: String, value: String) {
        updateMark(studentId) { mark ->
            val subject = _uiState.value.selectedSubject
            val numVal = value.toIntOrNull()
            val error = when {
                value.isNotEmpty() && numVal == null -> "Invalid"
                numVal != null && subject != null && numVal > subject.maxPractical -> "Max ${subject.maxPractical}"
                numVal != null && numVal < 0 -> "Invalid"
                else -> null
            }
            val newTotal = calculateTotal(mark.theory, value)
            mark.copy(practical = value, total = newTotal, practicalError = error, isAbsent = false)
        }
    }

    fun toggleAbsent(studentId: String) {
        updateMark(studentId) { mark ->
            if (mark.isAbsent) {
                mark.copy(isAbsent = false)
            } else {
                mark.copy(
                    isAbsent = true,
                    theory = "",
                    practical = "",
                    total = "AB",
                    theoryError = null,
                    practicalError = null
                )
            }
        }
    }

    private fun updateMark(studentId: String, transform: (StudentMark) -> StudentMark) {
        _uiState.update { state ->
            val updated = state.studentMarks.map { mark ->
                if (mark.studentId == studentId) transform(mark) else mark
            }
            state.copy(studentMarks = updated, hasUnsavedChanges = true)
        }
    }

    private fun calculateTotal(theory: String, practical: String): String {
        val t = theory.toIntOrNull() ?: return ""
        val p = practical.toIntOrNull() ?: return t.toString()
        return (t + p).toString()
    }

    fun saveMarks() {
        val state = _uiState.value
        val exam = state.selectedExam ?: return
        val subject = state.selectedSubject ?: return

        // Validate
        val hasErrors = state.studentMarks.any { it.theoryError != null || it.practicalError != null }
        if (hasErrors) {
            viewModelScope.launch {
                _events.emit(MarksEvent.SaveError("Please fix validation errors before saving"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val sectionKey = "${Constants.classKey(state.selectedClassName)}/${Constants.sectionKey(state.selectedSection)}"

                // Primary: Firestore batch marks save
                val marksList = state.studentMarks.map { mark ->
                    MarksDoc(
                        studentId = mark.studentId,
                        studentName = mark.name,
                        theory = if (mark.isAbsent) 0.0 else (mark.theory.toDoubleOrNull() ?: 0.0),
                        practical = if (mark.isAbsent) 0.0 else (mark.practical.toDoubleOrNull() ?: 0.0),
                        total = if (mark.isAbsent) 0.0 else (mark.total.toDoubleOrNull() ?: 0.0),
                        absent = mark.isAbsent
                    )
                }

                examFirestoreRepo.saveBatchMarks(
                    examId = exam.examId,
                    sectionKey = sectionKey,
                    className = state.selectedClassName,
                    section = state.selectedSection,
                    subject = subject.subjectId,
                    marksList = marksList
                ).fold(
                    onSuccess = { count ->
                        Log.d(TAG, "Firestore: saved marks for $count students")

                        _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                        _events.emit(MarksEvent.SaveSuccess("Marks saved for $count students"))
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isSaving = false) }
                        _events.emit(MarksEvent.SaveError(e.message ?: "Failed to save marks"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save marks", e)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(MarksEvent.SaveError(e.message ?: "Failed to save marks"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadStudentMarks()
    }
}
