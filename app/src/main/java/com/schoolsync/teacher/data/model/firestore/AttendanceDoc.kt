package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class AttendanceDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val date: String = "",           // "2026-03-24"
    val sectionKey: String = "",     // "9th_A"
    val studentId: String = "",
    val studentName: String = "",
    val status: String = "",         // P, A, L, H, T, V
    val markedBy: String = "",       // staffId who marked
    @ServerTimestamp
    val markedAt: Timestamp? = null,
    val late: Boolean = false,
    val lateMinutes: Int = 0,
    val notified: Boolean = false
)
