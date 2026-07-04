package com.schoolsync.teacher.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.DayTimetable
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.TimetableFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TimetableEntry(
    val subject: String,
    val className: String,
    val section: String,
    val room: String = "",
    val teacher: String = "",
    val isCurrentPeriod: Boolean = false,
    val startTime: String = "",
    val endTime: String = "",
    val isBreak: Boolean = false,
    val isLunch: Boolean = false,
)

data class TimetableUiState(
    /** Map<DayName, List<TimetableEntry?>> — index = period number (0-based) */
    val weekGrid: Map<String, List<TimetableEntry?>> = emptyMap(),
    val periodTimes: List<String> = emptyList(),
    val days: List<String> = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
    val currentDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
    val currentPeriod: Int = -1,
    val totalPeriods: Int = 8,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val timetableFirestoreRepo: TimetableFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        // Reload when the school's active session changes (propagated into
        // TokenManager by SchoolFirestoreRepository.observeSchool). First
        // emission performs the initial load. Mirrors AttendanceViewModel.
        viewModelScope.launch {
            tokenManager.session
                .distinctUntilChanged()
                .collect { session ->
                    if (!session.isNullOrBlank()) loadTimetable()
                }
        }
    }

    fun loadTimetable() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Get assigned classes from TeacherRepository (still RTDB for assignments)
                val assignments = teacherRepository.getAssignedClasses().getOrNull() ?: emptyList()
                val classSections = assignments
                    .map { it.className to it.section }.distinct()
                // Subject keys let the reader claim blank-teacherId periods that
                // match one of my assigned (class, section, subject) tuples —
                // covers admin timetables published without a teacher on a slot.
                val mySubjectKeys = TimetableFirestoreRepository.subjectKeysOf(
                    assignments.map { Triple(it.className, it.section, it.subject) }
                )

                // Fetch timetable from Firestore
                timetableFirestoreRepo.getMyTimetable(classSections, mySubjectKeys).fold(
                    onSuccess = { dayTimetables ->
                        val maxPeriods = dayTimetables.maxOfOrNull { it.periods.size } ?: 8
                        val totalPeriods = maxPeriods.coerceAtLeast(8)

                        // Build period times from actual data or defaults
                        val periodTimes = buildPeriodTimes(dayTimetables, totalPeriods)

                        // Determine current period
                        val currentPeriod = calculateCurrentPeriod(periodTimes)
                        val currentDayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                        val currentDayName = dayOfWeekName(currentDayIndex)

                        // Build week grid
                        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                        val weekGrid = mutableMapOf<String, List<TimetableEntry?>>()

                        for (day in days) {
                            val dayPeriods = dayTimetables
                                .filter { it.day.equals(day, ignoreCase = true) }
                                .flatMap { it.periods }
                                .associateBy { it.periodNumber }

                            val entries = (1..totalPeriods).map { periodNum ->
                                val firebaseEntry = dayPeriods[periodNum]
                                if (firebaseEntry != null) {
                                    TimetableEntry(
                                        subject = firebaseEntry.subject,
                                        className = firebaseEntry.className,
                                        section = firebaseEntry.section,
                                        room = firebaseEntry.room,
                                        teacher = firebaseEntry.teacher,
                                        isCurrentPeriod = day == currentDayName && periodNum - 1 == currentPeriod,
                                        startTime = firebaseEntry.startTime,
                                        endTime = firebaseEntry.endTime,
                                        isBreak = firebaseEntry.isBreak,
                                        isLunch = firebaseEntry.isLunch,
                                    )
                                } else {
                                    null
                                }
                            }
                            weekGrid[day] = entries
                        }

                        _uiState.update {
                            it.copy(
                                weekGrid = weekGrid,
                                periodTimes = periodTimes,
                                totalPeriods = totalPeriods,
                                currentDay = currentDayIndex,
                                currentPeriod = currentPeriod,
                                isLoading = false
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message)
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load timetable")
                }
            }
        }
    }

    private fun buildPeriodTimes(
        dayTimetables: List<DayTimetable>,
        totalPeriods: Int
    ): List<String> {
        // Try to extract times from actual data
        val timeMap = mutableMapOf<Int, String>()
        for (dt in dayTimetables) {
            for (period in dt.periods) {
                if (period.startTime.isNotBlank() && period.endTime.isNotBlank()) {
                    timeMap[period.periodNumber] = "${period.startTime}-${period.endTime}"
                }
            }
        }

        if (timeMap.isNotEmpty()) {
            return (1..totalPeriods).map { num ->
                timeMap[num] ?: "Period $num"
            }
        }

        // Default times
        return listOf(
            "8:00-8:40", "8:40-9:20", "9:20-10:00",
            "10:15-10:55", "10:55-11:35", "11:35-12:15",
            "1:00-1:40", "1:40-2:20"
        ).take(totalPeriods)
    }

    private fun calculateCurrentPeriod(periodTimes: List<String>): Int {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        periodTimes.forEachIndexed { index, time ->
            try {
                val parts = time.split("-")
                if (parts.size == 2) {
                    val startParts = parts[0].trim().split(":")
                    val endParts = parts[1].trim().split(":")
                    val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
                    val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
                    if (currentMinutes in startMinutes..endMinutes) {
                        return index
                    }
                }
            } catch (_: Exception) { }
        }
        return -1
    }

    private fun dayOfWeekName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        Calendar.SUNDAY -> "Sunday"
        else -> ""
    }

    fun refresh() {
        loadTimetable()
    }
}
