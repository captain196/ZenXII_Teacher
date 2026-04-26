package com.schoolsync.teacher.data.model

import com.google.firebase.database.ServerValue

/**
 * Message inbox item.
 * Path: Schools/{schoolCode}/Communication/Messages/Inbox/teacher/{teacherId}/
 */
data class MessageThread(
    val threadId: String = "",
    val conversationId: String = "",
    val otherPartyId: String = "",
    val otherPartyName: String = "",
    val otherPartyRole: String = "",
    val lastMessage: String = "",
    // Canonical timestamp (camelCase, ms). `lastTimestamp` is a legacy
    // alias kept so older inbox docs continue to deserialize. Always read
    // via `resolvedTimestamp`.
    val lastMessageTime: Long = 0L,
    val lastTimestamp: Long = 0L,
    val lastSeenAt: Long = 0L,
    val lastSenderId: String = "",
    val lastSenderName: String = "",
    val lastMessageType: String = "text",
    val unreadCount: Int = 0,
    val studentName: String = "",
    val studentClass: String = "",
    val className: String = "",
    val section: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(threadId = "")

    /** Prefer canonical lastMessageTime, fall back to legacy lastTimestamp. */
    val resolvedTimestamp: Long get() = if (lastMessageTime > 0L) lastMessageTime else lastTimestamp
}

/**
 * Individual chat message.
 * Path: Schools/{schoolCode}/Communication/Messages/Chat/{conversationId}/
 */
data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val text: String = "",
    val message: String = "",           // alias used by admin panel
    val timestamp: Long = 0L,
    val type: String = "text",
    val readBy: Map<String, Boolean> = emptyMap(),
    val attachmentUrl: String = "",
    val attachmentType: String = "",
    // snake_case aliases written by admin panel (Firebase auto-maps)
    val sender_id: String = "",
    val sender_name: String = "",
    val sender_role: String = "",
    val message_text: String = "",
    val sent_at: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(messageId = "")

    /** Resolved text — prefers camelCase, falls back to snake_case */
    val resolvedText: String get() = text.ifBlank { message.ifBlank { message_text } }
    val resolvedSenderId: String get() = senderId.ifBlank { sender_id }
    val resolvedSenderName: String get() = senderName.ifBlank { sender_name }

    fun toFirebaseMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "senderId" to senderId,
            "senderName" to senderName,
            "senderRole" to senderRole,
            "text" to text,
            "timestamp" to ServerValue.TIMESTAMP
        )
        if (attachmentUrl.isNotBlank()) {
            map["attachmentUrl"] = attachmentUrl
            map["attachmentType"] = attachmentType
        }
        return map
    }
}
