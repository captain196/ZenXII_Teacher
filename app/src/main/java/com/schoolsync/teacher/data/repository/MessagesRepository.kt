package com.schoolsync.teacher.data.repository

import com.google.firebase.database.ServerValue
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ChatMessage
import com.schoolsync.teacher.data.model.MessageThread
import com.schoolsync.teacher.data.model.firestore.ConversationDoc
import com.schoolsync.teacher.data.model.firestore.InboxDoc
import com.schoolsync.teacher.data.model.firestore.MessageDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Teacher-parent messaging.
 *
 * Phase 5: Firestore-first via [firestoreService] (collections
 * `conversations`, `messages`, `messageInboxes`). RTDB stays as a
 * best-effort mirror for older builds and unmigrated data.
 *
 * RTDB legacy paths (mirror only):
 *   Inbox: Schools/{schoolCode}/Communication/Messages/Inbox/teacher/{teacherId}/
 *   Chat:  Schools/{schoolCode}/Communication/Messages/Chat/{conversationId}/
 */
@Singleton
class MessagesRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    private companion object {
        const val COL_CONVERSATIONS = "conversations"
        const val COL_MESSAGES      = "messages"
        const val COL_INBOXES       = "messageInboxes"
    }

    // ── Firestore doc id helpers (must match Messaging_service.php) ───────

    private fun convDocId(schoolId: String, convId: String) = "${schoolId}_$convId"
    private fun msgDocId(schoolId: String, convId: String, msgId: String) =
        "${schoolId}_${convId}_$msgId"
    private fun inboxDocId(schoolId: String, role: String, userId: String, convId: String) =
        "${schoolId}_${role.lowercase()}_${userId}_$convId"

    private fun newMsgId(): String =
        "MSG_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"

    /** Convert a Firestore [InboxDoc] into the legacy [MessageThread] model. */
    private fun InboxDoc.toMessageThread(): MessageThread = MessageThread(
        threadId = id,
        conversationId = conversationId,
        otherPartyId = otherPartyId,
        otherPartyName = otherPartyName.ifBlank { otherName },
        otherPartyRole = otherPartyRole,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTime,
        lastTimestamp = lastMessageTime,
        lastSeenAt = lastSeenAt,
        lastSenderId = lastSenderId,
        lastSenderName = lastSenderName,
        lastMessageType = lastMessageType,
        unreadCount = unreadCount,
        studentName = studentName,
        studentClass = studentClass,
        className = className,
        section = section
    )

    /** Convert a Firestore [MessageDoc] into the legacy [ChatMessage] model. */
    private fun MessageDoc.toChatMessage(): ChatMessage = ChatMessage(
        messageId = messageId.ifBlank { id },
        senderId = senderId,
        senderName = senderName,
        senderRole = senderRole,
        text = text,
        timestamp = timestamp,
        type = type,
        readBy = readBy,
        attachmentUrl = attachmentUrl.ifBlank { mediaUrl },
        attachmentType = type
    )
    /**
     * "Delete chat for me" — removes only this teacher's inbox stub for
     * the conversation. The shared `Conversations/{id}` doc and the chat
     * history under `Chat/{id}` are left intact so the parent / admin
     * still sees the conversation.
     */
    suspend fun deleteConversationForMe(conversationId: String): Result<Unit> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull()
                ?: return Result.failure(Exception("School ID not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            // 1. Firestore primary — delete the inbox stub doc.
            try {
                firestoreService.deleteDocument(
                    COL_INBOXES,
                    inboxDocId(schoolId, "teacher", teacherId, conversationId)
                )
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore inbox delete failed", e)
            }

            // 2. RTDB mirror — clear both lowercase + capital paths in
            // case any legacy data still lives there.
            val basePath = "${Constants.Firebase.SCHOOLS}/$schoolId/Communication/Messages/Inbox"
            firebaseService.setValue("$basePath/teacher/$teacherId/$conversationId", null)
            firebaseService.setValue("$basePath/Teacher/$teacherId/$conversationId", null)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MessagesRepo", "deleteConversationForMe failed", e)
            Result.failure(e)
        }
    }

    suspend fun getConversations(): Result<List<MessageThread>> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull()
                ?: return Result.failure(Exception("School ID not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            // 1. Firestore primary
            try {
                val docs = firestoreService.queryDocumentsAs<InboxDoc>(COL_INBOXES) { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("role", "teacher")
                        .whereEqualTo("userId", teacherId)
                }
                if (docs.isNotEmpty()) {
                    return Result.success(
                        docs.map { it.toMessageThread() }
                            .sortedByDescending { it.resolvedTimestamp }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore inbox query failed, falling back to RTDB", e)
            }

            // 2. RTDB fallback
            val path = "${Constants.Firebase.SCHOOLS}/$schoolId/${Constants.Firebase.MESSAGES_INBOX}/teacher/$teacherId"
            val snapshot = firebaseService.readSnapshot(path)

            val threads = mutableListOf<MessageThread>()
            for (child in snapshot.children) {
                val thread = child.getValue(MessageThread::class.java)
                if (thread != null) {
                    threads.add(thread.copy(threadId = child.key ?: ""))
                }
            }
            Result.success(threads.sortedByDescending { it.resolvedTimestamp })
        } catch (e: Exception) {
            android.util.Log.e("MessagesRepo", "getConversations failed", e)
            Result.failure(e)
        }
    }

    /**
     * Reset this teacher's unread count for a conversation and stamp
     * `lastSeenAt` so the admin/parent know we've read it. Best-effort —
     * never throws back to the caller.
     */
    suspend fun markConversationRead(conversationId: String) {
        try {
            val schoolId = tokenManager.schoolId.firstOrNull() ?: return
            val teacherId = tokenManager.userId.firstOrNull() ?: return
            val now = System.currentTimeMillis()
            val fields = mapOf(
                "unreadCount" to 0,
                "lastSeenAt" to now
            )
            // 1. Firestore primary
            try {
                firestoreService.setDocument(
                    COL_INBOXES,
                    inboxDocId(schoolId, "teacher", teacherId, conversationId),
                    fields + mapOf(
                        "schoolId" to schoolId,
                        "role" to "teacher",
                        "userId" to teacherId,
                        "conversationId" to conversationId
                    ),
                    merge = true
                )
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore markRead failed", e)
            }
            // 2. RTDB mirror
            try {
                val inboxPath = "${Constants.Firebase.SCHOOLS}/$schoolId/Communication/Messages/Inbox/teacher/$teacherId/$conversationId"
                firebaseService.updateChildren(inboxPath, fields)
            } catch (_: Exception) { /* best-effort */ }
        } catch (e: Exception) {
            android.util.Log.w("MessagesRepo", "markConversationRead failed", e)
        }
    }

    suspend fun getChatMessages(conversationId: String): Result<List<ChatMessage>> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull()
                ?: return Result.failure(Exception("School ID not available"))

            // 1. Firestore primary
            try {
                val docs = firestoreService.queryDocumentsAs<MessageDoc>(COL_MESSAGES) { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("conversationId", conversationId)
                }
                if (docs.isNotEmpty()) {
                    return Result.success(
                        docs.map { it.toChatMessage() }.sortedBy { it.timestamp }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore chat query failed, falling back to RTDB", e)
            }

            // 2. RTDB fallback
            val path = "${Constants.Firebase.SCHOOLS}/$schoolId/${Constants.Firebase.MESSAGES_CHAT}/$conversationId"
            val snapshot = firebaseService.readSnapshot(path)

            val messages = mutableListOf<ChatMessage>()
            for (child in snapshot.children) {
                val msg = child.getValue(ChatMessage::class.java)
                if (msg != null) {
                    messages.add(msg.copy(messageId = child.key ?: ""))
                }
            }

            Result.success(messages.sortedBy { it.timestamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new conversation between the teacher and a student/parent.
     * Writes:
     *  - Conversations/{convId}    (metadata)
     *  - Chat/{convId}/{msgId}     (initial message)
     *  - Inbox/teacher/{teacherId}/{convId}  (teacher web)
     *  - Inbox/Teacher/{teacherId}/{convId}  (admin web)
     *  - Inbox/parent/{parentDbKey}/{convId} (parent app)
     */
    suspend fun createConversation(
        studentId: String,
        studentName: String,
        parentName: String,
        className: String,
        section: String,
        parentDbKey: String,
        initialMessage: String
    ): Result<String> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull()
                ?: return Result.failure(Exception("School ID not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))
            val teacherName = tokenManager.userName.firstOrNull() ?: "Teacher"

            // Generate a unique conversation ID
            val convId = "CONV_T${System.currentTimeMillis()}"
            val now = System.currentTimeMillis()
            val preview = if (initialMessage.length > 100) initialMessage.substring(0, 100) else initialMessage
            val displayName = if (parentName.isNotBlank()) "$studentName ($parentName)" else studentName

            val basePath = "${Constants.Firebase.SCHOOLS}/$schoolId/Communication/Messages"

            // 1. Conversation metadata — canonical camelCase only.
            val convDocFields = mapOf(
                "schoolId" to schoolId,
                "conversationId" to convId,
                "participants" to mapOf(
                    teacherId to "Teacher",
                    studentId to "Parent"
                ),
                "participantNames" to mapOf(
                    teacherId to teacherName,
                    studentId to displayName
                ),
                "participantIds" to listOf(teacherId, studentId),
                "type" to "direct",
                "title" to "",
                "context" to mapOf(
                    "studentId" to studentId,
                    "className" to className,
                    "section" to section
                ),
                "teacherDbKey" to teacherId,
                "lastMessage" to preview,
                "lastMessageTime" to now,
                "lastSenderId" to teacherId,
                "lastSenderName" to teacherName,
                "createdBy" to teacherId,
                "createdAt" to now,
                "updatedAt" to now,
                "status" to "active"
            )
            // 1a. Firestore primary
            try {
                firestoreService.setDocument(
                    COL_CONVERSATIONS,
                    convDocId(schoolId, convId),
                    convDocFields,
                    merge = false
                )
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore conv create failed", e)
            }
            // 1b. RTDB mirror
            firebaseService.setValue("$basePath/Conversations/$convId", convDocFields)

            // 2. Initial message — canonical camelCase only.
            val msgId = newMsgId()
            val msgFields = mapOf(
                "schoolId" to schoolId,
                "conversationId" to convId,
                "messageId" to msgId,
                "senderId" to teacherId,
                "senderName" to teacherName,
                "senderRole" to "teacher",
                "text" to initialMessage,
                "timestamp" to now,
                "type" to "text",
                "readBy" to mapOf(teacherId to true)
            )
            // 2a. Firestore primary
            try {
                firestoreService.setDocument(
                    COL_MESSAGES,
                    msgDocId(schoolId, convId, msgId),
                    msgFields,
                    merge = false
                )
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore message create failed", e)
            }
            // 2b. RTDB mirror
            firebaseService.setValue("$basePath/Chat/$convId/$msgId", msgFields)

            // 3. Teacher's own inbox - 0 unread
            val teacherInbox = mapOf(
                "schoolId" to schoolId,
                "role" to "teacher",
                "userId" to teacherId,
                "unreadCount" to 0,
                "lastSeenAt" to now,
                "conversationId" to convId,
                "otherName" to displayName,
                "otherPartyId" to studentId,
                "otherPartyName" to displayName,
                "otherPartyRole" to "Parent",
                "studentName" to studentName,
                "studentClass" to className,
                "className" to className,
                "section" to section,
                "lastMessage" to preview,
                "lastMessageTime" to now,
                "lastSenderId" to teacherId,
                "lastSenderName" to teacherName,
                // Legacy alias kept temporarily for older Teacher app builds
                // that still read `lastTimestamp`.
                "lastTimestamp" to now
            )
            // 3a. Firestore primary
            try {
                firestoreService.setDocument(
                    COL_INBOXES,
                    inboxDocId(schoolId, "teacher", teacherId, convId),
                    teacherInbox,
                    merge = false
                )
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore teacher inbox seed failed", e)
            }
            // 3b. RTDB mirror — single lowercase path.
            firebaseService.setValue("$basePath/Inbox/teacher/$teacherId/$convId", teacherInbox)

            // 4. Parent inbox (mobile app) - 1 unread
            if (parentDbKey.isNotBlank()) {
                val parentInbox = mapOf(
                    "schoolId" to schoolId,
                    "role" to "parent",
                    "userId" to parentDbKey,
                    "unreadCount" to 1,
                    "lastSeenAt" to 0L,
                    "conversationId" to convId,
                    "otherName" to "$teacherName (Teacher)",
                    "otherPartyId" to teacherId,
                    "otherPartyName" to teacherName,
                    "otherPartyRole" to "Teacher",
                    "senderName" to teacherName,
                    "studentName" to studentName,
                    "studentClass" to className,
                    "className" to className,
                    "section" to section,
                    "lastMessage" to preview,
                    "lastMessageTime" to now,
                    "lastSenderId" to teacherId,
                    "lastSenderName" to teacherName,
                    "teacherDbKey" to teacherId,
                    "otherDbKey" to teacherId,
                    "recipientDbKey" to teacherId
                )
                // 4a. Firestore primary
                try {
                    firestoreService.setDocument(
                        COL_INBOXES,
                        inboxDocId(schoolId, "parent", parentDbKey, convId),
                        parentInbox,
                        merge = false
                    )
                } catch (e: Exception) {
                    android.util.Log.w("MessagesRepo", "Firestore parent inbox seed failed", e)
                }
                // 4b. RTDB mirror
                firebaseService.setValue("$basePath/Inbox/parent/$parentDbKey/$convId", parentInbox)
            }

            Result.success(convId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(conversationId: String, text: String): Result<Unit> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull()
                ?: return Result.failure(Exception("School ID not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))
            val teacherName = tokenManager.userName.firstOrNull() ?: "Teacher"

            val now = System.currentTimeMillis()
            val msgId = newMsgId()
            val preview = if (text.length > 100) text.substring(0, 100) else text

            val msgFields = mapOf(
                "schoolId" to schoolId,
                "conversationId" to conversationId,
                "messageId" to msgId,
                "senderId" to teacherId,
                "senderName" to teacherName,
                "senderRole" to "teacher",
                "text" to text,
                "timestamp" to now,
                "type" to "text",
                "readBy" to mapOf(teacherId to true)
            )

            // 1a. Firestore primary
            try {
                firestoreService.setDocument(
                    COL_MESSAGES,
                    msgDocId(schoolId, conversationId, msgId),
                    msgFields,
                    merge = false
                )
            } catch (e: Exception) {
                android.util.Log.w("MessagesRepo", "Firestore message create failed", e)
            }

            // 1b. RTDB mirror
            try {
                val chatPath = "${Constants.Firebase.SCHOOLS}/$schoolId/${Constants.Firebase.MESSAGES_CHAT}/$conversationId/$msgId"
                firebaseService.setValue(chatPath, msgFields)
            } catch (_: Exception) { /* best-effort */ }

            // 2. Update conversation metadata — Firestore-first.
            val convFields = mapOf(
                "schoolId" to schoolId,
                "lastMessage" to preview,
                "lastMessageTime" to now,
                "lastSenderId" to teacherId,
                "lastSenderName" to teacherName,
                "updatedAt" to now
            )
            try {
                firestoreService.setDocument(
                    COL_CONVERSATIONS,
                    convDocId(schoolId, conversationId),
                    convFields,
                    merge = true
                )
            } catch (_: Exception) { /* best-effort */ }
            try {
                val convPath = "${Constants.Firebase.SCHOOLS}/$schoolId/Communication/Messages/Conversations/$conversationId"
                firebaseService.updateChildren(convPath, convFields)
            } catch (_: Exception) { /* best-effort */ }

            // 3. Update teacher's own inbox (0 unread) — Firestore-first.
            val teacherInboxFields = mapOf(
                "schoolId" to schoolId,
                "role" to "teacher",
                "userId" to teacherId,
                "conversationId" to conversationId,
                "lastMessage" to preview,
                "lastMessageTime" to now,
                "lastSenderId" to teacherId,
                "lastSenderName" to teacherName,
                "unreadCount" to 0,
                "lastSeenAt" to now
            )
            try {
                firestoreService.setDocument(
                    COL_INBOXES,
                    inboxDocId(schoolId, "teacher", teacherId, conversationId),
                    teacherInboxFields,
                    merge = true
                )
            } catch (_: Exception) { /* best-effort */ }
            try {
                val teacherInboxPath = "${Constants.Firebase.SCHOOLS}/$schoolId/${Constants.Firebase.MESSAGES_INBOX}/teacher/$teacherId/$conversationId"
                firebaseService.updateChildren(teacherInboxPath, teacherInboxFields + mapOf(
                    "lastTimestamp" to now
                ))
            } catch (_: Exception) { /* best-effort */ }

            // 4. Fan out to other participants — Firestore primary,
            // RTDB mirror. Reads conversation participants from
            // Firestore first (canonical), falls back to RTDB.
            try {
                val convDoc = try {
                    firestoreService.getDocumentAs<ConversationDoc>(
                        COL_CONVERSATIONS, convDocId(schoolId, conversationId)
                    )
                } catch (_: Exception) { null }

                val participants: Map<String, String> = convDoc?.participants
                    ?: run {
                        try {
                            val convPath = "${Constants.Firebase.SCHOOLS}/$schoolId/Communication/Messages/Conversations/$conversationId"
                            val convSnapshot = firebaseService.readSnapshot(convPath)
                            (convSnapshot.child("participants").value as? Map<*, *>)
                                ?.mapNotNull { (k, v) ->
                                    val ks = k?.toString() ?: return@mapNotNull null
                                    ks to (v?.toString() ?: "Admin")
                                }?.toMap().orEmpty()
                        } catch (_: Exception) { emptyMap() }
                    }

                for ((participantId, role) in participants) {
                    if (participantId == teacherId) continue
                    val inboxRole = when (role.lowercase()) {
                        "teacher" -> "teacher"
                        "parent", "student" -> "parent"
                        "hr", "hr manager" -> "hr"
                        else -> "admin"
                    }

                    // Read current unread (Firestore primary).
                    val currentUnread = try {
                        firestoreService.getDocumentAs<InboxDoc>(
                            COL_INBOXES, inboxDocId(schoolId, inboxRole, participantId, conversationId)
                        )?.unreadCount ?: 0
                    } catch (_: Exception) { 0 }

                    val patch = mapOf(
                        "schoolId" to schoolId,
                        "role" to inboxRole,
                        "userId" to participantId,
                        "conversationId" to conversationId,
                        "lastMessage" to preview,
                        "lastMessageTime" to now,
                        "lastSenderId" to teacherId,
                        "lastSenderName" to teacherName,
                        "unreadCount" to (currentUnread + 1)
                    )

                    // 4a. Firestore primary
                    try {
                        firestoreService.setDocument(
                            COL_INBOXES,
                            inboxDocId(schoolId, inboxRole, participantId, conversationId),
                            patch,
                            merge = true
                        )
                    } catch (_: Exception) { /* best-effort */ }

                    // 4b. RTDB mirror (lowercase path)
                    try {
                        val adminInboxPath = "${Constants.Firebase.SCHOOLS}/$schoolId/Communication/Messages/Inbox/$inboxRole/$participantId/$conversationId"
                        firebaseService.updateChildren(adminInboxPath, patch + mapOf(
                            "lastTimestamp" to now
                        ))
                    } catch (_: Exception) { /* best-effort */ }
                }
            } catch (_: Exception) { /* best-effort */ }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
