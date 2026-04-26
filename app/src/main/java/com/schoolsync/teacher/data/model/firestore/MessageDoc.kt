package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore representation of a single chat message.
 *
 * Collection: `messages`
 * Doc id:     {schoolId}_{conversationId}_{messageId}
 */
data class MessageDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val conversationId: String = "",
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val mediaUrl: String = "",
    val mediaThumb: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val readBy: Map<String, Boolean> = emptyMap(),
    val replyTo: Map<String, Any?>? = null,
    val reaction: String = "",
    val isDeleted: Boolean = false
)
