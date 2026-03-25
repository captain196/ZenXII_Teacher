package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class CircularDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val body: String = "",
    val author: String = "",
    val authorId: String = "",
    val category: String = "",        // General, Academic, Event, Administrative, Emergency
    val priority: String = "Normal",  // Normal, Important, Urgent
    val targetType: String = "All",   // All, class, role
    val targetClasses: List<String> = emptyList(),
    val targetRoles: List<String> = emptyList(),
    val attachmentUrl: String = "",
    val requireAcknowledgement: Boolean = false,
    val totalRecipients: Int = 0,
    val readCount: Int = 0,
    val channels: List<String> = emptyList(),
    val status: String = "sent",      // draft, sent
    @ServerTimestamp
    val sentAt: Timestamp? = null,
    @ServerTimestamp
    val expiresAt: Timestamp? = null
)
