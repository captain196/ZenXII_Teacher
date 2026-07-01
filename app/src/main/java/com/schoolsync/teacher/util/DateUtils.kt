package com.schoolsync.teacher.util

import java.util.Calendar
import java.util.Date

/**
 * Canonical English weekday name for [date].
 *
 * Timetable docs store the day in English ("Monday" … "Sunday") as written by
 * the admin panel. The dashboard / timetable repo must match against that, so
 * day-name generation MUST NOT use SimpleDateFormat("EEEE", Locale.getDefault()):
 * on a non-English device that yields a localized name (e.g. "सोमवार") which
 * never equals the stored English value, silently emptying the schedule.
 *
 * Mirrors the hardcoded-English approach already used in
 * LessonPlanFirestoreRepository.dayOfWeekFromIso().
 */
fun englishDayName(date: Date = Date()): String {
    val cal = Calendar.getInstance().apply { time = date }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        Calendar.SUNDAY -> "Sunday"
        else -> ""
    }
}
