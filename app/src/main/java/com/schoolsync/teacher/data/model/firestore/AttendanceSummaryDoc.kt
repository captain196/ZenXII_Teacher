package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class AttendanceSummaryDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val month: String = "",          // "March 2026"
    val studentId: String = "",
    val studentName: String = "",
    val sectionKey: String = "",
    val dayWise: String = "",        // "PPAPLHV..." string for calendar view
    val present: Int = 0,
    val absent: Int = 0,
    val late: Int = 0,
    val leave: Int = 0,
    val holiday: Int = 0,
    val vacation: Int = 0,
    val totalDays: Int = 0,
    val workingDays: Int = 0,
    val percentage: Double = 0.0,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
