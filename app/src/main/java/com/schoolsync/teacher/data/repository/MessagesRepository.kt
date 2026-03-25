package com.schoolsync.teacher.data.repository

import com.google.firebase.database.ServerValue
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ChatMessage
import com.schoolsync.teacher.data.model.MessageThread
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Teacher-parent messaging via Firebase RTDB.
 * Inbox path: Schools/{schoolCode}/Communication/Messages/Inbox/teacher/{teacherId}/
 * Chat path:  Schools/{schoolCode}/Communication/Messages/Chat/{conversationId}/
 */
@Singleton
class MessagesRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    suspend fun getConversations(): Result<List<MessageThread>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.MESSAGES_INBOX}/teacher/$teacherId"
            val snapshot = firebaseService.readSnapshot(path)

            val threads = mutableListOf<MessageThread>()
            for (child in snapshot.children) {
                val thread = child.getValue(MessageThread::class.java)
                if (thread != null) {
                    threads.add(thread.copy(threadId = child.key ?: ""))
                }
            }

            Result.success(threads.sortedByDescending { it.lastTimestamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatMessages(conversationId: String): Result<List<ChatMessage>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.MESSAGES_CHAT}/$conversationId"
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

    suspend fun sendMessage(conversationId: String, text: String): Result<Unit> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))
            val teacherName = tokenManager.userName.firstOrNull() ?: "Teacher"

            val chatPath = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.MESSAGES_CHAT}/$conversationId"

            val message = ChatMessage(
                senderId = teacherId,
                senderName = teacherName,
                senderRole = "teacher",
                text = text
            )

            firebaseService.pushValue(chatPath, message.toFirebaseMap())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
