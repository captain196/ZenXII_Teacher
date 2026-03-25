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
import com.schoolsync.teacher.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.SectionFirestoreRepository
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

data class DashboardUiState(
    val teacherName: String = "",
    val todaySchedule: List<PeriodItem> = emptyList(),
    val quickStats: List<QuickStat> = emptyList(),
    val recentActivity: List<ActivityItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentDate: String = "",
    val assignedClasses: List<String> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val tokenManager: TokenManager,
    private val homeworkRepository: HomeworkTeacherRepository,
    private val redFlagRepository: RedFlagRepository,
    private val studentRepository: StudentRepository,
    // TODO: Remove RTDB fallback after Firestore validation
    private val sectionFirestoreRepo: SectionFirestoreRepository,
    private val communicationFirestoreRepo: CommunicationFirestoreRepository
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
                        _uiState.update { it.copy(assignedClasses = classLabels) }
                    },
                    onFailure = { /* non-critical */ }
                )

                // Load today's timetable
                teacherRepository.getMyTimetable().fold(
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

                _uiState.update {
                    it.copy(quickStats = stats, isLoading = false)
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
