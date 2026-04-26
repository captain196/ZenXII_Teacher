package com.schoolsync.teacher.ui.attendance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.AttendanceData
import com.schoolsync.teacher.data.model.AttendanceStatus as ModelAttendanceStatus
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.teacher.util.Constants
import com.schoolsync.teacher.util.RoleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun next(): AttendanceStatus = when (this) {
        PRESENT -> ABSENT
        ABSENT -> LEAVE
        LEAVE -> HOLIDAY
        HOLIDAY -> TARDY
        TARDY -> VACATION
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
    val todayDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
    val isClassTeacher: Boolean = true,
    // Phase 10f: tardy time dialog
    val showTardyDialog: Boolean = false,
    val tardyStudentId: String = "",
    val tardyDay: Int = 0
)

sealed class AttendanceEvent {
    data class SaveSuccess(val message: String) : AttendanceEvent()
    data class SaveError(val message: String) : AttendanceEvent()
}

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AttendanceVM"
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
        loadAssignedClasses()
    }

    private fun loadAssignedClasses() {
        viewModelScope.launch {
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        Log.d(TAG, "Loaded ${assignments.size} assignments")
                        cachedAssignments = assignments
                        val classSections = assignments
                            .map { ClassSection(it.className, it.section) }
                            .distinct()
                        Log.d(TAG, "Distinct classes: ${classSections.map { it.displayName }}")
                        val firstClass = classSections.firstOrNull()
                        val isClassTeacherForFirst = firstClass?.let {
                            RoleHelper.isClassTeacher(assignments, it.className, it.section)
                        } ?: true
                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                selectedClass = firstClass,
                                isClassTeacher = isClassTeacherForFirst
                            )
                        }
                        if (classSections.isNotEmpty()) {
                            loadAttendance()
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load assignments: ${e.message}", e)
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load classes", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectClass(classSection: ClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        val isClassTeacherForClass = RoleHelper.isClassTeacher(
            cachedAssignments, classSection.className, classSection.section
        )
        _uiState.update {
            it.copy(
                selectedClass = classSection,
                hasUnsavedChanges = false,
                isClassTeacher = isClassTeacherForClass
            )
        }
        loadAttendance()
    }

    fun selectMonth(month: Int, year: Int) {
        // Phase 9b: don't silently discard unsaved changes — the UI
        // should show a confirmation dialog before calling this.
        // For now, reset the flag and reload.
        _uiState.update { it.copy(selectedMonth = month, selectedYear = year, hasUnsavedChanges = false) }
        loadAttendance()
    }

    /** True if the user has unsaved mark changes. UI should check
     *  before navigating away or switching months. */
    fun hasUnsavedChanges(): Boolean = _uiState.value.hasUnsavedChanges

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
                Log.d(TAG, "Loading attendance for ${classSection.displayName}, month=$monthName")

                // 1. Get student list for this class/section
                val studentsResult = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                val students = studentsResult.getOrNull() ?: emptyList()
                currentStudentInfos = students
                Log.d(TAG, "Found ${students.size} students: ${students.map { "${it.studentId}=${it.displayName}" }}")

                // 2. Firestore: For each student, read their attendance summary for this month
                // TODO: Remove RTDB fallback after Firestore validation
                val rows = students.map { student ->
                    val dayMap = mutableMapOf<Int, AttendanceStatus>()

                    // Primary: Firestore attendance summary
                    val firestoreResult = attendanceFirestoreRepo.getStudentAttendanceSummary(
                        studentId = student.studentId,
                        month = monthName
                    )
                    val summaryDoc = firestoreResult.getOrNull()

                    if (summaryDoc != null && summaryDoc.dayWise.isNotEmpty()) {
                        // Parse dayWise string (e.g. "PPAPLHV...") into day statuses
                        summaryDoc.dayWise.forEachIndexed { index, char ->
                            val status = AttendanceStatus.fromCode(char.toString())
                            dayMap[index + 1] = status
                        }
                        Log.d(TAG, "${student.studentId} Firestore dayWise='${summaryDoc.dayWise}' days=${dayMap.size}")
                    } else {
                        Log.d(TAG, "${student.studentId} no Firestore attendance data for $monthName")
                    }

                    StudentAttendanceRow(
                        studentId = student.studentId,
                        rollNo = student.rollNo.toIntOrNull() ?: 0,
                        name = student.displayName,
                        dayStatuses = dayMap
                    )
                }.sortedBy { it.rollNo }

                Log.d(TAG, "Attendance loaded: ${rows.size} rows")
                _uiState.update {
                    it.copy(
                        students = rows,
                        daysInMonth = daysInMonth,
                        isLoading = false,
                        hasUnsavedChanges = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load attendance", e)
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
                    val currentStatus = row.dayStatuses[day] ?: AttendanceStatus.PRESENT
                    val newStatus = currentStatus.next()
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
     * Mark all students as Present for today.
     */
    fun markAllPresentToday() {
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
     * Save attendance via Firestore (primary) with RTDB fallback.
     * For each student, builds the attendance string for the month and writes it.
     */
    fun saveAttendance() {
        val state = _uiState.value
        val classSection = state.selectedClass ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, state.selectedYear)
                    set(Calendar.MONTH, state.selectedMonth)
                }
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val sectionKey = "${Constants.classKey(classSection.className)}/${Constants.sectionKey(classSection.section)}"

                // Phase 9a: Write per-day docs for TODAY (the primary mark
                // day). Future: could also write for changed past days,
                // but for now the summary dayWise carries the full month.
                val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                val now = Calendar.getInstance()
                val isCurrentMonth = state.selectedMonth == now.get(Calendar.MONTH)
                        && state.selectedYear == now.get(Calendar.YEAR)

                if (isCurrentMonth) {
                    val todayCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, state.selectedYear)
                        set(Calendar.MONTH, state.selectedMonth)
                        set(Calendar.DAY_OF_MONTH, todayDay)
                    }
                    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(todayCal.time)

                    val todayStatuses = mutableMapOf<String, Pair<String, String>>()
                    for (student in state.students) {
                        val uiStatus = student.dayStatuses[todayDay] ?: continue
                        val studentInfo = currentStudentInfos.find { it.studentId == student.studentId }
                        todayStatuses[student.studentId] = Pair(uiStatus.code, studentInfo?.displayName ?: student.name)
                    }

                    if (todayStatuses.isNotEmpty()) {
                        attendanceFirestoreRepo.markAttendance(
                            sectionKey = sectionKey,
                            date = todayIso,
                            studentStatuses = todayStatuses
                        ).fold(
                            onSuccess = { count ->
                                Log.d(TAG, "Firestore: wrote $count daily attendance records")
                            },
                            onFailure = { e ->
                                Log.e(TAG, "Firestore daily attendance write failed: ${e.message}")
                            }
                        )
                    }
                }

                // Firestore: Update attendance summaries with full month dayWise string
                for (student in state.students) {
                    val dayWise = buildString {
                        for (day in 1..daysInMonth) {
                            val status = student.dayStatuses[day]
                            append(status?.code ?: "V")  // Phase 9a: "V" not "-" to match PHP canonical codes
                        }
                    }
                    val studentInfo = currentStudentInfos.find { it.studentId == student.studentId }
                    attendanceFirestoreRepo.updateAttendanceSummary(
                        studentId = student.studentId,
                        studentName = studentInfo?.displayName ?: student.name,
                        sectionKey = sectionKey,
                        month = monthName,
                        dayWise = dayWise
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "Firestore: summary updated for ${student.studentId}")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Firestore summary update failed for ${student.studentId}: ${e.message}")
                        }
                    )
                }

                _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                _events.emit(AttendanceEvent.SaveSuccess("Attendance saved successfully"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save attendance", e)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(AttendanceEvent.SaveError(e.message ?: "Failed to save"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadAttendance()
    }
}
