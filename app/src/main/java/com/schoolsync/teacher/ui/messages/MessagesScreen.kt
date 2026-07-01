package com.schoolsync.teacher.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.schoolsync.teacher.ui.components.staggerIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.teacher.R
import com.schoolsync.teacher.ui.theme.BgEnd
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.glassCard

@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left panel: Conversations list
            ConversationList(
                conversations = state.conversations,
                selectedId = state.selectedConversation?.conversationId,
                isLoading = state.isLoadingConversations,
                onConversationClick = viewModel::selectConversation,
                onDeleteConversation = viewModel::deleteConversation,
                onRefresh = viewModel::refresh,
                onNewConversation = viewModel::openNewConversation,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
            )

            // Divider
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = DividerColor
            )

            // Right panel: Chat view
            if (state.selectedConversation != null) {
                ChatPanel(
                    conversation = state.selectedConversation!!,
                    messages = state.chatMessages,
                    messageInput = state.messageInput,
                    isLoadingChat = state.isLoadingChat,
                    isSending = state.isSending,
                    onMessageInputChange = viewModel::onMessageInputChange,
                    onSendMessage = viewModel::sendMessage,
                    onDeleteConversation = viewModel::deleteConversation,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                )
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Chat,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.messages_select_conversation),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = stringResource(R.string.messages_select_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        // ── New Conversation Dialog ──
        if (state.showNewConversationSheet) {
            NewConversationDialog(
                students = state.students,
                isLoadingStudents = state.isLoadingStudents,
                searchQuery = state.studentSearchQuery,
                selectedStudent = state.selectedStudent,
                messageInput = state.newMessageInput,
                isCreating = state.isCreatingConversation,
                onSearchChange = viewModel::onStudentSearchChange,
                onStudentSelect = viewModel::selectStudent,
                onClearStudent = viewModel::clearSelectedStudent,
                onMessageChange = viewModel::onNewMessageInputChange,
                onSend = viewModel::createNewConversation,
                onClose = viewModel::closeNewConversation
            )
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    selectedId: String?,
    isLoading: Boolean,
    onConversationClick: (Conversation) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onRefresh: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.messages_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = onNewConversation, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.messages_new), tint = Teal)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = TextSecondary)
                }
            }
        }

        Divider(color = DividerColor, thickness = 0.5.dp)

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(32.dp))
            }
        } else if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.messages_no_conversations),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(
                    items = conversations,
                    key = { _, it -> it.conversationId }
                ) { index, conversation ->
                    Box(modifier = Modifier.staggerIn(index)) {
                        ConversationItem(
                            conversation = conversation,
                            isSelected = conversation.conversationId == selectedId,
                            onClick = { onConversationClick(conversation) },
                            onDelete = { onDeleteConversation(conversation.conversationId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        DeleteConversationDialog(
            otherName = conversation.parentName,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .background(
                if (isSelected) TealSurface else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.messages_delete_chat), color = ErrorRed) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = ErrorRed
                    )
                },
                onClick = {
                    menuExpanded = false
                    confirmDelete = true
                }
            )
        }

        // Avatar
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (conversation.parentProfilePic.isNotEmpty()) {
                AsyncImage(
                    model = conversation.parentProfilePic,
                    contentDescription = conversation.parentName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = conversation.parentName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Teal,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.parentName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = conversation.lastMessageTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }

            Text(
                text = "${conversation.studentName} (${conversation.studentClass})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp
                )

                if (conversation.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Teal),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (conversation.unreadCount > 9) "9+" else conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = SurfaceDark,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(
    conversation: Conversation,
    messages: List<ChatMessage>,
    messageInput: String,
    isLoadingChat: Boolean,
    isSending: Boolean,
    onMessageInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        DeleteConversationDialog(
            otherName = conversation.parentName,
            onConfirm = {
                confirmDelete = false
                onDeleteConversation(conversation.conversationId)
            },
            onDismiss = { confirmDelete = false }
        )
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .glassCard()
    ) {
        // Chat header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark.copy(alpha = 0.5f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Teal.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.parentName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Teal,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.parentName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.messages_parent_of, conversation.studentName),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }

            // Delete chat (per-user)
            IconButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_chat),
                    tint = TextSecondary
                )
            }
        }

        Divider(color = DividerColor, thickness = 0.5.dp)

        // Messages list
        if (isLoadingChat) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.messageId }) { message ->
                    MessageBubble(message = message)
                }
            }
        }

        Divider(color = DividerColor, thickness = 0.5.dp)

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = onMessageInputChange,
                placeholder = {
                    Text(
                        stringResource(R.string.messages_input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Teal,
                    focusedBorderColor = Teal,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = Glass.copy(alpha = 0.2f),
                    unfocusedContainerColor = Glass.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSendMessage,
                enabled = messageInput.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (messageInput.isNotBlank() && !isSending) Teal
                        else Teal.copy(alpha = 0.3f)
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SurfaceDark,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = SurfaceDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isTeacher = message.isFromTeacher

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isTeacher) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isTeacher) 16.dp else 4.dp,
                        bottomEnd = if (isTeacher) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isTeacher) Teal.copy(alpha = 0.2f) else Glass
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (!isTeacher) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )

                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  NEW CONVERSATION DIALOG
// ══════════════════════════════════════════════════════════════════

@Composable
private fun NewConversationDialog(
    students: List<StudentPick>,
    isLoadingStudents: Boolean,
    searchQuery: String,
    selectedStudent: StudentPick?,
    messageInput: String,
    isCreating: Boolean,
    onSearchChange: (String) -> Unit,
    onStudentSelect: (StudentPick) -> Unit,
    onClearStudent: () -> Unit,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedStudent == null)
                            stringResource(R.string.messages_new)
                        else
                            stringResource(R.string.messages_compose_to, selectedStudent.name),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedStudent == null) {
                    // ── Step 1: Pick a student ──
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.messages_search_placeholder), color = TextTertiary) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    ) {
                        when {
                            isLoadingStudents -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Teal, modifier = Modifier.size(32.dp))
                                }
                            }
                            students.isEmpty() -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.messages_no_students), color = TextTertiary)
                                }
                            }
                            else -> {
                                val q = searchQuery.trim().lowercase()
                                val filtered = if (q.isEmpty()) students else students.filter {
                                    it.name.lowercase().contains(q) ||
                                    it.fatherName.lowercase().contains(q) ||
                                    it.className.lowercase().contains(q) ||
                                    it.section.lowercase().contains(q)
                                }
                                if (filtered.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.messages_no_matches), color = TextTertiary)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filtered, key = { it.studentId }) { student ->
                                            StudentRow(student = student, onClick = { onStudentSelect(student) })
                                            Divider(color = DividerColor, thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ── Step 2: Compose message ──
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TealSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Teal),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedStudent.name.take(1).uppercase(),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedStudent.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Class ${selectedStudent.className} ${selectedStudent.section}" +
                                            if (selectedStudent.fatherName.isNotBlank()) "  ·  Parent: ${selectedStudent.fatherName}" else "",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = onClearStudent) {
                                Text(stringResource(R.string.action_change), color = Teal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = onMessageChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text(stringResource(R.string.messages_type_your_message), color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onClose, enabled = !isCreating) {
                            Text(stringResource(R.string.action_cancel), color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSend,
                            enabled = !isCreating && messageInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_sending))
                            } else {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_send))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentRow(student: StudentPick, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TealSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = student.name.take(1).uppercase().ifBlank { "?" },
                color = Teal,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append("Class ${student.className}")
                    if (student.section.isNotBlank()) append(" ${student.section}")
                    if (student.fatherName.isNotBlank()) append("  ·  ${student.fatherName}")
                },
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeleteConversationDialog(
    otherName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val errorRed = ErrorRed
    val resolvedName = otherName.ifBlank { stringResource(R.string.messages_delete_default_name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.messages_delete_chat), color = TextPrimary) },
        text = {
            Text(
                stringResource(R.string.messages_delete_confirm, resolvedName),
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = errorRed, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
        containerColor = BgEnd
    )
}
