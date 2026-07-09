package com.schoolsync.teacher.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.AttendanceData
import com.schoolsync.teacher.data.model.AttendanceStatus as ModelAttendanceStatus
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.AttendanceApiError
import com.schoolsync.teacher.data.repository.AttendanceApiRepository
import com.schoolsync.teacher.data.repository.LateMark
import com.schoolsync.teacher.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.teacher.util.Constants
import com.schoolsync.teacher.util.RoleHelper
import com.schoolsync.teacher.util.debugLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * UI attendance status mirrors the model enum for display purposes.
 * Cycling: P -> A -> L -> H -> T -> V -> P
 */
enum class AttendanceStatus(val code: String, val label: String) {
    PRESENT("P", "Present"),
    ABSENT("A", "Absent"),
    LEAVE("L", "Leave"),
    HOLIDAY("H", "Holiday"),
    TARDY("T", "Tardy"),
    VACATION("V", "Vacation");

    // Tap cycle: P -> A -> L -> T -> P. HOLIDAY and VACATION are deliberately
    // NOT in the cycle: they are admin/system-set states shown from server data,
    // and `saveAttendance` maps them to "no delta" (server default = Present).
    // Previously they WERE tappable, so a teacher could visibly set Holiday and
    // have it silently saved as Present — silent data loss. Tapping an
    // admin-set H/V now moves it to a user-markable state (Present) which the
    // save can actually persist.
    fun next(): AttendanceStatus = when (this) {
        PRESENT -> ABSENT
        ABSENT -> LEAVE
        LEAVE -> TARDY
        TARDY -> PRESENT
        HOLIDAY -> PRESENT
        VACATION -> PRESENT
    }

    fun toModel(): ModelAttendanceStatus = when (this) {
        PRESENT -> ModelAttendanceStatus.PRESENT
        ABSENT -> ModelAttendanceStatus.ABSENT
        LEAVE -> ModelAttendanceStatus.LEAVE
        HOLIDAY -> ModelAttendanceStatus.HOLIDAY
        TARDY -> ModelAttendanceStatus.TARDY
        VACATION -> ModelAttendanceStatus.VACATION
    }

    companion object {
        fun fromModel(model: ModelAttendanceStatus): AttendanceStatus = when (model) {
            ModelAttendanceStatus.PRESENT -> PRESENT
            ModelAttendanceStatus.ABSENT -> ABSENT
            ModelAttendanceStatus.LEAVE -> LEAVE
            ModelAttendanceStatus.HOLIDAY -> HOLIDAY
            ModelAttendanceStatus.TARDY -> TARDY
            ModelAttendanceStatus.VACATION -> VACATION
        }

        fun fromCode(code: String): AttendanceStatus {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: PRESENT
        }

        /**
         * Decode a single dayWise char to a status, or null when the char is not
         * a recognised code (padding, placeholder, unmarked). Callers must treat
         * null as "not marked" — NOT as Present. `fromCode` defaulting unknown
         * chars to PRESENT silently invented present marks for placeholder days
         * and threw off the "remaining/Not marked" totals.
         */
        fun fromCodeOrNull(code: String): AttendanceStatus? {
            return entries.find { it.code.equals(code, ignoreCase = true) }
        }
    }
}

/**
 * Mirror of the server's _stage() output. Drives the Save UI: free editing,
 * editing with reason, or locked → correction-request flow.
 */
enum class Stage(val label: String) {
    S1_FREE("Editable"),
    S2_RESTRICTED("Editable with reason"),
    S3_LOCKED("Locked"),
    UNKNOWN("Loading…");

    companion object {
        fun fromServer(s: String?): Stage = when (s) {
            "S1_FREE" -> S1_FREE
            "S2_RESTRICTED" -> S2_RESTRICTED
            "S3_LOCKED" -> S3_LOCKED
            else -> UNKNOWN
        }
    }
}

data class StudentAttendanceRow(
    val studentId: String,
    val rollNo: Int,
    val name: String,
    /** Map<DayOfMonth (1-31), AttendanceStatus> */
    val dayStatuses: MutableMap<Int, AttendanceStatus> = mutableMapOf()
)

data class ClassSection(
    val className: String,
    val section: String
) {
    val displayName: String get() = "$className - $section"
}

data class AttendanceUiState(
    val availableClasses: List<ClassSection> = emptyList(),
    val selectedClass: ClassSection? = null,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),  // 0-indexed
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val students: List<StudentAttendanceRow> = emptyList(),
    val daysInMonth: Int = 31,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val hasUnsavedChanges: Boolean = false,
    // Confirmation dialog before discarding unsaved marks on a class/month
    // switch or refresh (see guardUnsaved).
    val showDiscardDialog: Boolean = false,
    val todayDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    // Default false: a teacher is read-only until an assignment confirms them as
    // the class teacher. Defaulting true briefly showed an editable roster for a
    // non-class-teacher until the load resolved (or if it failed, permanently).
    val isClassTeacher: Boolean = false,
    // Phase 10f: tardy time dialog
    val showTardyDialog: Boolean = false,
    val tardyStudentId: String = "",
    val tardyDay: Int = 0,
    // Captured arrival-time minutes per "studentId|day"; consumed by saveAttendance
    // when building LateMark entries. Stale keys across class/month switches are
    // harmless — only the current state.students roster is iterated on save.
    val tardyMinutesByStudentDay: Map<String, Int> = emptyMap(),
    // Phase 1+2 stage gate (server-driven)
    val stage: Stage = Stage.UNKNOWN,
    val editReason: String = "",                       // required for S2 saves
    val lockedBy: String? = null,
    val lockReason: String? = null,
    val pendingCorrectionIds: Set<String> = emptySet(),
    // Correction-request dialog state (opened from S3 / locked rows)
    val showCorrectionDialog: Boolean = false,
    val correctionStudentId: String = "",
    val correctionStudentName: String = "",
    val correctionDate: String = "",
    val correctionCurrentStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val correctionRequestedStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val correctionReason: String = "",
    val isSubmittingCorrection: Boolean = false
)

sealed class AttendanceEvent {
    data class SaveSuccess(val message: String) : AttendanceEvent()
    data class SaveError(val message: String) : AttendanceEvent()
    /** Server returned 423 — switch UI to correction-request mode. */
    object Locked : AttendanceEvent()
    /** Server returned 400 because S2 needed a reason. */
    object ReasonRequired : AttendanceEvent()
    data class CorrectionSubmitted(val message: String) : AttendanceEvent()
}

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,    // reads only
    private val attendanceApiRepo: AttendanceApiRepository,                 // ALL writes
    private val tokenManager: TokenManager                                 // observes active-session changes
) : ViewModel() {

    companion object {
        private const val TAG = "AttendanceVM"

        // Default school start time used to convert arrival HH:mm into
        // lateMinutes. 08:00 is the prevailing Indian-school norm and is
        // safe as a fallback. TODO(rollout+1): read from school config
        // once a `schoolStartTime` field exists on the school doc.
        private const val SCHOOL_START_MINUTES = 8 * 60
        private const val LATE_MINUTES_CAP = 180
    }

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AttendanceEvent>()
    val events = _events.asSharedFlow()

    // Internal cache of students for the current class
    private var currentStudentInfos: List<StudentInfo> = emptyList()

    // Cached assignments for role-based permission checks
    private var cachedAssignments: List<ClassAssignment> = emptyList()

    init {
        // React to academic-session changes. When the admin switches the
        // school's active session, SchoolFirestoreRepository.observeSchool()
        // propagates the new value into TokenManager; we reload the teacher's
        // assigned classes for that session so the class dropdown + roster
        // never show stale data from the previous session. The first (current)
        // emission performs the initial load.
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
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        debugLog("[$TAG][D] Loaded ${assignments.size} assignments")
                        cachedAssignments = assignments
                        val classSections = assignments
                            .map { ClassSection(it.className, it.section) }
                            .distinct()
                        debugLog("[$TAG][D] Distinct classes: ${classSections.map { it.displayName }}")
                        val firstClass = classSections.firstOrNull()
                        val isClassTeacherForFirst = firstClass?.let {
                            RoleHelper.isClassTeacher(assignments, it.className, it.section)
                        } ?: false
                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                selectedClass = firstClass,
                                isClassTeacher = isClassTeacherForFirst,
                                // Clear the stale roster when the new session has no
                                // classes for this teacher (e.g. a future session with
                                // no assignments) so the prior session's students don't
                                // linger on screen after a session switch.
                                students = if (classSections.isEmpty()) emptyList() else it.students,
                                hasUnsavedChanges = if (classSections.isEmpty()) false else it.hasUnsavedChanges
                            )
                        }
                        if (classSections.isNotEmpty()) {
                            loadAttendance()
                            // Pull the server stage for the auto-selected class so a
                            // locked run opens read-only instead of looking editable
                            // and only rejecting at save (423). Previously refreshStage
                            // ran only from selectClass, never on this initial path.
                            refreshStage()
                        } else {
                            currentStudentInfos = emptyList()
                        }
                    },
                    onFailure = { e ->
                        debugLog("[$TAG][E] Failed to load assignments: ${e.message}")
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            } catch (e: Exception) {
                debugLog("[$TAG][E] Failed to load classes: ${e.message}")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectClass(classSection: ClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        guardUnsaved { applySelectClass(classSection) }
    }

    private fun applySelectClass(classSection: ClassSection) {
        val isClassTeacherForClass = RoleHelper.isClassTeacher(
            cachedAssignments, classSection.className, classSection.section
        )
        _uiState.update {
            it.copy(
                selectedClass = classSection,
                hasUnsavedChanges = false,
                isClassTeacher = isClassTeacherForClass,
                stage = Stage.UNKNOWN,        // reset until refreshStage returns
                editReason = ""
            )
        }
        loadAttendance()
        refreshStage()
    }

    fun selectMonth(month: Int, year: Int) {
        guardUnsaved { applySelectMonth(month, year) }
    }

    private fun applySelectMonth(month: Int, year: Int) {
        _uiState.update { it.copy(selectedMonth = month, selectedYear = year, hasUnsavedChanges = false) }
        loadAttendance()
    }

    // ── Unsaved-changes guard (Phase 9b) ──────────────────────────
    // Switching class/month or refreshing while marks are unsaved used to
    // silently reload and discard them. These entry points now route through
    // guardUnsaved: if there are pending marks, the UI shows a confirm dialog
    // (showDiscardDialog) and the navigation runs only after confirmDiscardChanges().
    private var pendingNav: (() -> Unit)? = null

    private fun guardUnsaved(action: () -> Unit) {
        if (_uiState.value.hasUnsavedChanges) {
            pendingNav = action
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            action()
        }
    }

    fun confirmDiscardChanges() {
        val action = pendingNav
        pendingNav = null
        _uiState.update { it.copy(showDiscardDialog = false, hasUnsavedChanges = false) }
        action?.invoke()
    }

    fun dismissDiscardChanges() {
        pendingNav = null
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    /** True if the user has unsaved mark changes. UI should check
     *  before navigating away or switching months. */
    fun hasUnsavedChanges(): Boolean = _uiState.value.hasUnsavedChanges

    /**
     * True when the selected month/year is the actual current month. The UI uses
     * this to disable the bulk "All Present/Absent" actions and to route past-day
     * cell taps to the correction flow instead of the (save-only) cycle editor.
     */
    fun isViewingCurrentMonth(): Boolean {
        val now = Calendar.getInstance()
        val s = _uiState.value
        return s.selectedMonth == now.get(Calendar.MONTH) && s.selectedYear == now.get(Calendar.YEAR)
    }

    fun previousMonth() {
        val current = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, current.selectedYear)
            set(Calendar.MONTH, current.selectedMonth)
            add(Calendar.MONTH, -1)
        }
        selectMonth(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR))
    }

    fun nextMonth() {
        val current = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, current.selectedYear)
            set(Calendar.MONTH, current.selectedMonth)
            add(Calendar.MONTH, 1)
        }
        // Phase 9a: block navigating past the current month
        val now = Calendar.getInstance()
        if (cal.get(Calendar.YEAR) > now.get(Calendar.YEAR) ||
            (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.MONTH) > now.get(Calendar.MONTH))
        ) return
        selectMonth(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR))
    }

    private fun loadAttendance() {
        val state = _uiState.value
        val classSection = state.selectedClass ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, state.selectedYear)
                    set(Calendar.MONTH, state.selectedMonth)
                }
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                debugLog("[$TAG][D] Loading attendance for ${classSection.displayName}, month=$monthName")

                // 1. Get student list for this class/section
                val studentsResult = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                val students = studentsResult.getOrNull() ?: emptyList()
                currentStudentInfos = students
                debugLog("[$TAG][D] Found ${students.size} students: ${students.map { "${it.studentId}=${it.displayName}" }}")

                // 2. Firestore: For each student, read their attendance summary for this month
                val rows = students.map { student ->
                    val dayMap = mutableMapOf<Int, AttendanceStatus>()

                    // Primary: Firestore attendance summary
                    val firestoreResult = attendanceFirestoreRepo.getStudentAttendanceSummary(
                        studentId = student.studentId,
                        month = monthName
                    )
                    val summaryDoc = firestoreResult.getOrNull()

                    if (summaryDoc != null && summaryDoc.dayWise.isNotEmpty()) {
                        // Parse dayWise string (e.g. "PPAPLHV...") into day statuses.
                        // Unknown/placeholder chars decode to null and are left
                        // UNMARKED (not silently Present — see fromCodeOrNull).
                        summaryDoc.dayWise.forEachIndexed { index, char ->
                            AttendanceStatus.fromCodeOrNull(char.toString())?.let { status ->
                                dayMap[index + 1] = status
                            }
                        }
                        debugLog("[$TAG][D] ${student.studentId} Firestore dayWise='${summaryDoc.dayWise}' days=${dayMap.size}")
                    } else {
                        debugLog("[$TAG][D] ${student.studentId} no Firestore attendance data for $monthName")
                    }

                    StudentAttendanceRow(
                        studentId = student.studentId,
                        rollNo = student.rollNo.toIntOrNull() ?: 0,
                        name = student.displayName,
                        dayStatuses = dayMap
                    )
                }.sortedBy { it.rollNo }

                debugLog("[$TAG][D] Attendance loaded: ${rows.size} rows")
                _uiState.update {
                    it.copy(
                        students = rows,
                        daysInMonth = daysInMonth,
                        isLoading = false,
                        hasUnsavedChanges = false,
                        // Recompute today's day on every (re)load from ONE source so
                        // the mark-day the UI writes against always matches the
                        // save-day. A VM kept alive across midnight otherwise held a
                        // stale todayDay while saveAttendance bucketed against a fresh
                        // `now` — reading the (empty) new day and saving the whole
                        // class Present.
                        todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                    )
                }
            } catch (e: Exception) {
                debugLog("[$TAG][E] Failed to load attendance: ${e.message}")
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load attendance")
                }
            }
        }
    }

    /**
     * Cycle a student's status for a given day: P -> A -> L -> H -> T -> V -> P
     * Phase 9a: blocks future dates — can only mark today or past.
     */
    fun cycleStatus(studentId: String, day: Int) {
        // Block future dates
        val state = _uiState.value
        val now = Calendar.getInstance()
        val isCurrentMonth = state.selectedMonth == now.get(Calendar.MONTH)
                && state.selectedYear == now.get(Calendar.YEAR)
        val isFutureMonth = state.selectedYear > now.get(Calendar.YEAR)
                || (state.selectedYear == now.get(Calendar.YEAR) && state.selectedMonth > now.get(Calendar.MONTH))
        if (isFutureMonth || (isCurrentMonth && day > now.get(Calendar.DAY_OF_MONTH))) {
            return // silently ignore future dates
        }

        _uiState.update { st ->
            val updatedStudents = st.students.map { row ->
                if (row.studentId == studentId) {
                    // First tap on an UNMARKED student lands on Present (the common
                    // case). Only an already-marked cell advances through the cycle.
                    // Previously an unmarked cell was treated as Present and then
                    // cycled straight to Absent on first tap, skipping Present.
                    val existing = row.dayStatuses[day]
                    val newStatus = existing?.next() ?: AttendanceStatus.PRESENT
                    row.copy(
                        dayStatuses = row.dayStatuses.toMutableMap().also { it[day] = newStatus }
                    )
                } else {
                    row
                }
            }
            val newState = st.copy(students = updatedStudents, hasUnsavedChanges = true)
            // Phase 10f: show tardy time dialog when mark lands on T
            val landedOnT = updatedStudents.find { it.studentId == studentId }?.dayStatuses?.get(day) == AttendanceStatus.TARDY
            if (landedOnT) {
                newState.copy(showTardyDialog = true, tardyStudentId = studentId, tardyDay = day)
            } else {
                newState
            }
        }
    }

    fun dismissTardyDialog() {
        _uiState.update { it.copy(showTardyDialog = false, tardyStudentId = "", tardyDay = 0) }
    }

    /**
     * Confirm the user-entered arrival time for the currently-pending tardy
     * mark. Parses HH:mm, computes minutes-late vs SCHOOL_START_MINUTES,
     * and stores per "studentId|day" so saveAttendance can emit an accurate
     * LateMark.lateMinutes through the existing /save contract.
     *
     * Invalid input (non-HH:mm, out-of-range) silently records 0 minutes —
     * matches the pre-existing default and avoids an extra error surface
     * in the dialog.
     */
    fun confirmTardyTime(time: String) {
        val state = _uiState.value
        val studentId = state.tardyStudentId
        val day = state.tardyDay
        if (studentId.isEmpty() || day <= 0) {
            dismissTardyDialog()
            return
        }

        val arrivalMinutes = parseHhMmToMinutes(time)
        val lateMinutes = if (arrivalMinutes < 0) 0
            else (arrivalMinutes - SCHOOL_START_MINUTES).coerceIn(0, LATE_MINUTES_CAP)

        val key = "$studentId|$day"
        _uiState.update {
            it.copy(
                tardyMinutesByStudentDay = it.tardyMinutesByStudentDay + (key to lateMinutes),
                showTardyDialog = false,
                tardyStudentId = "",
                tardyDay = 0
            )
        }
    }

    private fun parseHhMmToMinutes(s: String): Int {
        val parts = s.trim().split(":")
        if (parts.size != 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        if (h !in 0..23 || m !in 0..59) return -1
        return h * 60 + m
    }

    /**
     * Mark all students as Present for today.
     */
    fun markAllPresentToday() {
        // Guard: bulk actions only make sense for TODAY, which only exists in the
        // current month. Viewing a past/History month, this used to write today's
        // day-number INTO that month (e.g. mark "day 7 of March" for everyone).
        if (!isViewingCurrentMonth()) return
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        _uiState.update { state ->
            val updatedStudents = state.students.map { row ->
                row.copy(
                    dayStatuses = row.dayStatuses.toMutableMap().also {
                        it[today] = AttendanceStatus.PRESENT
                    }
                )
            }
            state.copy(students = updatedStudents, hasUnsavedChanges = true)
        }
    }

    /**
     * Mark all students as Absent for today.
     */
    fun markAllAbsentToday() {
        if (!isViewingCurrentMonth()) return
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        _uiState.update { state ->
            val updatedStudents = state.students.map { row ->
                row.copy(
                    dayStatuses = row.dayStatuses.toMutableMap().also {
                        it[today] = AttendanceStatus.ABSENT
                    }
                )
            }
            state.copy(students = updatedStudents, hasUnsavedChanges = true)
        }
    }

    /**
     * Save TODAY's attendance via the PHP API (Phase 1).
     *
     * The server enforces stage gate (S1/S2/S3), holiday block, admission
     * date check, audit logging — none of which the legacy direct-Firestore
     * write path checked. Past-day edits go through the correction flow
     * (see submitCorrection / openCorrectionDialog).
     */
    fun saveAttendance() {
        val state = _uiState.value
        val classSection = state.selectedClass ?: return

        // Hard guard: locked → caller should be using Request Correction
        if (state.stage == Stage.S3_LOCKED) {
            viewModelScope.launch { _events.emit(AttendanceEvent.Locked) }
            return
        }

        // Reason required for S2 — fail fast before HTTP round-trip
        if (state.stage == Stage.S2_RESTRICTED && state.editReason.trim().length < 10) {
            viewModelScope.launch { _events.emit(AttendanceEvent.ReasonRequired) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // /save is today-only. If the user is viewing a non-current month,
            // there's nothing for /save to act on; surface a clear message.
            val now = Calendar.getInstance()
            val isCurrentMonth = state.selectedMonth == now.get(Calendar.MONTH)
                    && state.selectedYear == now.get(Calendar.YEAR)
            if (!isCurrentMonth) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(AttendanceEvent.SaveError("Past-month edits require a correction request. Tap any locked cell to file one."))
                return@launch
            }

            val todayDay = now.get(Calendar.DAY_OF_MONTH)

            // Midnight-rollover safety: if the day advanced between when the marks
            // were made (state.todayDay) and now, the buckets below would read the
            // fresh (empty) day and silently save the whole class Present. Bail out
            // and reload today's sheet instead of persisting stale data.
            if (todayDay != state.todayDay) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(AttendanceEvent.SaveError("The date changed since you started marking. Reloading today's sheet — please re-check and save again."))
                loadAttendance()
                return@launch
            }

            // Bucket today's per-student marks into absent / leave / late lists.
            // Server treats every active-roster student NOT in any list as Present.
            val absent = mutableListOf<String>()
            val leave  = mutableListOf<String>()
            val late   = mutableListOf<LateMark>()
            for (student in state.students) {
                val st = student.dayStatuses[todayDay] ?: continue
                when (st) {
                    AttendanceStatus.ABSENT  -> absent.add(student.studentId)
                    AttendanceStatus.LEAVE   -> leave.add(student.studentId)
                    AttendanceStatus.TARDY   -> {
                        val key = "${student.studentId}|$todayDay"
                        val minutes = state.tardyMinutesByStudentDay[key] ?: 0
                        late.add(LateMark(student.studentId, lateMinutes = minutes))
                    }
                    AttendanceStatus.PRESENT -> {} // server default
                    AttendanceStatus.HOLIDAY,
                    AttendanceStatus.VACATION -> {} // not user-markable; ignore
                }
            }

            attendanceApiRepo.save(
                className = classSection.className,
                section   = classSection.section,
                absent    = absent,
                leave     = leave,
                late      = late,
                reason    = state.editReason
            ).fold(
                onSuccess = { res ->
                    debugLog("[$TAG][D] save OK — updated=${res.updated.size} rejected=${res.rejected.size} stage=${res.stage}")
                    _uiState.update { it.copy(
                        isSaving = false,
                        hasUnsavedChanges = false,
                        editReason = "",
                        stage = Stage.fromServer(res.stage)
                    ) }
                    val msg = if (res.rejected.isEmpty()) {
                        "Saved ${res.updated.size} mark(s)."
                    } else {
                        "Saved ${res.updated.size} · ${res.rejected.size} rejected (see notes)."
                    }
                    _events.emit(AttendanceEvent.SaveSuccess(msg))
                },
                onFailure = { e ->
                    debugLog("[$TAG][E] save failed: ${e.message}")
                    _uiState.update { it.copy(isSaving = false) }
                    when (e) {
                        is AttendanceApiError.Locked -> {
                            _uiState.update { it.copy(stage = Stage.S3_LOCKED) }
                            _events.emit(AttendanceEvent.Locked)
                        }
                        is AttendanceApiError.ReasonRequired -> {
                            _uiState.update { it.copy(stage = Stage.S2_RESTRICTED) }
                            _events.emit(AttendanceEvent.ReasonRequired)
                        }
                        is AttendanceApiError.Forbidden -> {
                            _events.emit(AttendanceEvent.SaveError(e.message ?: "Not authorized for this class."))
                        }
                        is AttendanceApiError.Holiday -> {
                            _events.emit(AttendanceEvent.SaveError(e.message ?: "Today is a holiday — attendance cannot be marked."))
                        }
                        is AttendanceApiError.Conflict -> {
                            _events.emit(AttendanceEvent.SaveError(e.message ?: "Conflict — refresh and retry."))
                        }
                        is AttendanceApiError.NotFound -> {
                            _events.emit(AttendanceEvent.SaveError(e.message ?: "Not found."))
                        }
                        else -> _events.emit(AttendanceEvent.SaveError(e.message ?: "Save failed"))
                    }
                }
            )
        }
    }

    // ── Stage / lock state ────────────────────────────────────────

    /** Pull current stage from server for the selected class (today's date). */
    fun refreshStage() {
        val state = _uiState.value
        val cs = state.selectedClass ?: return
        viewModelScope.launch {
            attendanceApiRepo.fetchLock(
                className = cs.className,
                section   = cs.section
            ).fold(
                onSuccess = { ls ->
                    _uiState.update { it.copy(
                        stage      = Stage.fromServer(ls.stage),
                        lockedBy   = ls.lockedBy,
                        lockReason = null   // ls doesn't expose lockReason today; leave null
                    ) }
                },
                onFailure = { e ->
                    debugLog("[$TAG][W] refreshStage failed: ${e.message}")
                    // Keep last-good state; UI degrades to UNKNOWN if never loaded
                }
            )
        }
    }

    fun setEditReason(reason: String) {
        _uiState.update { it.copy(editReason = reason) }
    }

    // ── Correction request flow ───────────────────────────────────

    /**
     * Open the correction-request dialog for a single student/day.
     * Used both when the run is locked and when a teacher wants to amend
     * a past-day mark.
     */
    fun openCorrectionDialog(studentId: String, day: Int) {
        val state = _uiState.value
        val student = state.students.find { it.studentId == studentId } ?: return
        val curStatus = student.dayStatuses[day] ?: AttendanceStatus.PRESENT

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.selectedYear)
            set(Calendar.MONTH, state.selectedMonth)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        _uiState.update { it.copy(
            showCorrectionDialog       = true,
            correctionStudentId        = studentId,
            correctionStudentName      = student.name,
            correctionDate             = isoDate,
            correctionCurrentStatus    = curStatus,
            correctionRequestedStatus  = if (curStatus == AttendanceStatus.ABSENT) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
            correctionReason           = ""
        ) }
    }

    fun closeCorrectionDialog() {
        _uiState.update { it.copy(
            showCorrectionDialog = false,
            correctionStudentId  = "",
            correctionDate       = "",
            correctionReason     = ""
        ) }
    }

    fun setCorrectionRequestedStatus(status: AttendanceStatus) {
        _uiState.update { it.copy(correctionRequestedStatus = status) }
    }

    fun setCorrectionReason(reason: String) {
        _uiState.update { it.copy(correctionReason = reason) }
    }

    fun submitCorrectionRequest() {
        val state = _uiState.value
        if (state.correctionStudentId.isEmpty() || state.correctionDate.isEmpty()) return
        if (state.correctionReason.trim().length < 10) {
            viewModelScope.launch { _events.emit(AttendanceEvent.ReasonRequired) }
            return
        }
        val req = state.correctionRequestedStatus
        val (statusChar, isLate) = when (req) {
            AttendanceStatus.PRESENT -> "P" to false
            AttendanceStatus.ABSENT  -> "A" to false
            AttendanceStatus.LEAVE   -> "L" to false
            AttendanceStatus.TARDY   -> "P" to true   // late = P + late=true (Phase 1 model)
            else                     -> "P" to false
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingCorrection = true) }
            attendanceApiRepo.submitCorrection(
                studentId             = state.correctionStudentId,
                date                  = state.correctionDate,
                requestedStatus       = statusChar,
                requestedLate         = isLate,
                requestedLateMinutes  = 0,
                reason                = state.correctionReason.trim()
            ).fold(
                onSuccess = { reqId ->
                    _uiState.update { it.copy(
                        isSubmittingCorrection = false,
                        showCorrectionDialog   = false,
                        pendingCorrectionIds   = it.pendingCorrectionIds + state.correctionStudentId,
                        correctionStudentId    = "",
                        correctionDate         = "",
                        correctionReason       = ""
                    ) }
                    _events.emit(AttendanceEvent.CorrectionSubmitted("Correction submitted (#$reqId)"))
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isSubmittingCorrection = false) }
                    val msg = when (e) {
                        is AttendanceApiError.Conflict -> e.message ?: "A pending correction already exists."
                        is AttendanceApiError.Forbidden -> e.message ?: "Not authorized for this class."
                        is AttendanceApiError.ReasonRequired -> "Reason must be 10+ characters."
                        else -> e.message ?: "Failed to submit"
                    }
                    _events.emit(AttendanceEvent.SaveError(msg))
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        guardUnsaved { loadAttendance() }
    }
}
