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
 * Per-month payment summary for a class — drives the vertical "Monthly
 * Collection" list with progress bars on the Fees screen.
 *
 * Yearly demands are split into [FeesUiState.annualFee] so the monthly
 * list never mixes "April" with "Yearly Fees" (those carry different
 * meanings and used to confuse the count).
 */
data class MonthlyClassBreakdown(
    val month: String,
    val paidStudents: Int,
    val totalStudents: Int
) {
    val percent: Int get() = if (totalStudents > 0) (paidStudents * 100) / totalStudents else 0
}

/**
 * Class-level roll-up of standalone yearly demands (Pattern B billing —
 * see forensic doc). Pattern A students (annual fee bundled into April)
 * never appear here. Each entry has the per-student status so the UI
 * can surface "who owes the yearly fee" rather than just an aggregate
 * percentage.
 */
data class AnnualFeeInfo(
    val totalStudents: Int,
    val paidStudents: Int,
    val students: List<AnnualFeeStudent>
)

data class AnnualFeeStudent(
    val studentId: String,
    val studentName: String,
    val isPaid: Boolean,
    val balance: Double
)

/**
 * Unified student filter modes. Replaces the old Overview/Defaulters
 * toggle + All/Clear/Dues chips with one consistent filter row.
 */
enum class StudentFilter { ALL, CLEAR, DUES, BLOCKED }

data class FeesUiState(
    val isLoading: Boolean = false,
    val availableClasses: List<ClassSection> = emptyList(),
    val selectedClass: ClassSection? = null,
    val classOverview: ClassFeeOverview? = null,
    val studentStatuses: List<StudentFeeStatus> = emptyList(),
    val defaulters: List<FeeDefaulter> = emptyList(),
    /**
     * Per-month rollup, MONTHLY demands only. The server stores
     * "Yearly Fees" as a `month` value on annual demands — we filter
     * that out here so the monthly list never carries a phantom
     * "Yearly" row alongside April / May / etc.
     */
    val monthlyBreakdown: List<MonthlyClassBreakdown> = emptyList(),
    /**
     * Standalone yearly demands rolled up separately, with per-student
     * detail so the Fees screen can list WHO needs to pay (rather than
     * just an aggregate progress bar). Null when no Pattern B students
     * exist in the class — the Annual Fee card then doesn't mount.
     */
    val annualFee: AnnualFeeInfo? = null,
    val selectedFilter: StudentFilter = StudentFilter.ALL,
    /**
     * Phase 8B: studentId → latest fee-reminder sent_date (ISO) for the
     * school/session. Drives the "Reminded 2h ago" badge on student rows.
     */
    val lastReminderByStudent: Map<String, String> = emptyMap(),
    /**
     * SW4-companion-C parity (2026-05-27): per-student paid-month list,
     * derived from the same observeClassFeeDemands stream that drives
     * monthlyBreakdown. Drives the green "Paid:" chip row on each Fees
     * student card so the screen visually mirrors the StudentsScreen
     * FeeSnapshotCard's paid+unpaid layout. Sorted in academic order
     * (April → March, "Yearly Fees" relabelled to "Annual" by the UI).
     */
    val paidMonthsByStudent: Map<String, List<String>> = emptyMap(),
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
        /**
         * Server-side `month` value used for any annual / one-time
         * demand. `Fee_firestore_txn::periodToMonth` strips a trailing
         * year suffix — "Yearly Fees 2026-27" → "Yearly Fees" — so the
         * raw demand doc carries this exact string. Anything that's
         * NOT this label (and is non-blank) is a real monthly demand.
         */
        private const val YEARLY_MONTH_LABEL = "Yearly Fees"
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
        // Reset filter to All when switching classes — previous filter
        // (e.g. "Blocked 3") may not apply to the new class.
        _uiState.update { it.copy(selectedClass = classSection, selectedFilter = StudentFilter.ALL) }
        classSwitchJob?.cancel()
        classSwitchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            // Guard against stale delayed fires if the user moved on.
            if (_uiState.value.selectedClass != classSection) return@launch
            loadFees(classSection.className, classSection.section)
            attachClassListeners(classSection.className, classSection.section)
        }
    }

    fun setFilter(filter: StudentFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
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
     *     class snapshot, defaulter list, and total-pending amount all
     *     recompute. `.drop(1)` skips the initial snapshot (the
     *     `loadFees` call from selectClass already covered it) so we
     *     don't double-load.
     *
     *   • Demands listener — derives the per-month vertical breakdown
     *     reactively. Yearly demands are split into a separate
     *     `annualBreakdown` so the monthly list stays clean.
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

                    // Split demands by periodicity. Yearly fees can't be
                    // meaningfully grouped alongside April / May / etc.,
                    // so they get their own roll-up card on the screen.
                    val (yearlyDemands, monthlyDemands) = demands.partition {
                        it.month == YEARLY_MONTH_LABEL
                    }

                    val academicOrder = listOf(
                        "April","May","June","July","August","September",
                        "October","November","December","January","February","March"
                    )
                    val perMonth = monthlyDemands
                        .groupBy { it.month.ifBlank { "Unknown" } }
                        .map { (month, monthDemands) ->
                            // A student in this month is "paid" iff every
                            // demand doc for them in that month has
                            // status="paid". Server writes lowercase per
                            // FeeCollectionService; we trust that contract.
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

                    val annual = if (yearlyDemands.isEmpty()) null else {
                        val byStudent = yearlyDemands.groupBy { it.studentId }
                        val list = byStudent.map { (sid, list) ->
                            AnnualFeeStudent(
                                studentId = sid,
                                studentName = list.firstOrNull()?.studentName.orEmpty(),
                                // Per-student paid iff every yearly demand
                                // for them is status="paid". Mirrors the
                                // monthly aggregator's contract.
                                isPaid = list.isNotEmpty() && list.all { it.status == "paid" },
                                balance = list.sumOf { d -> d.balance }
                            )
                        }.sortedBy { it.studentId }
                        AnnualFeeInfo(
                            totalStudents = list.size,
                            paidStudents = list.count { it.isPaid },
                            students = list
                        )
                    }

                    // SW4-companion-C parity (2026-05-27): derive paid
                    // months per student. A month enters a student's paid
                    // list iff every demand for that student in that month
                    // (monthly or yearly) has status="paid". Mirrors the
                    // StudentsViewModel.selectedDemandsJob contract.
                    val paidByStudent: Map<String, List<String>> = demands
                        .groupBy { it.studentId }
                        .mapValues { (_, studentDemands) ->
                            studentDemands
                                .groupBy { it.month.ifBlank { "Unknown" } }
                                .filter { (_, group) ->
                                    group.isNotEmpty() && group.all { it.status == "paid" }
                                }
                                .keys
                                .sortedBy { m ->
                                    val idx = academicOrder.indexOf(m)
                                    when {
                                        idx >= 0 -> idx
                                        m == YEARLY_MONTH_LABEL -> academicOrder.size
                                        else -> academicOrder.size + 1
                                    }
                                }
                        }

                    _uiState.update {
                        it.copy(
                            monthlyBreakdown = perMonth,
                            annualFee = annual,
                            paidMonthsByStudent = paidByStudent
                        )
                    }
                }
        }
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
