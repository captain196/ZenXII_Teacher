package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore `attendanceSummary` document model.
 *
 * Phase 9b: all fields that admin writes are now present so the
 * Firestore CustomClassMapper doesn't silently drop them.
 */
data class AttendanceSummaryDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val month: String = "",            // "2026-04" (canonical YYYY-MM)
    val monthLabel: String = "",       // "April 2026"
    val type: String = "student",      // "student" or "staff"
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",        // "Class 8th"
    val section: String = "",          // "Section A"
    val sectionKey: String = "",       // "Class 8th/Section A"
    val dayWise: String = "",
    val present: Int = 0,
    val absent: Int = 0,
    val tardy: Int = 0,
    val leave: Int = 0,
    val holiday: Int = 0,
    val vacation: Int = 0,
    val totalDays: Int = 0,
    val workingDays: Int = 0,
    val percentage: Double = 0.0,
    val lateTimes: Map<String, Map<String, String>> = emptyMap(),
    val updatedAt: Any? = null,
    val updatedBy: String = ""
) {
    fun arrivalTimeFor(day: Int): String? {
        val entry = lateTimes[day.toString()] ?: return null
        return entry["time"]?.takeIf { it.isNotBlank() }
    }
}
