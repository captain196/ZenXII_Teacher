package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a full day timetable for a class/section.
 *
 * Collection: `timetables`
 * Doc ID: `{schoolId}_{session}_{sectionKey}_{day}` e.g. "SCH001_2025-2026_Class 8th/Section A_Monday"
 *
 * One document per day per class/section.
 */
data class TimetableDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val sectionKey: String = "",     // "Class 8th/Section A"
    val day: String = "",            // "Monday", "Tuesday", etc.
    val periods: List<PeriodDoc> = emptyList(),
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

/**
 * A single period entry within a day's timetable.
 */
data class PeriodDoc(
    val periodNumber: Int = 0,
    val subject: String = "",
    val teacher: String = "",
    val teacherId: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val room: String = "",
    val type: String = "class"       // "class", "break", "lunch"
)
