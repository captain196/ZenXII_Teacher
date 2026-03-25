package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class IncidentDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val sectionKey: String = "",
    val date: String = "",
    val time: String = "",
    val category: String = "",             // tardiness, bullying, vandalism, uniform_violation
    val severity: String = "minor",        // minor, moderate, major
    val description: String = "",
    val location: String = "",
    val witnesses: List<String> = emptyList(),
    val reportedBy: String = "",
    val reportedByName: String = "",
    val pointsImpact: Int = 0,
    val action: String = "",
    val parentNotified: Boolean = false,
    val status: String = "open",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
