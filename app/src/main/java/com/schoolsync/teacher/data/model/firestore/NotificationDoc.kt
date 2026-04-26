package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

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
    val createdAt: Any? = null,
    val expiresAt: Any? = null
)
