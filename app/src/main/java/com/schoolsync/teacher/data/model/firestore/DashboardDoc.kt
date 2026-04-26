package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class DashboardDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val role: String = "",
    val entityId: String = "",
    val data: Map<String, Any> = emptyMap(),
    val updatedAt: Any? = null
)
