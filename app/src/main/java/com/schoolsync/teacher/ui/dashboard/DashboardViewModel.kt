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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /**
     * True when the timetable fetch itself FAILED (network / permission /
     * missing index), as opposed to a genuinely empty schedule. Lets the UI
     * show a distinct "couldn't load — retry" state instead of the calm
     * "no classes today" empty state, which previously looked identical.
     */
    val scheduleError: Boolean = false,
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
        // Reload when the school's active session changes (propagated into
        // TokenManager by SchoolFirestoreRepository.observeSchool). First
        // emission performs the initial load. Mirrors AttendanceViewModel.
        viewModelScope.launch {
            tokenManager.session
                .distinctUntilChanged()
                .collect { session ->
                    if (!session.isNullOrBlank()) loadDashboard()
                }
        }

        // LIVE: silently re-load when the admin changes this teacher's subject
        // assignments, so "My Classes" / class-teacher info update without a
        // manual refresh. Skip the first emission — the session collector above
        // already performs the initial load.
        viewModelScope.launch {
            var first = true
            teacherRepository.observeAssignedClasses()
                .distinctUntilChanged()
                .catch { android.util.Log.e("DashboardVM", "observeAssignedClasses failed", it) }
                .collect {
                    if (first) first = false
                    else loadDashboard(showLoading = false)
                }
        }
    }

    fun loadDashboard(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Teacher name + assignments are the gating prereqs — every
                // other fetch needs the assigned class+section list. Run
                // these first; the rest fans out in parallel below.
                val name = tokenManager.userName.firstOrNull() ?: ""
                _uiState.update { it.copy(teacherName = name) }

                val assignedResult = teacherRepository.getAssignedClasses()
                assignedResult.exceptionOrNull()?.let {
                    android.util.Log.e("DashboardVM", "getAssignedClasses failed", it)
                }
                val assignedClasses = assignedResult.getOrNull() ?: emptyList()
                val classLabels = assignedClasses.map { it.classKey }.distinct()
                val classTeacherOf = assignedClasses
                    .filter { it.classTeacher }
                    .map { "${it.className} — ${it.section}" }
                    .distinct()
                _uiState.update {
                    it.copy(assignedClasses = classLabels, classTeacherOf = classTeacherOf)
                }

                val classSectionsDistinct = assignedClasses
                    .map { it.className to it.section }
                    .distinct()
                val classTeacherSections = assignedClasses
                    .filter { it.classTeacher }
                    .map { it.className to it.section }
                    .distinct()

                // Fan everything else out in parallel. Each block returns
                // its own slice of the final state; we update the UI once
                // at the end. coroutineScope ensures all children either
                // succeed or surface a single failure to the outer try.
                val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val todayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

                coroutineScope {
                    // 1. Today's attendance — class-wide query, NOT per-student.
                    //    One Firestore RPC per class-teacher section returns
                    //    every student's monthly summary. We then read the
                    //    today-th char of dayWise locally.
                    val attendanceJob = async {
                        var total = 0; var p = 0; var a = 0; var t = 0; var l = 0; var unmarked = 0
                        for ((cls, sec) in classTeacherSections) {
                            try {
                                val students = studentRepository.getStudentsForClass(cls, sec)
                                    .getOrNull() ?: emptyList()
                                val sectionKey = "$cls/$sec"
                                val summaries = attendanceFirestoreRepo
                                    .getClassMonthlySummaries(sectionKey, monthKey)
                                    .getOrNull().orEmpty()
                                val byStudent = summaries.associateBy { it.studentId }

                                for (student in students) {
                                    total++
                                    val dw = byStudent[student.studentId]?.dayWise.orEmpty()
                                    if (dw.length >= today) {
                                        when (dw[today - 1]) {
                                            'P' -> p++
                                            'A' -> a++
                                            'T' -> t++
                                            'L' -> l++
                                            else -> unmarked++
                                        }
                                    } else {
                                        unmarked++
                                    }
                                }
                            } catch (_: Exception) { /* non-critical */ }
                        }
                        TodayAttendanceSummary(
                            totalStudents = total, present = p, absent = a,
                            tardy = t, leave = l, unmarked = unmarked
                        )
                    }

                    // 2. Timetable — independent. Returns (periods, failed):
                    //    `failed=true` means the fetch errored (network /
                    //    permission / missing index), which the UI must
                    //    distinguish from a genuinely empty schedule.
                    val timetableJob = async {
                        timetableFirestoreRepo.getMyTimetable(classSectionsDistinct).fold(
                            onSuccess = { dayTimetables ->
                                val todayName = todayDayName()
                                val todayPeriods = dayTimetables
                                    .filter { it.day.equals(todayName, ignoreCase = true) }
                                    .flatMap { it.periods }
                                    .sortedBy { it.periodNumber }
                                val currentPeriod = calculateCurrentPeriod(todayPeriods)
                                todayPeriods.map { entry ->
                                    PeriodItem(
                                        periodNumber = entry.periodNumber,
                                        time = entry.timeSlot,
                                        subject = entry.subject,
                                        className = entry.className,
                                        section = entry.section,
                                        isCurrent = entry.periodNumber == currentPeriod
                                    )
                                } to false
                            },
                            onFailure = { e ->
                                android.util.Log.e("DashboardVM", "getMyTimetable failed", e)
                                emptyList<PeriodItem>() to true
                            }
                        )
                    }

                    // 3. Homework due today — one class-wide query per section.
                    val homeworkDueJobs = classSectionsDistinct.map { (cls, sec) ->
                        async {
                            try {
                                homeworkRepository.getHomeworkDueTodayCount(cls, sec, todayDate)
                            } catch (_: Exception) { 0 }
                        }
                    }

                    // 4. Active flags — one class-wide query per section.
                    //    Reuses students fetched in (5), keyed below.
                    //    We launch the students fetch first so flag job can
                    //    await its result without a second RPC.
                    val studentsBySection = classSectionsDistinct.associateWith { (cls, sec) ->
                        async {
                            try {
                                studentRepository.getStudentsForClass(cls, sec)
                                    .getOrNull() ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                        }
                    }
                    val flagJobs = classSectionsDistinct.map { key ->
                        async {
                            try {
                                val students = studentsBySection.getValue(key).await()
                                redFlagRepository.getTotalActiveFlagCount(students)
                            } catch (_: Exception) { 0 }
                        }
                    }

                    // 5. Substitute info — independent.
                    val subInfoJob = async { loadSubstituteInfo(todayIso) }

                    // Await everything in parallel — total wall time is the
                    // slowest single fetch, not the sum.
                    val attendance = attendanceJob.await()
                    val (schedule, scheduleFailed) = timetableJob.await()
                    val homeworkDueToday = homeworkDueJobs.awaitAll().sum()
                    val activeFlagCount = flagJobs.awaitAll().sum()
                    val subInfo = subInfoJob.await()

                    val uniqueClasses = classLabels.size
                    val stats = listOf(
                        QuickStat("Classes", uniqueClasses.toString(), "assigned"),
                        QuickStat("Today", schedule.size.toString(), "periods"),
                        QuickStat("HW Due", homeworkDueToday.toString(), "today"),
                        QuickStat("Flags", activeFlagCount.toString(), "active")
                    )

                    _uiState.update {
                        it.copy(
                            todayAttendance = attendance,
                            todaySchedule = schedule,
                            scheduleError = scheduleFailed,
                            quickStats = stats,
                            substituteInfo = subInfo,
                            isLoading = false
                        )
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load dashboard")
                }
            }
        }
    }

    /**
     * Today's substitute coverage — either someone covering my classes
     * (I'm absent) or me covering someone else's. Single Firestore query.
     */
    private suspend fun loadSubstituteInfo(todayIso: String): String? {
        return try {
            val myId = tokenManager.userId.firstOrNull() ?: ""
            val mySchool = tokenManager.schoolCode.firstOrNull()
                ?: tokenManager.schoolId.firstOrNull() ?: ""
            if (myId.isBlank() || mySchool.isBlank()) return null

            val subSnapshot = firestoreService.queryDocuments("substitutes") { ref ->
                ref.whereEqualTo("date", todayIso)
            }
            if (subSnapshot.isEmpty) return null

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
                    // I'm absent — who is covering my classes?
                    if (!assignments.isNullOrEmpty()) {
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
                } else if (!assignments.isNullOrEmpty()) {
                    // Am I a substitute in any assignment?
                    for (a in assignments) {
                        val subTid = a["substitute_teacher_id"]?.toString() ?: ""
                        if (subTid == myId) {
                            val pn = (a["periodNumber"] as? Number)?.toInt() ?: continue
                            val subj = a["subject"]?.toString() ?: ""
                            parts.add("Covering for $absentName — P$pn $subj")
                        }
                    }
                } else {
                    // Legacy flat substitute_teacher_id shape.
                    if ((data["substitute_teacher_id"]?.toString() ?: "") == myId) {
                        @Suppress("UNCHECKED_CAST")
                        val periods = (data["periods"] as? List<*>)?.joinToString(", ") { "P$it" } ?: ""
                        parts.add("Covering for $absentName — $periods")
                    }
                }
            }
            if (parts.isEmpty()) null else parts.joinToString(" | ")
        } catch (_: Exception) { null }
    }

    private fun todayDayName(): String = com.schoolsync.teacher.util.englishDayName()

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
