package com.schoolsync.teacher.ui.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.MessageThread
import com.schoolsync.teacher.data.model.ChatMessage as ModelChatMessage
import com.schoolsync.teacher.data.model.firestore.StudentDoc
import com.schoolsync.teacher.data.repository.MessagesRepository
import com.schoolsync.teacher.data.repository.firestore.StudentFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class Conversation(
    val conversationId: String,
    val parentName: String,
    val studentName: String,
    val studentClass: String = "",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val parentProfilePic: String = ""
)

data class ChatMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: String,
    val isFromTeacher: Boolean,
    val isRead: Boolean = false
)

data class StudentPick(
    val studentId: String,
    val name: String,
    val fatherName: String,
    val className: String,
    val section: String,
    val parentDbKey: String,
    val rollNo: String = ""
)

data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversation: Conversation? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val messageInput: String = "",
    val isLoadingConversations: Boolean = true,
    val isLoadingChat: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    // New conversation flow
    val showNewConversationSheet: Boolean = false,
    val students: List<StudentPick> = emptyList(),
    val isLoadingStudents: Boolean = false,
    val studentSearchQuery: String = "",
    val selectedStudent: StudentPick? = null,
    val newMessageInput: String = "",
    val isCreatingConversation: Boolean = false
)

sealed class MessagesEvent {
    data object MessageSent : MessagesEvent()
    data class Error(val message: String) : MessagesEvent()
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
    private val studentFirestoreRepository: StudentFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "MessagesVM"
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MessagesEvent>()
    val events = _events.asSharedFlow()

    private var currentTeacherId: String = ""

    init {
        viewModelScope.launch {
            currentTeacherId = tokenManager.userId.firstOrNull() ?: ""
        }
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConversations = true, error = null) }
            try {
                messagesRepository.getConversations().fold(
                    onSuccess = { threads ->
                        val conversations = threads.map { thread ->
                            Conversation(
                                conversationId = thread.conversationId.ifBlank { thread.threadId },
                                parentName = thread.otherPartyName,
                                studentName = "", // will be populated if available
                                studentClass = "${thread.className}-${thread.section}",
                                lastMessage = thread.lastMessage,
                                lastMessageTime = formatTimestamp(thread.resolvedTimestamp),
                                unreadCount = thread.unreadCount
                            )
                        }
                        _uiState.update {
                            it.copy(conversations = conversations, isLoadingConversations = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoadingConversations = false, error = e.message)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversations", e)
                _uiState.update { it.copy(isLoadingConversations = false, error = e.message) }
            }
        }
    }

    /**
     * "Delete chat for me" — drops only this teacher's inbox stub. Other
     * participants still see the conversation. If the deleted thread is
     * currently open, also closes the chat panel.
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            val result = messagesRepository.deleteConversationForMe(conversationId)
            result.onSuccess {
                _uiState.update { st ->
                    val isOpen = st.selectedConversation?.conversationId == conversationId
                    st.copy(
                        conversations = st.conversations.filterNot { it.conversationId == conversationId },
                        selectedConversation = if (isOpen) null else st.selectedConversation,
                        chatMessages = if (isOpen) emptyList() else st.chatMessages
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to delete conversation") }
            }
        }
    }

    fun selectConversation(conversation: Conversation) {
        _uiState.update {
            it.copy(
                selectedConversation = conversation,
                chatMessages = emptyList(),
                // Optimistically clear the unread badge in the list — the
                // server-side reset happens via markConversationRead below.
                conversations = it.conversations.map { c ->
                    if (c.conversationId == conversation.conversationId) c.copy(unreadCount = 0) else c
                }
            )
        }
        loadChatMessages(conversation.conversationId)
        viewModelScope.launch { messagesRepository.markConversationRead(conversation.conversationId) }
    }

    private fun loadChatMessages(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChat = true) }
            try {
                messagesRepository.getChatMessages(conversationId).fold(
                    onSuccess = { modelMessages ->
                        val messages = modelMessages.map { msg ->
                            ChatMessage(
                                messageId = msg.messageId,
                                senderId = msg.resolvedSenderId,
                                senderName = msg.resolvedSenderName,
                                message = msg.resolvedText,
                                timestamp = formatTimestamp(msg.timestamp),
                                isFromTeacher = msg.resolvedSenderId == currentTeacherId ||
                                        msg.senderRole.ifBlank { msg.sender_role }.equals("teacher", ignoreCase = true),
                                isRead = msg.readBy.containsKey(currentTeacherId)
                            )
                        }
                        _uiState.update {
                            it.copy(chatMessages = messages, isLoadingChat = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoadingChat = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingChat = false, error = e.message) }
            }
        }
    }

    fun onMessageInputChange(value: String) {
        _uiState.update { it.copy(messageInput = value) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return
        val message = state.messageInput.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, messageInput = "") }
            try {
                messagesRepository.sendMessage(
                    conversationId = conversation.conversationId,
                    text = message
                ).fold(
                    onSuccess = {
                        loadChatMessages(conversation.conversationId)
                        _uiState.update { it.copy(isSending = false) }
                        _events.emit(MessagesEvent.MessageSent)
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isSending = false, messageInput = message)
                        }
                        _events.emit(MessagesEvent.Error(e.message ?: "Failed to send"))
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, messageInput = message) }
                _events.emit(MessagesEvent.Error(e.message ?: "Failed to send"))
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return try {
            val date = Date(timestamp)
            val now = Date()
            val diff = now.time - timestamp
            val oneDay = 24 * 60 * 60 * 1000L
            if (diff < oneDay) {
                timeFormat.format(date)
            } else {
                dateFormat.format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadConversations()
        _uiState.value.selectedConversation?.let {
            loadChatMessages(it.conversationId)
        }
    }

    // ── New Conversation Flow ─────────────────────────────────────────

    fun openNewConversation() {
        _uiState.update { it.copy(showNewConversationSheet = true, students = emptyList()) }
        loadStudents()
    }

    fun closeNewConversation() {
        _uiState.update {
            it.copy(
                showNewConversationSheet = false,
                selectedStudent = null,
                newMessageInput = "",
                studentSearchQuery = ""
            )
        }
    }

    fun onStudentSearchChange(query: String) {
        _uiState.update { it.copy(studentSearchQuery = query) }
    }

    fun selectStudent(student: StudentPick) {
        _uiState.update { it.copy(selectedStudent = student) }
    }

    fun clearSelectedStudent() {
        _uiState.update { it.copy(selectedStudent = null, newMessageInput = "") }
    }

    fun onNewMessageInputChange(text: String) {
        _uiState.update { it.copy(newMessageInput = text) }
    }

    private fun loadStudents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStudents = true) }
            try {
                studentFirestoreRepository.getStudentsBySchool().fold(
                    onSuccess = { docs ->
                        val students = docs
                            .filter { it.status.equals("Active", ignoreCase = true) || it.status.isBlank() }
                            .map { d ->
                                StudentPick(
                                    studentId = d.id.ifBlank { d.userId },
                                    name = d.name,
                                    fatherName = d.fatherName,
                                    className = d.className,
                                    section = d.section,
                                    parentDbKey = d.parentDbKey,
                                    rollNo = d.rollNo
                                )
                            }
                            .sortedWith(compareBy({ it.className }, { it.section }, { it.name }))
                        _uiState.update { it.copy(students = students, isLoadingStudents = false) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load students", e)
                        _uiState.update { it.copy(isLoadingStudents = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading students", e)
                _uiState.update { it.copy(isLoadingStudents = false, error = e.message) }
            }
        }
    }

    fun createNewConversation() {
        val state = _uiState.value
        val student = state.selectedStudent ?: return
        val message = state.newMessageInput.trim()
        if (message.isEmpty()) {
            viewModelScope.launch { _events.emit(MessagesEvent.Error("Please enter a message")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingConversation = true) }
            try {
                messagesRepository.createConversation(
                    studentId = student.studentId,
                    studentName = student.name,
                    parentName = student.fatherName,
                    className = student.className,
                    section = student.section,
                    parentDbKey = student.parentDbKey,
                    initialMessage = message
                ).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isCreatingConversation = false,
                                showNewConversationSheet = false,
                                selectedStudent = null,
                                newMessageInput = "",
                                studentSearchQuery = ""
                            )
                        }
                        _events.emit(MessagesEvent.MessageSent)
                        loadConversations() // refresh list
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isCreatingConversation = false) }
                        _events.emit(MessagesEvent.Error(e.message ?: "Failed to create conversation"))
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreatingConversation = false) }
                _events.emit(MessagesEvent.Error(e.message ?: "Failed to create conversation"))
            }
        }
    }
}
