package com.schoolsync.teacher.ui.fees

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.ClassFeeOverview
import com.schoolsync.teacher.data.model.FeeDefaulter
import com.schoolsync.teacher.data.model.StudentFeeStatus
import com.schoolsync.teacher.data.repository.FeeRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.teacher.ui.attendance.ClassSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-month payment summary for a class — drives the "Apr 28/30 ·
 * May 25/30 …" breakdown chips on the class summary card.
 */
data class MonthlyClassBreakdown(
    val month: String,
    val paidStudents: Int,
    val totalStudents: Int
)

data class FeesUiState(
    val isLoading: Boolean = false,
    val availableClasses: List<ClassSection> = emptyList(),
    val selectedClass: ClassSection? = null,
    val classOverview: ClassFeeOverview? = null,
    val studentStatuses: List<StudentFeeStatus> = emptyList(),
    val defaulters: List<FeeDefaulter> = emptyList(),
    val monthlyBreakdown: List<MonthlyClassBreakdown> = emptyList(),
    val selectedView: String = "summary", // "summary" or "defaulters"
    /**
     * Phase 8B: studentId → latest fee-reminder sent_date (ISO) for the
     * school/session. Populated from feeReminderLog and merged into the
     * DefaulterCard as a "Reminded 2h ago" badge. Empty map when no
     * reminders have ever been sent (pre-Phase-8A) or the listener hasn't
     * emitted yet.
     */
    val lastReminderByStudent: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

@HiltViewModel
class FeesTeacherViewModel @Inject constructor(
    private val feeRepository: FeeRepository,
    private val teacherRepository: TeacherRepository,
    private val feeFirestoreRepo: FeeFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FeesTeacherVM"
    }

    private val _uiState = MutableStateFlow(FeesUiState())
    val uiState: StateFlow<FeesUiState> = _uiState.asStateFlow()

    /** Cancelled and re-attached on every selectClass() so listeners
     *  always target the currently-displayed class only. */
    private var defaultersListenerJob: Job? = null
    private var demandsListenerJob: Job? = null
    private var remindersListenerJob: Job? = null

    init {
        loadAssignedClasses()
    }

    private fun loadAssignedClasses() {
        viewModelScope.launch {
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        Log.d(TAG, "Loaded ${assignments.size} assignments")
                        val classSections = assignments
                            .map { ClassSection(it.className, it.section) }
                            .distinct()
                        Log.d(TAG, "Distinct classes: ${classSections.map { it.displayName }}")
                        val firstClass = classSections.firstOrNull()
                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                selectedClass = firstClass
                            )
                        }
                        if (firstClass != null) {
                            loadFees(firstClass.className, firstClass.section)
                            attachClassListeners(firstClass.className, firstClass.section)
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load assignments: ${e.message}", e)
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load classes", e)
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    // Debounce rapid class taps — holds the latest selection and only
    // re-attaches listeners after the user has settled. Without this, a
    // fast A → B → C tap sequence fires three listener re-attachments
    // that all race each other and leak snapshot subscriptions.
    private var classSwitchJob: Job? = null

    fun selectClass(classSection: ClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        _uiState.update { it.copy(selectedClass = classSection) }
        classSwitchJob?.cancel()
        classSwitchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            // Guard against stale delayed fires if the user moved on.
            if (_uiState.value.selectedClass != classSection) return@launch
            loadFees(classSection.className, classSection.section)
            attachClassListeners(classSection.className, classSection.section)
        }
    }

    /**
     * Pull-to-refresh handler — cancels all active class listeners and
     * re-attaches them + re-runs the one-shot fetch. Recovers from any
     * silently-dead Flow that the repo-level .catch absorbed.
     */
    fun refreshAll() {
        val selected = _uiState.value.selectedClass ?: return
        loadFees(selected.className, selected.section)
        attachClassListeners(selected.className, selected.section)
    }

    fun loadFees(className: String, section: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val overviewDeferred = async { feeRepository.getClassFeeOverview(className, section) }
                val statusesDeferred = async { feeRepository.getStudentFeeStatuses(className, section) }
                val defaultersDeferred = async { feeRepository.getDefaulterList(className, section) }

                val overviewResult = overviewDeferred.await()
                val statusesResult = statusesDeferred.await()
                val defaultersResult = defaultersDeferred.await()

                _uiState.update { current ->
                    current.copy(
                        classOverview = overviewResult.getOrNull(),
                        studentStatuses = statusesResult.getOrElse { emptyList() },
                        defaulters = defaultersResult.getOrElse { emptyList() },
                        isLoading = false,
                        errorMessage = overviewResult.exceptionOrNull()?.message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fees", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    /**
     * Subscribe to class-scoped Firestore listeners so the screen stays
     * in sync with parent payments without manual refresh:
     *
     *   • Defaulters listener — every change re-runs `loadFees` so the
     *     "X of Y paid" count, defaulter list, and total-pending amount
     *     all recompute. `.drop(1)` skips the initial snapshot (the
     *     `loadFees` call from selectClass already covered it) so we
     *     don't double-load.
     *
     *   • Demands listener — derives the per-month "Apr 28/30 · May 25/30"
     *     breakdown reactively. Each demand has a `month` field; we
     *     group, count per-student paid status, then publish.
     */
    private fun attachClassListeners(className: String, section: String) {
        defaultersListenerJob?.cancel()
        demandsListenerJob?.cancel()
        remindersListenerJob?.cancel()

        // Phase 8B: the reminder log is school-wide (not class-specific), so
        // we only need one subscription — it lives across class switches
        // but we re-attach here to keep lifecycle grouped with the other
        // listeners and respect onCleared cancellation.
        remindersListenerJob = viewModelScope.launch {
            feeFirestoreRepo.observeReminderLog()
                .collect { latestByStudent ->
                    _uiState.update { it.copy(lastReminderByStudent = latestByStudent) }
                }
        }

        defaultersListenerJob = viewModelScope.launch {
            feeFirestoreRepo.observeClassDefaulters(className, section)
                .drop(1)
                .collect {
                    val current = _uiState.value.selectedClass ?: return@collect
                    if (current.className == className && current.section == section) {
                        Log.d(TAG, "Defaulters changed for ${className}/${section} — reloading aggregates")
                        loadFees(className, section)
                    }
                }
        }

        demandsListenerJob = viewModelScope.launch {
            feeFirestoreRepo.observeClassFeeDemands(className, section)
                .collect { demands ->
                    val current = _uiState.value.selectedClass ?: return@collect
                    if (current.className != className || current.section != section) return@collect

                    val academicOrder = listOf(
                        "April","May","June","July","August","September",
                        "October","November","December","January","February","March"
                    )
                    val perMonth = demands
                        .groupBy { it.month.ifBlank { "Unknown" } }
                        .map { (month, monthDemands) ->
                            // A student in this month is "paid" if every demand
                            // doc for them in that month is status="paid".
                            val byStudent = monthDemands.groupBy { it.studentId }
                            val total = byStudent.size
                            val paid = byStudent.count { (_, list) ->
                                list.isNotEmpty() && list.all { it.status == "paid" }
                            }
                            MonthlyClassBreakdown(
                                month = month,
                                paidStudents = paid,
                                totalStudents = total
                            )
                        }
                        .sortedBy { mb ->
                            val idx = academicOrder.indexOf(mb.month)
                            if (idx >= 0) idx else 99
                        }

                    _uiState.update { it.copy(monthlyBreakdown = perMonth) }
                }
        }
    }

    fun switchView(view: String) {
        _uiState.update { it.copy(selectedView = view) }
    }

    fun refresh() {
        val selected = _uiState.value.selectedClass ?: return
        loadFees(selected.className, selected.section)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        defaultersListenerJob?.cancel()
        demandsListenerJob?.cancel()
        remindersListenerJob?.cancel()
    }
}
