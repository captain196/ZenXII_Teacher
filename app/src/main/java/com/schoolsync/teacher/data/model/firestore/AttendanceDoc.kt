package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class AttendanceDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val date: String = "",           // "2026-03-24"
    val sectionKey: String = "",     // "Class 9th/Section A"
    val studentId: String = "",
    val studentName: String = "",
    val status: String = "",         // P, A, L, H, T, V
    val markedBy: String = "",       // staffId who marked
    val markedAt: Any? = null,
    val late: Boolean = false,
    val lateMinutes: Int = 0,
    val notified: Boolean = false
)
