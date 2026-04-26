package com.schoolsync.teacher.data.model

/**
 * A single period entry in the timetable.
 * Path: Schools/{schoolCode}/{session}/{class}/{section}/Time_table/
 *
 * The timetable is typically structured as:
 * Time_table/{day}/{periodNumber} → { subject, teacher, startTime, endTime }
 */
data class TimetableEntry(
    val day: String = "",
    val periodNumber: Int = 0,
    val subject: String = "",
    val teacher: String = "",
    val teacherId: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val room: String = "",
    val className: String = "",
    val section: String = "",
    /** Period type from Firestore PeriodDoc.type: "class" | "break" | "lunch" */
    val type: String = "class",
    /** Derived flag — any non-class period (break, lunch, assembly, etc.). */
    val isBreak: Boolean = false
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(day = "")

    val isLunch: Boolean get() = type.equals("lunch", ignoreCase = true) ||
        subject.equals("Lunch", ignoreCase = true)

    val timeSlot: String
        get() = if (startTime.isNotBlank() && endTime.isNotBlank()) {
            "$startTime - $endTime"
        } else {
            "Period $periodNumber"
        }

    val displayLabel: String
        get() = if (isBreak) {
            if (isLunch) "Lunch Break" else (subject.ifBlank { "Break" })
        } else {
            "$subject ($className-$section)"
        }
}

/**
 * Full day timetable for display.
 */
data class DayTimetable(
    val day: String,
    val periods: List<TimetableEntry>
)
