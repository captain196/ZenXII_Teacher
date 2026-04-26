package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class AuditLogDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val userId: String = "",
    val userName: String = "",
    val action: String = "",
    val module: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val details: Map<String, Any> = emptyMap(),
    val ip: String = "",
    val userAgent: String = "",
    val timestamp: Any? = null
)
