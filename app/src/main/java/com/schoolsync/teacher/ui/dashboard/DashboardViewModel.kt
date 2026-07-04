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

/** Where a period sits relative to the current wall-clock time. */
enum class PeriodStatus { DONE, CURRENT, UPCOMING }

data class PeriodItem(
    val periodNumber: Int,
    val time: String,
    val subject: String,
    val className: String,
    val section: String,
    val isCurrent: Boolean = false,
    val status: PeriodStatus = PeriodStatus.UPCOMING,
    val isBreak: Boolean = false
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
    val substituteInfo: String? = null,
    /** Upcoming school events (soonest first) for the dashboard events rail. */
    val upcomingEvents: List<com.schoolsync.teacher.data.model.firestore.EventDoc> = emptyList(),
    /** True until the first events fetch completes — drives the events-rail
     *  shimmer. Events load in a SEPARATE un-awaited coroutine, so they arrive
     *  after the main dashboard `isLoading` flips; this flag covers that gap. */
    val eventsLoading: Boolean = true
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
    private val eventsFirestoreRepo: com.schoolsync.teacher.data.repository.firestore.EventsFirestoreRepository,
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
                    if (!session.isNullOrBlank()) {
                        loadDashboard()
                        loadUpcomingEvents()
                    }
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
                // Subject keys so the timetable reader can claim blank-teacherId
                // periods matching my (class, section, subject) assignments —
                // covers admin timetables published without a teacher on a slot.
                val mySubjectKeys = TimetableFirestoreRepository.subjectKeysOf(
                    assignedClasses.map { Triple(it.className, it.section, it.subject) }
                )
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
                        timetableFirestoreRepo.getMyTimetable(classSectionsDistinct, mySubjectKeys).fold(
                            onSuccess = { dayTimetables ->
                                val todayName = todayDayName()
                                val todayPeriods = dayTimetables
                                    .filter { it.day.equals(todayName, ignoreCase = true) }
                                    .flatMap { it.periods }
                                    .sortedBy { it.periodNumber }
                                val nowMinutes = Calendar.getInstance().let {
                                    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
                                }
                                todayPeriods.map { entry ->
                                    val st = periodStatusOf(entry, nowMinutes)
                                    PeriodItem(
                                        periodNumber = entry.periodNumber,
                                        time = entry.timeSlot,
                                        subject = entry.subject,
                                        className = entry.className,
                                        section = entry.section,
                                        isCurrent = st == PeriodStatus.CURRENT,
                                        status = st,
                                        isBreak = entry.isBreak
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

    /**
     * Classify a period against the current wall-clock time using its 24h
     * HH:MM start/end. now within [start,end] → CURRENT; past end → DONE;
     * otherwise UPCOMING. Unparseable times fall back to UPCOMING, so a period
     * is never wrongly shown as live.
     */
    private fun periodStatusOf(entry: TimetableEntry, nowMinutes: Int): PeriodStatus {
        val start = parseTimeToMinutes(entry.startTime) ?: return PeriodStatus.UPCOMING
        val end = parseTimeToMinutes(entry.endTime) ?: return PeriodStatus.UPCOMING
        return when {
            nowMinutes in start..end -> PeriodStatus.CURRENT
            nowMinutes > end -> PeriodStatus.DONE
            else -> PeriodStatus.UPCOMING
        }
    }

    /**
     * Parse a clock string to minutes-since-midnight. Handles both 24h
     * ("14:30") and 12h with an AM/PM suffix in any spacing/case
     * ("2:30PM", "2:30 pm", "10:45AM") — the timetable stores times in the
     * 12h form, so a naive HH:MM split silently failed and left every period
     * marked UPCOMING. Returns null when unparseable.
     */
    private fun parseTimeToMinutes(raw: String): Int? {
        val t = raw.trim().uppercase(Locale.US)
        if (t.isEmpty()) return null
        val isAm = t.contains("AM")
        val isPm = t.contains("PM")
        val cleaned = t.replace("AM", "").replace("PM", "").trim()
        val parts = cleaned.split(":")
        if (parts.size < 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (m !in 0..59) return null
        var hour = h
        if (isAm || isPm) {                 // 12-hour clock
            hour %= 12
            if (isPm) hour += 12
        }
        if (hour !in 0..23) return null
        return hour * 60 + m
    }

    fun refresh() {
        loadDashboard()
        loadUpcomingEvents()
    }

    /**
     * Load upcoming school events for the dashboard rail. Independent of the
     * main load so a slow/failed events fetch never blocks the dashboard.
     * startDate is stored as "yyyy-MM-dd", so a lexicographic compare with
     * today correctly keeps today-and-later, soonest first.
     */
    private fun loadUpcomingEvents() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val events = eventsFirestoreRepo.getEvents().getOrNull().orEmpty()
                .filter {
                    it.startDate >= today &&
                        !it.status.equals("cancelled", true) &&
                        !it.status.equals("completed", true)
                }
                .sortedBy { it.startDate }
                .take(8)
            _uiState.value = _uiState.value.copy(upcomingEvents = events, eventsLoading = false)
        }
    }
}
