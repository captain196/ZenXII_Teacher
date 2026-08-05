package com.schoolsync.teacher.ui.marks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.MarksDoc
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.teacher.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val practicalError: String? = null,
    val totalError: String? = null
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
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val studentFirestoreRepo: StudentFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "MarksVM"
        // How long to wait for the server to ACK a marks batch before assuming
        // we're offline and surfacing "saved offline, will sync" (MED-5).
        private const val SAVE_ACK_TIMEOUT_MS = 4000L
    }

    private val _uiState = MutableStateFlow(MarksUiState())
    val uiState: StateFlow<MarksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MarksEvent>()
    val events = _events.asSharedFlow()

    // Synchronous re-entry guard for saveMarks() so a fast double-tap on the Save
    // FAB cannot enqueue two batch writes (isSaving alone flips inside the
    // coroutine, one frame too late). Set at the top of saveMarks, cleared in a
    // finally. @Volatile: written/read from the main thread but be explicit.
    @Volatile
    private var saveInProgress = false

    init {
        // Reload when the school's active session changes (propagated into
        // TokenManager by SchoolFirestoreRepository.observeSchool). First
        // emission performs the initial load. Mirrors AttendanceViewModel.
        viewModelScope.launch {
            tokenManager.session
                .distinctUntilChanged()
                .collect { session ->
                    if (!session.isNullOrBlank()) loadInitialData()
                }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Clear downstream selections/results so a session switch never
            // leaves the previous session's exams/subjects/marks on screen.
            _uiState.update {
                it.copy(
                    isLoading = true,
                    availableExams = emptyList(),
                    selectedExam = null,
                    availableSubjects = emptyList(),
                    selectedSubject = null,
                    studentMarks = emptyList(),
                    hasUnsavedChanges = false
                )
            }
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
            _uiState.update { it.copy(error = null) }
            try {
                // Primary: Firestore exams
                examFirestoreRepo.getExams().fold(
                    onSuccess = { examDocs ->
                        // Filter exams applicable to the selected class
                        val filtered = examDocs.filter { doc ->
                            doc.applicableClasses.isEmpty() ||
                                doc.applicableClasses.any { it.equals(state.selectedClassName, ignoreCase = true) }
                        }
                        // Use the bare examId field (NOT it.id, which is the
                        // schoolId-prefixed doc-id) so downstream examSchedule /
                        // marks doc-ids resolve without doubling the schoolId.
                        val exams = filtered.map {
                            ExamInfo(it.examId.ifBlank { it.id }, it.examName)
                        }
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
            _uiState.update { it.copy(error = null) }
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
                                val maxTheory = subjectSchedule.maxTheory.toInt()
                                val maxPractical = subjectSchedule.maxPractical.toInt()
                                val maxTotal = subjectSchedule.maxTotal.toInt()
                                // Total-only datesheet (no theory/practical split):
                                // treat the whole paper as Theory so the teacher can
                                // actually enter marks — Total is read-only and is
                                // derived from theory + practical, so a 0/0 split
                                // would otherwise lock entry at 0.
                                val effTheory =
                                    if (maxTheory == 0 && maxPractical == 0 && maxTotal > 0) maxTotal
                                    else maxTheory
                                SubjectInfo(
                                    subjectId = subjectSchedule.subjectName,
                                    subjectName = subjectSchedule.subjectName,
                                    maxTheory = effTheory,
                                    maxPractical = maxPractical,
                                    maxTotal = maxTotal
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

                // 1) Class roster — the source of truth for WHO appears in the
                //    grid. Without this the teacher could never enter the FIRST
                //    marks: the old code only listed students who already had a
                //    saved marks doc (a chicken-and-egg empty grid).
                val roster = studentFirestoreRepo.getStudentsByClass(
                    state.selectedClassName,
                    state.selectedSection
                ).getOrElse { e ->
                    Log.e(TAG, "Failed to load roster: ${e.message}")
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    return@launch
                }

                // 2) Any marks already saved for this exam + section + subject.
                val existing = examFirestoreRepo.getStudentMarks(
                    examId = exam.examId,
                    sectionKey = sectionKey,
                    subject = subject.subjectId
                ).getOrElse { e ->
                    Log.w(TAG, "Marks fetch failed, showing blank roster: ${e.message}")
                    emptyList()
                }.associateBy { it.studentId }

                // 3) Merge saved marks onto the full roster so every student is
                //    present and editable, with previously-entered values filled.
                val marks = roster
                    .sortedWith(compareBy({ it.rollNo.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }))
                    .mapIndexed { idx, stu ->
                        val sid = stu.studentId.ifBlank { stu.userId }
                        val m = existing[sid]
                        // Preserve fractional marks on load — the admin schema is
                        // Double and a 7.5 must NOT be truncated to 7 on re-save
                        // (silent corruption). formatMark drops a trailing ".0".
                        StudentMark(
                            studentId = sid,
                            rollNo = stu.rollNo.toIntOrNull() ?: (idx + 1),
                            name = stu.name,
                            theory = if (m != null && !m.absent) formatMark(m.theory) else "",
                            practical = if (m != null && !m.absent) formatMark(m.practical) else "",
                            total = when {
                                m == null -> ""
                                m.absent -> "AB"
                                else -> formatMark(m.total)
                            },
                            isAbsent = m?.absent ?: false
                        )
                    }

                _uiState.update {
                    it.copy(studentMarks = marks, isLoading = false, hasUnsavedChanges = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load marks", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateTheory(studentId: String, value: String) {
        updateMark(studentId) { mark ->
            val subject = _uiState.value.selectedSubject
            // Accept fractional input (marks are Double); toDoubleOrNull rejects a
            // stray second '.' or non-numeric text as "Invalid".
            val numVal = value.toDoubleOrNull()
            val error = when {
                value.isNotEmpty() && numVal == null -> "Invalid"
                numVal != null && subject != null && numVal > subject.maxTheory -> "Max ${subject.maxTheory}"
                numVal != null && numVal < 0 -> "Invalid"
                else -> null
            }
            val newTotal = calculateTotal(value, mark.practical)
            mark.copy(theory = value, total = newTotal, theoryError = error, isAbsent = false)
                .withTotalError(subject)
        }
    }

    fun updatePractical(studentId: String, value: String) {
        updateMark(studentId) { mark ->
            val subject = _uiState.value.selectedSubject
            val numVal = value.toDoubleOrNull()
            val error = when {
                value.isNotEmpty() && numVal == null -> "Invalid"
                numVal != null && subject != null && numVal > subject.maxPractical -> "Max ${subject.maxPractical}"
                numVal != null && numVal < 0 -> "Invalid"
                else -> null
            }
            val newTotal = calculateTotal(mark.theory, value)
            mark.copy(practical = value, total = newTotal, practicalError = error, isAbsent = false)
                .withTotalError(subject)
        }
    }

    fun toggleAbsent(studentId: String) {
        updateMark(studentId) { mark ->
            if (mark.isAbsent) {
                // Un-marking absent: clear the "AB" total too. Theory/practical
                // were emptied when AB was set, so the recomputed total is blank
                // and the teacher can enter fresh marks.
                mark.copy(
                    isAbsent = false,
                    total = calculateTotal(mark.theory, mark.practical),
                    totalError = null
                )
            } else {
                mark.copy(
                    isAbsent = true,
                    theory = "",
                    practical = "",
                    total = "AB",
                    theoryError = null,
                    practicalError = null,
                    totalError = null
                )
            }
        }
    }

    /**
     * MED-6: flag when the combined total exceeds the subject's maxTotal — the
     * per-component checks alone can't catch a theory+practical sum over the cap.
     */
    private fun StudentMark.withTotalError(subject: SubjectInfo?): StudentMark {
        val t = total.toDoubleOrNull()
        val err = if (subject != null && exceedsMaxTotal(t, subject.maxTotal, isAbsent))
            "Max ${subject.maxTotal}" else null
        return copy(totalError = err)
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
        val t = theory.toDoubleOrNull() ?: return ""
        val p = practical.toDoubleOrNull() ?: return formatMark(t)
        return formatMark(t + p)
    }

    fun saveMarks() {
        val state = _uiState.value
        val exam = state.selectedExam ?: return
        val subject = state.selectedSubject ?: return

        // MED-4: synchronous re-entry guard — a fast double-tap must not enqueue
        // two batch writes. Set BEFORE anything async; cleared in finally below.
        if (saveInProgress) return

        // Validate — per-component errors AND the combined total-vs-maxTotal cap.
        val hasErrors = state.studentMarks.any {
            it.theoryError != null || it.practicalError != null || it.totalError != null
        }
        if (hasErrors) {
            viewModelScope.launch {
                _events.emit(MarksEvent.SaveError("Please fix validation errors before saving"))
            }
            return
        }

        saveInProgress = true
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val sectionKey = "${Constants.classKey(state.selectedClassName)}/${Constants.sectionKey(state.selectedSection)}"
                val hasPractical = subject.maxPractical > 0

                // Primary: Firestore batch marks save. componentMarks is the
                // CANONICAL shape the admin report cards read component columns
                // from — names "Theory"/"Practical" are detected by the admin's
                // buildMarksDoc (strpos on the lower-cased name). Omit Practical
                // when the subject has none (maxPractical == 0).
                val marksList = state.studentMarks.map { mark ->
                    val theoryVal = if (mark.isAbsent) 0.0 else (mark.theory.toDoubleOrNull() ?: 0.0)
                    val practicalVal = if (mark.isAbsent) 0.0 else (mark.practical.toDoubleOrNull() ?: 0.0)
                    val totalVal = if (mark.isAbsent) 0.0 else (mark.total.toDoubleOrNull() ?: 0.0)
                    val components = buildComponentMarks(theoryVal, practicalVal, hasPractical)
                    MarksDoc(
                        studentId = mark.studentId,
                        studentName = mark.name,
                        componentMarks = components,
                        theory = theoryVal,
                        practical = practicalVal,
                        total = totalVal,
                        maxMarks = subject.maxTotal.toDouble(),
                        absent = mark.isAbsent
                    )
                }

                // MED-5: setDocumentsBatch's commit success only fires on SERVER
                // ack, so offline the coroutine would suspend forever and the Save
                // spinner would hang with no feedback. The write is already
                // durably queued in Firestore's local cache the moment commit() is
                // called, so if the server hasn't acked within the window we tell
                // the teacher it's saved offline (and will sync) instead of hanging.
                val result = withTimeoutOrNull(SAVE_ACK_TIMEOUT_MS) {
                    examFirestoreRepo.saveBatchMarks(
                        examId = exam.examId,
                        sectionKey = sectionKey,
                        className = state.selectedClassName,
                        section = state.selectedSection,
                        subject = subject.subjectId,
                        marksList = marksList
                    )
                }

                if (result == null) {
                    // No server ack in time — offline / very slow link. The batch
                    // is queued locally and will sync automatically.
                    Log.d(TAG, "Save not acked within ${SAVE_ACK_TIMEOUT_MS}ms — treating as offline-queued")
                    _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                    _events.emit(MarksEvent.SaveSuccess("Saved offline — will sync when online"))
                } else {
                    result.fold(
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save marks", e)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(MarksEvent.SaveError(e.message ?: "Failed to save marks"))
            } finally {
                saveInProgress = false
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * MED-7: retry the load that most recently failed, based on how far the
     * selection has progressed (marks → subjects → exams → initial).
     */
    fun retry() {
        _uiState.update { it.copy(error = null) }
        val state = _uiState.value
        when {
            state.selectedExam != null && state.selectedSubject != null -> loadStudentMarks()
            state.selectedExam != null -> loadSubjects()
            state.selectedClassName.isNotEmpty() -> loadExams()
            else -> loadInitialData()
        }
    }

    fun refresh() {
        loadStudentMarks()
    }
}
