package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore representation of a per-user inbox stub.
 *
 * Collection: `messageInboxes`
 * Doc id:     {schoolId}_{role}_{userId}_{conversationId}
 */
data class InboxDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val role: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val otherPartyId: String = "",
    val otherPartyName: String = "",
    val otherPartyRole: String = "",
    val otherName: String = "",
    val studentName: String = "",
    val studentClass: String = "",
    val className: String = "",
    val section: String = "",
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastMessageTime: Long = 0L,
    val lastSenderId: String = "",
    val lastSenderName: String = "",
    val unreadCount: Int = 0,
    val lastSeenAt: Long = 0L,
    val teacherDbKey: String = "",
    val otherDbKey: String = "",
    val recipientDbKey: String = "",
    val parentDbKey: String = ""
)
