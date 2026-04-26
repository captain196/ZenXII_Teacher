package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class BehaviorSummaryDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val totalMerit: Int = 0,
    val totalDemerit: Int = 0,
    val netScore: Int = 0,
    val grade: String = "",
    val totalIncidents: Int = 0,
    val incidentsByCategory: Map<String, Int> = emptyMap(),
    val trend: String = "stable",          // improving, stable, declining
    val detentionCount: Int = 0,
    val counselorReferrals: Int = 0,
    val updatedAt: Any? = null
)
