package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class DashboardDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val role: String = "",
    val entityId: String = "",
    val data: Map<String, Any> = emptyMap(),
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
