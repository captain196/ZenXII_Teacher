package com.schoolsync.teacher.ui.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.MessageThread
import com.schoolsync.teacher.data.model.ChatMessage as ModelChatMessage
import com.schoolsync.teacher.data.repository.MessagesRepository
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

data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversation: Conversation? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val messageInput: String = "",
    val isLoadingConversations: Boolean = true,
    val isLoadingChat: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

sealed class MessagesEvent {
    data object MessageSent : MessagesEvent()
    data class Error(val message: String) : MessagesEvent()
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
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
                                lastMessageTime = formatTimestamp(thread.lastTimestamp),
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

    fun selectConversation(conversation: Conversation) {
        _uiState.update {
            it.copy(selectedConversation = conversation, chatMessages = emptyList())
        }
        loadChatMessages(conversation.conversationId)
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
                                senderId = msg.senderId,
                                senderName = msg.senderName,
                                message = msg.text,
                                timestamp = formatTimestamp(msg.timestamp),
                                isFromTeacher = msg.senderId == currentTeacherId ||
                                        msg.senderRole.equals("teacher", ignoreCase = true),
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
}
