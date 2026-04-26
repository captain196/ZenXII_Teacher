package com.schoolsync.teacher.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.DayTimetable
import com.schoolsync.teacher.data.model.TimetableEntry
import com.schoolsync.teacher.data.repository.HomeworkTeacherRepository
import com.schoolsync.teacher.data.repository.RedFlagRepository
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.SectionFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.TimetableFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PeriodItem(
    val periodNumber: Int,
    val time: String,
    val subject: String,
    val className: String,
    val section: String,
    val isCurrent: Boolean = false
)

data class QuickStat(
    val label: String,
    val value: String,
    val subtitle: String = ""
)

data class ActivityItem(
    val title: String,
    val description: String,
    val timestamp: String,
    val type: ActivityType = ActivityType.INFO
)

enum class ActivityType { INFO, ATTENDANCE, MARKS, NOTICE }

data class TodayAttendanceSummary(
    val totalStudents: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val tardy: Int = 0,
    val leave: Int = 0,
    val unmarked: Int = 0
) {
    val percentage: Float get() {
        val working = present + absent + tardy + leave
        return if (working > 0) (present + tardy).toFloat() / working * 100f else 0f
    }
}

data class DashboardUiState(
    val teacherName: String = "",
    val todaySchedule: List<PeriodItem> = emptyList(),
    val quickStats: List<QuickStat> = emptyList(),
    val recentActivity: List<ActivityItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentDate: String = "",
    val assignedClasses: List<String> = emptyList(),
    /** Today's attendance summary across all class-teacher sections. */
    val todayAttendance: TodayAttendanceSummary = TodayAttendanceSummary(),
    /**
     * Sections where the logged-in teacher is the designated Class Teacher.
     * Each entry is "Class 8th — Section A". Computed from any
     * [ClassAssignment] row whose `classTeacher` (isClassTeacher) is true.
     * Empty list means the teacher is not the class teacher anywhere.
     */
    val classTeacherOf: List<String> = emptyList(),
    /** Substitute info for today — shows if someone is covering this teacher's classes */
    val substituteInfo: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val tokenManager: TokenManager,
    private val homeworkRepository: HomeworkTeacherRepository,
    private val redFlagRepository: RedFlagRepository,
    private val studentRepository: StudentRepository,
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,
    private val sectionFirestoreRepo: SectionFirestoreRepository,
    private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val timetableFirestoreRepo: TimetableFirestoreRepository,
    private val firestoreService: com.schoolsync.teacher.data.firebase.FirestoreService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Load teacher name from local storage
                val name = tokenManager.userName.firstOrNull() ?: ""
                _uiState.update { it.copy(teacherName = name) }

                // Load assigned classes
                var assignedClasses = emptyList<ClassAssignment>()
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { classes ->
                        assignedClasses = classes
                        val classLabels = classes.map { it.classKey }.distinct()

                        // Class-teacher sections — collected from any
                        // assignment row with isClassTeacher=true. Same
                        // teacher may be class teacher of multiple sections
                        // in theory, so we keep a list and de-dupe.
                        val classTeacherOf = classes
                            .filter { it.classTeacher }
                            .map { "${it.className} — ${it.section}" }
                            .distinct()

                        _uiState.update {
                            it.copy(
                                assignedClasses = classLabels,
                                classTeacherOf = classTeacherOf,
                            )
                        }
                    },
                    onFailure = { /* non-critical */ }
                )

                // Phase 10f: Load today's attendance summary for class-teacher sections
                val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val classTeacherSections = assignedClasses
                    .filter { it.classTeacher }
                    .map { it.className to it.section }
                    .distinct()
                var attTotal = 0; var attP = 0; var attA = 0; var attT = 0; var attL = 0; var attUnmarked = 0
                for ((cls, sec) in classTeacherSections) {
                    try {
                        val students = studentRepository.getStudentsForClass(cls, sec).getOrNull() ?: emptyList()
                        for (student in students) {
                            attTotal++
                            val summary = attendanceFirestoreRepo.getStudentAttendanceSummary(student.studentId, monthKey).getOrNull()
                            val dw = summary?.dayWise ?: ""
                            if (dw.length >= today) {
                                when (dw[today - 1]) {
                                    'P' -> attP++
                                    'A' -> attA++
                                    'T' -> attT++
                                    'L' -> attL++
                                    else -> attUnmarked++
                                }
                            } else {
                                attUnmarked++
                            }
                        }
                    } catch (_: Exception) {}
                }
                _uiState.update { it.copy(todayAttendance = TodayAttendanceSummary(
                    totalStudents = attTotal, present = attP, absent = attA,
                    tardy = attT, leave = attL, unmarked = attUnmarked
                )) }

                // Load today's timetable — Firestore canonical source (Phase C-1).
                // Pre-compute class/section pairs from already-fetched assignments
                // so we don't re-query them inside the repo.
                val timetableClassSections = assignedClasses
                    .map { it.className to it.section }
                    .distinct()
                timetableFirestoreRepo.getMyTimetable(timetableClassSections).fold(
                    onSuccess = { dayTimetables ->
                        val todayName = todayDayName()
                        val todayPeriods = dayTimetables
                            .filter { it.day.equals(todayName, ignoreCase = true) }
                            .flatMap { it.periods }
                            .sortedBy { it.periodNumber }

                        val currentPeriod = calculateCurrentPeriod(todayPeriods)

                        val schedule = todayPeriods.map { entry ->
                            PeriodItem(
                                periodNumber = entry.periodNumber,
                                time = entry.timeSlot,
                                subject = entry.subject,
                                className = entry.className,
                                section = entry.section,
                                isCurrent = entry.periodNumber == currentPeriod
                            )
                        }
                        _uiState.update { it.copy(todaySchedule = schedule) }
                    },
                    onFailure = { /* use empty list */ }
                )

                // Firestore: Load section data (student counts) for assigned classes
                // TODO: Remove RTDB fallback after Firestore validation
                val classSectionsDistinct = assignedClasses
                    .map { it.className to it.section }
                    .distinct()
                var firestoreStudentCount = 0
                for ((className, section) in classSectionsDistinct) {
                    sectionFirestoreRepo.getSection(className, section).fold(
                        onSuccess = { sectionDoc ->
                            firestoreStudentCount += sectionDoc.studentCount
                        },
                        onFailure = { /* non-critical, use RTDB fallback data */ }
                    )
                }

                // Firestore: Load circulars count for recent activity
                // TODO: Remove RTDB fallback after Firestore validation
                var recentCircularCount = 0
                communicationFirestoreRepo.getCirculars(limit = 10).fold(
                    onSuccess = { circulars ->
                        recentCircularCount = circulars.size
                    },
                    onFailure = { /* non-critical */ }
                )

                // Build quick stats — start with classes/subjects/periods
                val uniqueClasses = assignedClasses.map { it.classKey }.distinct().size
                val todayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

                // Load homework due today + active flags across all assigned classes
                var homeworkDueToday = 0
                var activeFlagCount = 0
                try {
                    for ((className, section) in classSectionsDistinct) {
                        homeworkDueToday += homeworkRepository.getHomeworkDueTodayCount(
                            className, section, todayDate
                        )
                        val students = studentRepository.getStudentsForClass(className, section)
                            .getOrNull() ?: emptyList()
                        activeFlagCount += redFlagRepository.getTotalActiveFlagCount(students)
                    }
                } catch (_: Exception) { /* non-critical */ }

                val stats = listOf(
                    QuickStat("Classes", uniqueClasses.toString(), "assigned"),
                    QuickStat("Today", _uiState.value.todaySchedule.size.toString(), "periods"),
                    QuickStat("HW Due", homeworkDueToday.toString(), "today"),
                    QuickStat("Flags", activeFlagCount.toString(), "active")
                )

                // Load substitute info for today
                // Shows either "X is covering your P1" (if I'm absent)
                // or "You are covering for X at P1" (if I'm the substitute)
                var subInfo: String? = null
                try {
                    val myId = tokenManager.userId.firstOrNull() ?: ""
                    val mySchool = tokenManager.schoolCode.firstOrNull()
                        ?: tokenManager.schoolId.firstOrNull() ?: ""
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    if (myId.isNotBlank() && mySchool.isNotBlank()) {
                        val subSnapshot = firestoreService.queryDocuments("substitutes") { ref ->
                            ref.whereEqualTo("date", todayStr)
                        }
                        if (!subSnapshot.isEmpty) {
                            val parts = mutableListOf<String>()
                            for (doc in subSnapshot.documents) {
                                val data = doc.data ?: continue
                                if ((data["schoolId"]?.toString() ?: "") != mySchool) continue
                                if ((data["status"]?.toString() ?: "") == "cancelled") continue

                                val absentId = data["absent_teacher_id"]?.toString() ?: ""
                                val absentName = data["absent_teacher_name"]?.toString() ?: ""

                                @Suppress("UNCHECKED_CAST")
                                val assignments = data["assignments"] as? List<Map<String, Any>>

                                if (absentId == myId) {
                                    // I'm absent — show who is covering my classes
                                    if (assignments != null && assignments.isNotEmpty()) {
                                        for (a in assignments) {
                                            val sName = a["substitute_teacher_name"]?.toString() ?: "Substitute"
                                            val pn = (a["periodNumber"] as? Number)?.toInt() ?: continue
                                            parts.add("$sName covering P$pn")
                                        }
                                    } else {
                                        val sName = data["substitute_teacher_name"]?.toString() ?: "Substitute"
                                        @Suppress("UNCHECKED_CAST")
                                        val periods = (data["periods"] as? List<*>)?.joinToString(", ") { "P$it" } ?: ""
                                        parts.add("$sName covering $periods")
                                    }
                                } else if (assignments != null && assignments.isNotEmpty()) {
                                    // Check if I'm a substitute in any assignment
                                    for (a in assignments) {
                                        val subTid = a["substitute_teacher_id"]?.toString() ?: ""
                                        if (subTid == myId) {
                                            val pn = (a["periodNumber"] as? Number)?.toInt() ?: continue
                                            val subj = a["subject"]?.toString() ?: ""
                                            parts.add("Covering for $absentName — P$pn $subj")
                                        }
                                    }
                                } else {
                                    // Legacy: check flat substitute_teacher_id
                                    if ((data["substitute_teacher_id"]?.toString() ?: "") == myId) {
                                        @Suppress("UNCHECKED_CAST")
                                        val periods = (data["periods"] as? List<*>)?.joinToString(", ") { "P$it" } ?: ""
                                        parts.add("Covering for $absentName — $periods")
                                    }
                                }
                            }
                            if (parts.isNotEmpty()) subInfo = parts.joinToString(" | ")
                        }
                    }
                } catch (_: Exception) { /* non-critical */ }

                _uiState.update {
                    it.copy(quickStats = stats, substituteInfo = subInfo, isLoading = false)
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load dashboard")
                }
            }
        }
    }

    private fun todayDayName(): String {
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
    }

    private fun calculateCurrentPeriod(periods: List<TimetableEntry>): Int {
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        for (period in periods) {
            try {
                val startParts = period.startTime.trim().split(":")
                val endParts = period.endTime.trim().split(":")
                if (startParts.size >= 2 && endParts.size >= 2) {
                    val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
                    val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
                    if (currentTimeMinutes in startMinutes..endMinutes) {
                        return period.periodNumber
                    }
                }
            } catch (_: Exception) { }
        }
        return -1
    }

    fun refresh() {
        loadDashboard()
    }
}
