package com.schoolsync.teacher.ui.results

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.ResultDoc
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.teacher.ui.marks.ExamInfo
import com.schoolsync.teacher.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One student's row in the ranked results list, with pre-computed display cells. */
data class ResultRow(
    val studentId: String,
    val rank: Int,
    val rollNo: String,
    val name: String,
    val cells: ResultDisplayCells,
    val subjects: List<SubjectRow>
)

/** One subject's line in a student's expandable breakdown. */
data class SubjectRow(
    val name: String,
    val cells: ResultDisplayCells
)

data class ResultsUiState(
    val availableClasses: List<Pair<String, String>> = emptyList(),
    val selectedClassName: String = "",
    val selectedSection: String = "",
    val availableExams: List<ExamInfo> = emptyList(),
    val selectedExam: ExamInfo? = null,
    val results: List<ResultRow> = emptyList(),
    val isLoading: Boolean = false,
    // Distinct from an error: the exam is a Draft / its results live in the
    // server-only `resultsStaging` and haven't been published to `results` yet.
    val notPublished: Boolean = false,
    val error: String? = null
)

/**
 * Read-only view of PUBLISHED exam results (RESIDUAL-4). Mirrors MarksViewModel:
 * session-reactive, assigned-classes driven, exam list from Firestore. It reads
 * ONLY the published `results` collection via [ExamFirestoreRepository]; the
 * unpublished `resultsStaging` is client-denied and never touched here.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "ResultsVM"
    }

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        // Reload when the active session changes; first emission is the initial
        // load. Mirrors MarksViewModel.
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
            _uiState.update {
                it.copy(
                    isLoading = true,
                    availableExams = emptyList(),
                    selectedExam = null,
                    results = emptyList(),
                    notPublished = false,
                    error = null
                )
            }
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
                    if (classPairs.isNotEmpty()) loadExams()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun selectClass(className: String, section: String) {
        _uiState.update {
            it.copy(
                selectedClassName = className,
                selectedSection = section,
                selectedExam = null,
                results = emptyList(),
                notPublished = false
            )
        }
        loadExams()
    }

    fun selectExam(exam: ExamInfo) {
        _uiState.update { it.copy(selectedExam = exam, results = emptyList(), notPublished = false) }
        loadResults()
    }

    private fun loadExams() {
        val state = _uiState.value
        if (state.selectedClassName.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            examFirestoreRepo.getExams().fold(
                onSuccess = { examDocs ->
                    val filtered = examDocs.filter { doc ->
                        doc.applicableClasses.isEmpty() ||
                            doc.applicableClasses.any { it.equals(state.selectedClassName, ignoreCase = true) }
                    }
                    // Use the bare examId (not the schoolId-prefixed doc id) so the
                    // result sectionKey query resolves correctly — same as MarksVM.
                    val exams = filtered.map { ExamInfo(it.examId.ifBlank { it.id }, it.examName) }
                    _uiState.update { it.copy(availableExams = exams) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load exams: ${e.message}")
                    _uiState.update { it.copy(error = e.message) }
                }
            )
        }
    }

    private fun loadResults() {
        val state = _uiState.value
        val exam = state.selectedExam ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, notPublished = false) }
            val sectionKey = "${Constants.classKey(state.selectedClassName)}/${Constants.sectionKey(state.selectedSection)}"
            examFirestoreRepo.getSectionResults(exam.examId, sectionKey).fold(
                onSuccess = { docs ->
                    if (docs.isEmpty()) {
                        // Empty published set = Draft/unpublished exam. Distinct
                        // from an error — surfaced as "Results not published yet".
                        _uiState.update {
                            it.copy(isLoading = false, results = emptyList(), notPublished = true)
                        }
                    } else {
                        val rows = docs.map { toRow(it) }
                            // Authoritative rank first (absent → rank 0 → sink to end);
                            // the query already returns percentage-desc as a tiebreak.
                            .sortedWith(compareBy { if (it.rank > 0) it.rank else Int.MAX_VALUE })
                        _uiState.update {
                            it.copy(isLoading = false, results = rows, notPublished = false)
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load results: ${e.message}")
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    private fun toRow(doc: ResultDoc): ResultRow {
        val subjectRows = doc.subjects.entries
            .sortedBy { it.key.lowercase() }
            .map { (name, sub) -> SubjectRow(name = name, cells = cellsFor(sub)) }
        return ResultRow(
            studentId = doc.studentId,
            rank = doc.rank,
            rollNo = doc.rollNo,
            name = doc.studentName,
            cells = cellsFor(doc),
            subjects = subjectRows
        )
    }

    /** Retry the load that most recently failed, based on selection progress. */
    fun retry() {
        _uiState.update { it.copy(error = null) }
        val state = _uiState.value
        when {
            state.selectedExam != null -> loadResults()
            state.selectedClassName.isNotEmpty() -> loadExams()
            else -> loadInitialData()
        }
    }
}
