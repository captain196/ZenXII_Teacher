package com.schoolsync.teacher.ui.homework

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.HomeworkStatusEntry
import com.schoolsync.teacher.data.model.HomeworkTeacher
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.teacher.util.toEpochMillisOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeworkClassSection(
    val className: String,
    val section: String
) {
    val displayName: String get() = "$className - $section"
}

data class HomeworkFormState(
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val dueDate: String = "",
    val isSubmitting: Boolean = false
)

data class HomeworkUiState(
    val availableClasses: List<HomeworkClassSection> = emptyList(),
    val availableSubjects: List<String> = emptyList(),
    val selectedClass: HomeworkClassSection? = null,
    val selectedSubjectFilter: String? = null, // null = all subjects
    val homeworkList: List<HomeworkTeacher> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val formState: HomeworkFormState = HomeworkFormState(),
    val selectedHomework: HomeworkTeacher? = null,
    val submissions: List<HomeworkStatusEntry> = emptyList(),
    val students: List<StudentInfo> = emptyList(),
    val showDetailSheet: Boolean = false,
    val isLoadingSubmissions: Boolean = false,
    // Subjects available for the selected class (from assignments)
    val subjectsForClass: List<String> = emptyList(),
    // Phase HW: delete confirmation
    val homeworkToDelete: HomeworkTeacher? = null
)

sealed class HomeworkEvent {
    data class Success(val message: String) : HomeworkEvent()
    data class Error(val message: String) : HomeworkEvent()
}

@HiltViewModel
class HomeworkTeacherViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val tokenManager: TokenManager,
    private val homeworkFirestoreRepo: HomeworkFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeworkVM"
    }

    private val _uiState = MutableStateFlow(HomeworkUiState())
    val uiState: StateFlow<HomeworkUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeworkEvent>()
    val events = _events.asSharedFlow()

    // Cached assignments for subject lookups
    private var allAssignments: List<ClassAssignment> = emptyList()
    private var homeworkListenerJob: Job? = null

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
                            .map { HomeworkClassSection(it.className, it.section) }
                            .distinct()
                        val allSubjects = assignments.map { it.subject }.distinct().sorted()

                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                availableSubjects = allSubjects,
                                selectedClass = classSections.firstOrNull()
                            )
                        }

                        if (classSections.isNotEmpty()) {
                            updateSubjectsForClass(classSections.first())
                            loadStudentsForClass(classSections.first())
                            loadHomework()
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

    private fun updateSubjectsForClass(classSection: HomeworkClassSection) {
        val subjects = allAssignments
            .filter { it.className == classSection.className && it.section == classSection.section }
            .map { it.subject }
            .distinct()
            .sorted()
        _uiState.update { it.copy(subjectsForClass = subjects) }
    }

    fun selectClass(classSection: HomeworkClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        _uiState.update {
            it.copy(
                selectedClass = classSection,
                selectedSubjectFilter = null,
                selectedHomework = null,
                showDetailSheet = false
            )
        }
        updateSubjectsForClass(classSection)
        loadStudentsForClass(classSection)
        loadHomework()
    }

    private fun loadStudentsForClass(classSection: HomeworkClassSection) {
        viewModelScope.launch {
            try {
                val result = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                result.getOrNull()?.let { students ->
                    _uiState.update { it.copy(students = students) }
                }
            } catch (_: Exception) { }
        }
    }

    fun selectSubjectFilter(subject: String?) {
        _uiState.update { it.copy(selectedSubjectFilter = subject) }
        // Fix #3: re-filter immediately since loadHomework is a one-shot call
        loadHomework()
    }

    fun loadHomework() {
        val classSection = _uiState.value.selectedClass ?: return
        val sectionKey = "${com.schoolsync.teacher.util.Constants.classKey(classSection.className)}/${com.schoolsync.teacher.util.Constants.sectionKey(classSection.section)}"

        homeworkListenerJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }

        homeworkListenerJob = viewModelScope.launch {
            try {
                // Firestore-only: read homework for this section
                homeworkFirestoreRepo.getHomework(sectionKey).fold(
                    onSuccess = { homeworkDocs ->
                        val allHomework = homeworkDocs.map { doc ->
                            HomeworkTeacher(
                                hwId = doc.id,
                                title = doc.title,
                                description = doc.description,
                                subject = doc.subject,
                                teacherId = doc.teacherId,
                                teacherName = doc.teacherName,
                                dueDate = doc.dueDate,
                                createdAt = doc.createdAt.toEpochMillisOrNull() ?: 0L,
                                status = doc.status,
                                className = doc.className,
                                section = doc.section
                            )
                        }
                        val subjectFilter = _uiState.value.selectedSubjectFilter
                        val filtered = if (subjectFilter != null) {
                            allHomework.filter { it.subject.equals(subjectFilter, ignoreCase = true) }
                        } else {
                            allHomework
                        }
                        Log.d(TAG, "Firestore loaded: ${filtered.size} homework items")
                        _uiState.update { it.copy(homeworkList = filtered, isLoading = false) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Firestore load failed", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Homework load failed", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // --- Create homework ---

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                formState = HomeworkFormState(
                    subject = it.subjectsForClass.firstOrNull() ?: ""
                )
            )
        }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, formState = HomeworkFormState()) }
    }

    fun updateFormTitle(title: String) {
        _uiState.update { it.copy(formState = it.formState.copy(title = title)) }
    }

    fun updateFormDescription(desc: String) {
        _uiState.update { it.copy(formState = it.formState.copy(description = desc)) }
    }

    fun updateFormSubject(subject: String) {
        _uiState.update { it.copy(formState = it.formState.copy(subject = subject)) }
    }

    fun updateFormDueDate(date: String) {
        _uiState.update { it.copy(formState = it.formState.copy(dueDate = date)) }
    }

    fun createHomework() {
        val state = _uiState.value
        val classSection = state.selectedClass ?: return
        val form = state.formState

        if (form.title.isBlank()) {
            viewModelScope.launch { _events.emit(HomeworkEvent.Error("Title is required")) }
            return
        }
        if (form.subject.isBlank()) {
            viewModelScope.launch { _events.emit(HomeworkEvent.Error("Subject is required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = true)) }
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: ""
                val students = _uiState.value.students
                val dueDate = form.dueDate.ifBlank {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                }

                // Firestore-only: create homework
                homeworkFirestoreRepo.createHomework(
                    title = form.title.trim(),
                    description = form.description.trim(),
                    subject = form.subject.trim(),
                    className = classSection.className,
                    section = classSection.section,
                    dueDate = dueDate,
                    teacherId = teacherId,
                    teacherName = teacherName,
                    totalStudents = students.size
                ).fold(
                    onSuccess = { firestoreHwId ->
                        Log.d(TAG, "Homework created: $firestoreHwId")
                        _uiState.update {
                            it.copy(
                                showCreateDialog = false,
                                formState = HomeworkFormState()
                            )
                        }
                        _events.emit(HomeworkEvent.Success("Homework created successfully"))
                        loadHomework() // Refresh list
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to create homework", e)
                        _uiState.update {
                            it.copy(formState = it.formState.copy(isSubmitting = false))
                        }
                        _events.emit(HomeworkEvent.Error(e.message ?: "Failed to create homework"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create homework", e)
                _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = false)) }
                _events.emit(HomeworkEvent.Error(e.message ?: "Failed to create homework"))
            }
        }
    }

    // --- Delete homework ---

    /** Set to non-null to show delete confirmation dialog. */
    fun confirmDelete(hw: HomeworkTeacher?) {
        _uiState.update { it.copy(
            homeworkToDelete = hw
        )}
    }

    fun executeDelete() {
        val hw = _uiState.value.homeworkToDelete ?: return
        _uiState.update { it.copy(homeworkToDelete = null) }

        viewModelScope.launch {
            try {
                // Fix #7: delete all related submissions first
                val submissions = homeworkFirestoreRepo.getSubmissions(hw.hwId).getOrNull() ?: emptyList()
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                for (sub in submissions) {
                    try { fs.collection("submissions").document(sub.id).delete().await() } catch (_: Exception) {}
                }

                homeworkFirestoreRepo.deleteHomework(hw.hwId).fold(
                    onSuccess = {
                        Log.d(TAG, "Homework + ${submissions.size} submissions deleted: ${hw.hwId}")
                        _events.emit(HomeworkEvent.Success("Homework and ${submissions.size} submission(s) deleted"))
                        _uiState.update { it.copy(selectedHomework = null, showDetailSheet = false) }
                        loadHomework()
                    },
                    onFailure = { e ->
                        _events.emit(HomeworkEvent.Error(e.message ?: "Failed to delete"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(HomeworkEvent.Error(e.message ?: "Failed to delete"))
            }
        }
    }

    // --- Submission detail ---

    fun selectHomework(hw: HomeworkTeacher) {
        _uiState.update {
            it.copy(
                selectedHomework = hw,
                showDetailSheet = true,
                isLoadingSubmissions = true
            )
        }
        loadSubmissions(hw)
    }

    fun hideDetailSheet() {
        submissionListenerJob?.cancel()
        _uiState.update { it.copy(showDetailSheet = false, selectedHomework = null) }
    }

    private var submissionListenerJob: Job? = null

    private fun loadSubmissions(hw: HomeworkTeacher) {
        val classSection = _uiState.value.selectedClass ?: return

        submissionListenerJob?.cancel()

        submissionListenerJob = viewModelScope.launch {
            try {
                // Load students
                val studentsResult = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                val students = studentsResult.getOrNull() ?: emptyList()

                // Firestore-only: load submissions
                val submissionsResult = homeworkFirestoreRepo.getSubmissions(hw.hwId)
                val submissions = submissionsResult.getOrNull()?.map { doc ->
                    HomeworkStatusEntry(
                        studentId = doc.studentId,
                        studentName = doc.studentName,
                        status = doc.status,
                        remark = doc.remark,
                        text = doc.text,
                        files = doc.files,
                        score = doc.score,
                        maxMarks = doc.maxMarks
                    )
                } ?: emptyList()

                Log.d(TAG, "Submissions loaded: ${submissions.size} entries")

                // Fix submissionCount: teacher is staff, rules allow update
                val submittedCount = submissions.size
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("homework").document(hw.hwId)
                        .update("submissionCount", submittedCount)
                        .addOnSuccessListener { Log.d(TAG, "submissionCount updated to $submittedCount") }
                } catch (_: Exception) {}

                _uiState.update {
                    it.copy(
                        students = students,
                        submissions = submissions,
                        isLoadingSubmissions = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load submissions", e)
                _uiState.update { it.copy(isLoadingSubmissions = false) }
            }
        }
    }

    fun markStudentStatus(
        studentId: String,
        studentName: String,
        status: String,
        remark: String = "",
        score: Int = -1
    ) {
        val hw = _uiState.value.selectedHomework ?: return

        viewModelScope.launch {
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: teacherId
                val submissionId = "${hw.hwId}_${studentId}"
                val schoolCode = tokenManager.schoolId.firstOrNull() ?: ""
                val classSection = _uiState.value.selectedClass

                // Fix #2: If submission doc doesn't exist (student hasn't
                // submitted), create it first so reviewSubmission doesn't
                // fail with "doc not found".
                val existing = homeworkFirestoreRepo.getSubmissions(hw.hwId)
                    .getOrNull()?.find { it.studentId == studentId }

                if (existing == null) {
                    // Create the submission doc first
                    val sectionKey = if (classSection != null) {
                        "${com.schoolsync.teacher.util.Constants.classKey(classSection.className)}/${com.schoolsync.teacher.util.Constants.sectionKey(classSection.section)}"
                    } else ""

                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("submissions").document(submissionId)
                        .set(mapOf(
                            "schoolId" to schoolCode,
                            "homeworkId" to hw.hwId,
                            "studentId" to studentId,
                            "studentName" to studentName,
                            "sectionKey" to sectionKey,
                            "status" to status,
                            "text" to "",
                            "files" to emptyList<String>(),
                            "remark" to remark,
                            "score" to score,
                            "maxMarks" to 0,
                            "reviewedBy" to teacherName,
                            "reviewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "submittedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ))
                        .await()
                    loadSubmissions(hw)
                } else {
                    // Fix #5 + #6: remark without status prefix, actual score
                    homeworkFirestoreRepo.reviewSubmission(
                        submissionId = submissionId,
                        remark = remark.trim(),
                        score = score,
                        reviewedBy = teacherName
                    ).fold(
                        onSuccess = {
                            loadSubmissions(hw)
                            loadHomework() // Fix #10: refresh homework list too
                        },
                        onFailure = { e ->
                            _events.emit(HomeworkEvent.Error(e.message ?: "Failed to update"))
                        }
                    )
                }
            } catch (e: Exception) {
                _events.emit(HomeworkEvent.Error(e.message ?: "Failed to update status"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadHomework()
    }
}
