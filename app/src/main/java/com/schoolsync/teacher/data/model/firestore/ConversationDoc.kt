package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore representation of a chat conversation.
 *
 * Collection: `conversations`
 * Doc id:     {schoolId}_{conversationId}
 *
 * Mirrors the canonical schema written by Messaging_service.php and the
 * parent app. Field names are the camelCase contract from Phase 1-4.
 */
data class ConversationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val conversationId: String = "",
    val participants: Map<String, String> = emptyMap(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantIds: List<String> = emptyList(),
    val type: String = "direct",
    val title: String = "",
    val context: Map<String, Any?> = emptyMap(),
    val teacherDbKey: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastSenderId: String = "",
    val lastSenderName: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val status: String = "active"
) {
    val studentId: String get() = (context["studentId"] as? String).orEmpty()
    val className: String get() = (context["className"] as? String).orEmpty()
    val section: String get() = (context["section"] as? String).orEmpty()
}
