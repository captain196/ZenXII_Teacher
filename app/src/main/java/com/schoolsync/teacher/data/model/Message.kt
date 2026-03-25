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
    val lastTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val className: String = "",
    val section: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(threadId = "")
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
    val timestamp: Long = 0L,
    val readBy: Map<String, Boolean> = emptyMap(),
    val attachmentUrl: String = "",
    val attachmentType: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(messageId = "")

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
