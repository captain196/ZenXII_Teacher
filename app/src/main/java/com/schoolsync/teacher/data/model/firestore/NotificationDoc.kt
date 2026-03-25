package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class NotificationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val userId: String = "",
    val type: String = "",            // attendance, fee, homework, exam, circular, chat, transport, general
    val title: String = "",
    val body: String = "",
    val icon: String = "",
    val priority: String = "normal",  // normal, high
    val data: Map<String, String> = emptyMap(),  // deep link params
    val read: Boolean = false,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val expiresAt: Timestamp? = null
)
