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
    /** Teacher marks for non-submitters: studentId → (score, remark). */
    val teacherMarks: Map<String, com.schoolsync.teacher.data.repository.firestore.TeacherMarkEntry> = emptyMap(),
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
        if (form.dueDate.isBlank()) {
            // Was silently falling back to today via .ifBlank — making it
            // impossible to spot a missed picker selection. Force the
            // teacher to pick explicitly.
            viewModelScope.launch { _events.emit(HomeworkEvent.Error("Due date is required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = true)) }
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: ""
                val dueDate = form.dueDate

                // Re-query the roster at write time. The state-cached
                // `_uiState.value.students` was loaded when the form opened
                // and can be stale if a student was added/removed in the
                // interim — submission rate would then start out wrong.
                val freshRoster = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                ).getOrNull() ?: _uiState.value.students
                val totalStudents = freshRoster.size

                homeworkFirestoreRepo.createHomework(
                    title = form.title.trim(),
                    description = form.description.trim(),
                    subject = form.subject.trim(),
                    className = classSection.className,
                    section = classSection.section,
                    dueDate = dueDate,
                    teacherId = teacherId,
                    teacherName = teacherName,
                    totalStudents = totalStudents
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
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                // Cascade — delete every doc in `submissions` AND `teacherMarks`
                // tied to this homework. Skipping teacherMarks (the original
                // bug) left orphans for students who were graded without
                // submitting; they piled up in Firestore and confused later
                // reports.
                val submissions = homeworkFirestoreRepo.getSubmissions(hw.hwId).getOrNull() ?: emptyList()
                for (sub in submissions) {
                    try { fs.collection("submissions").document(sub.id).delete().await() } catch (_: Exception) {}
                }
                val schoolCode = tokenManager.schoolId.firstOrNull() ?: ""
                val markDocs = try {
                    fs.collection("teacherMarks")
                        .whereEqualTo("schoolId", schoolCode)
                        .whereEqualTo("homeworkId", hw.hwId)
                        .get()
                        .await()
                        .documents
                } catch (e: Exception) {
                    Log.w(TAG, "teacherMarks query failed during delete cascade: ${e.message}")
                    emptyList()
                }
                for (m in markDocs) {
                    try { m.reference.delete().await() } catch (_: Exception) {}
                }

                homeworkFirestoreRepo.deleteHomework(hw.hwId).fold(
                    onSuccess = {
                        Log.d(TAG, "Homework + ${submissions.size} submissions + ${markDocs.size} teacherMarks deleted: ${hw.hwId}")
                        val msg = buildString {
                            append("Homework deleted")
                            if (submissions.isNotEmpty()) append(" (${submissions.size} submission(s)")
                            if (markDocs.isNotEmpty()) {
                                if (submissions.isNotEmpty()) append(", ${markDocs.size} mark(s))")
                                else append(" (${markDocs.size} mark(s))")
                            } else if (submissions.isNotEmpty()) append(")")
                        }
                        _events.emit(HomeworkEvent.Success(msg))
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
                // Roster + teacherMarks loaded once per detail-sheet open.
                // These change rarely compared to submissions and don't need
                // a live listener.
                val students = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                ).getOrNull() ?: emptyList()
                val teacherMarks = homeworkFirestoreRepo.getTeacherMarksForHomework(hw.hwId)
                    .getOrNull() ?: emptyMap()

                // Live submissions — every parent submit / teacher review
                // mutates a doc here, and this listener pushes the change
                // straight into the UI without a manual refresh. submission-
                // Count is NOT mirrored here: the parent app's submit
                // transaction owns the increment to keep it monotonic.
                homeworkFirestoreRepo.observeSubmissions(hw.hwId).collect { docs ->
                    val submissions = docs.map { doc ->
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
                    }
                    Log.d(TAG, "Submissions live-update: ${submissions.size} entries")
                    _uiState.update {
                        it.copy(
                            students = students,
                            submissions = submissions,
                            teacherMarks = teacherMarks,
                            isLoadingSubmissions = false
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal — sheet dismissed
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
                val studentName = _uiState.value.students
                    .firstOrNull { it.studentId == studentId }?.name ?: ""

                // Atomic decide-and-write — see HomeworkFirestoreRepository
                // .reviewOrMark for the race-condition rationale. The
                // submission-vs-teacherMark branch lives inside a Firestore
                // transaction so a parent-app submit landing mid-flight
                // cannot create a duplicate-grade state.
                homeworkFirestoreRepo.reviewOrMark(
                    homeworkId = hw.hwId,
                    studentId  = studentId,
                    studentName = studentName,
                    score      = score,
                    remark     = remark,
                    reviewedBy = teacherName,
                    teacherId  = teacherId,
                    status     = status   // Pass through the teacher's pick
                ).fold(
                    onSuccess = {
                        loadSubmissions(hw)
                        loadHomework()
                    },
                    onFailure = { e ->
                        _events.emit(HomeworkEvent.Error(e.message ?: "Failed to save mark"))
                    }
                )
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
