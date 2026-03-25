package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

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
    @ServerTimestamp
    val timestamp: Timestamp? = null
)
