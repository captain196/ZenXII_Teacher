package com.schoolsync.teacher.ui.leave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.ErrorRedSurface
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.InfoBlue
import com.schoolsync.teacher.ui.theme.InfoBlueSurface
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.SuccessGreenSurface
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.WarningAmberSurface
import com.schoolsync.teacher.ui.theme.glassCard
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LeaveScreen(
    viewModel: LeaveViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var successBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LeaveEvent.SubmitSuccess -> {
                    successBanner = event.message
                    errorBanner = null
                    kotlinx.coroutines.delay(3000)
                    successBanner = null
                }
                is LeaveEvent.SubmitError -> {
                    errorBanner = event.message
                    successBanner = null
                    kotlinx.coroutines.delay(4000)
                    errorBanner = null
                }
            }
        }
    }

    GradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { },
            floatingActionButton = {
                // Only show "Apply Leave" FAB on the My Leave tab (tab 0)
                if (state.selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::showApplyDialog,
                        containerColor = Teal,
                        contentColor = BgStart,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Leave", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Success / Error banner
                successBanner?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuccessGreen.copy(alpha = 0.12f))
                            .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.EventAvailable, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Leave Submitted", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen)
                            Text(msg, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
                errorBanner?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ErrorRed.copy(alpha = 0.12f))
                            .border(1.dp, ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Close, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Error", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ErrorRed)
                            Text(msg, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EventAvailable,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Leave Management",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                    }
                }

                // Phase 10e: Tab bar — My Leave / Student Leave
                if (state.isClassTeacher) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("My Leave" to 0, "Student Leave" to 1).forEach { (label, idx) ->
                            val selected = state.selectedTab == idx
                            val pendingCount = if (idx == 1) state.studentLeaves.count { it.status == "pending" } else 0
                            Button(
                                onClick = { viewModel.selectTab(idx) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Teal else Glass,
                                    contentColor = if (selected) BgStart else TextSecondary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                if (idx == 1 && pendingCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) BgStart.copy(alpha = 0.3f) else WarningAmber),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$pendingCount",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) BgStart else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal)
                    }
                } else if (state.selectedTab == 1 && state.isClassTeacher) {
                    // ── Student Leave Tab ──
                    StudentLeaveContent(
                        leaves = state.studentLeaves,
                        isLoading = state.isLoadingStudentLeaves,
                        processingId = state.processingLeaveId,
                        onApprove = viewModel::approveStudentLeave,
                        onReject = viewModel::rejectStudentLeave,
                        onRefresh = viewModel::loadStudentLeaves
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Leave balance — title fixed, cards scrollable
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "Leave Balance",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                          Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                          ) {
                            if (state.balances.isEmpty()) {
                                // No leave types configured yet — show setup message
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Glass)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Leave balances not configured yet.\nContact your admin to set up leave types.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                state.balances.forEach { balance ->
                                    LeaveBalanceCard(balance = balance)
                                }
                            }
                          } // end scrollable cards column
                        } // end left column

                        // Right: Leave history
                        Column(
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "Leave History",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.leaveHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .glassCard()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Filled.CalendarMonth,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No leave records",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextTertiary
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(state.leaveHistory, key = { it.requestId }) { request ->
                                        LeaveHistoryCard(request = request)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apply leave dialog
        if (state.showApplyDialog) {
            ApplyLeaveDialog(
                state = state,
                onDismiss = viewModel::hideApplyDialog,
                onTypeChange = viewModel::setLeaveType,
                onStartDateChange = viewModel::setStartDate,
                onEndDateChange = viewModel::setEndDate,
                onReasonChange = viewModel::setReason,
                onSubmit = viewModel::submitLeaveRequest
            )
        }
    }
}

@Composable
private fun LeaveBalanceCard(balance: LeaveBalance) {
    val (color, surfaceColor) = when (balance.type.lowercase()) {
        "casual" -> Teal to TealSurface
        "sick" -> WarningAmber to WarningAmberSurface
        "earned" -> InfoBlue to InfoBlueSurface
        else -> TextSecondary to Glass
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 12.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = balance.type.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = balance.type,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            // Progress bar
            val progress = if (balance.total > 0) balance.used.toFloat() / balance.total else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Glass)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Used: ${balance.used}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
                Text(
                    text = "Remaining: ${balance.remaining}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
                Text(
                    text = "Total: ${balance.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LeaveHistoryCard(request: LeaveRequest) {
    val (statusColor, statusBg) = when (request.status) {
        LeaveStatus.PENDING -> WarningAmber to WarningAmberSurface
        LeaveStatus.APPROVED -> SuccessGreen to SuccessGreenSurface
        LeaveStatus.REJECTED -> ErrorRed to ErrorRedSurface
        LeaveStatus.CANCELLED -> TextTertiary to Glass
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 12.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.type,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = request.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${request.startDate} - ${request.endDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "${request.days} day(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            if (request.reason.isNotEmpty()) {
                Text(
                    text = request.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 11.sp
                )
            }

            if (request.remarks.isNotEmpty()) {
                Text(
                    text = "Remarks: ${request.remarks}",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ApplyLeaveDialog(
    state: LeaveUiState,
    onDismiss: () -> Unit,
    onTypeChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Apply for Leave",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Leave type selector
                Column {
                    Text(
                        "Leave Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Box {
                        OutlinedButton(
                            onClick = { typeDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = SolidColor(GlassBorder)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = state.applyLeaveType,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = typeDropdownExpanded,
                            onDismissRequest = { typeDropdownExpanded = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            state.leaveTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = TextPrimary) },
                                    onClick = {
                                        onTypeChange(type)
                                        typeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Date fields with click-to-pick
                var showStartPicker by remember { mutableStateOf(false) }
                var showEndPicker by remember { mutableStateOf(false) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (state.applyStartDate.isNotBlank()) {
                            try { java.time.LocalDate.parse(state.applyStartDate).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) }
                            catch (_: Exception) { state.applyStartDate }
                        } else "",
                        onValueChange = {},
                        label = { Text("Start Date") },
                        placeholder = { Text("Tap to select") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true,
                        colors = leaveTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { showStartPicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, "Pick date", tint = Teal)
                            }
                        },
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also {
                            LaunchedEffect(it) {
                                it.interactions.collect { interaction ->
                                    if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                        showStartPicker = true
                                    }
                                }
                            }
                        }
                    )
                    OutlinedTextField(
                        value = if (state.applyEndDate.isNotBlank()) {
                            try { java.time.LocalDate.parse(state.applyEndDate).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) }
                            catch (_: Exception) { state.applyEndDate }
                        } else "",
                        onValueChange = {},
                        label = { Text("End Date") },
                        placeholder = { Text("Tap to select") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true,
                        colors = leaveTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { showEndPicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, "Pick date", tint = Teal)
                            }
                        },
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also {
                            LaunchedEffect(it) {
                                it.interactions.collect { interaction ->
                                    if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                        showEndPicker = true
                                    }
                                }
                            }
                        }
                    )
                }

                // Date picker dialogs
                if (showStartPicker) {
                    android.app.DatePickerDialog(
                        androidx.compose.ui.platform.LocalContext.current,
                        { _, y, m, d ->
                            onStartDateChange(String.format("%04d-%02d-%02d", y, m + 1, d))
                            showStartPicker = false
                        },
                        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                        java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                    ).also { dialog ->
                        dialog.datePicker.minDate = System.currentTimeMillis()
                        dialog.setOnDismissListener { showStartPicker = false }
                        dialog.show()
                    }
                }
                if (showEndPicker) {
                    val startMs = try {
                        java.time.LocalDate.parse(state.applyStartDate)
                            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) { System.currentTimeMillis() }

                    android.app.DatePickerDialog(
                        androidx.compose.ui.platform.LocalContext.current,
                        { _, y, m, d ->
                            onEndDateChange(String.format("%04d-%02d-%02d", y, m + 1, d))
                            showEndPicker = false
                        },
                        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                        java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
                        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                    ).also { dialog ->
                        dialog.datePicker.minDate = startMs
                        dialog.setOnDismissListener { showEndPicker = false }
                        dialog.show()
                    }
                }

                // Reason
                OutlinedTextField(
                    value = state.applyReason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason") },
                    placeholder = { Text("Enter reason for leave") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    colors = leaveTextFieldColors(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = BgStart
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BgStart,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Submit", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun leaveTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Teal,
    focusedBorderColor = Teal,
    unfocusedBorderColor = GlassBorder,
    focusedLabelColor = Teal,
    unfocusedLabelColor = TextTertiary,
    focusedContainerColor = Glass.copy(alpha = 0.2f),
    unfocusedContainerColor = Glass.copy(alpha = 0.1f),
    focusedPlaceholderColor = TextTertiary,
    unfocusedPlaceholderColor = TextTertiary
)

// ═══════════════════════════════════════════════════════════════
// Phase 10e: Student Leave Content — shown when "Student Leave"
// tab is selected. Only visible to class teachers.
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StudentLeaveContent(
    leaves: List<com.schoolsync.teacher.data.model.firestore.LeaveApplicationDoc>,
    isLoading: Boolean,
    processingId: String?,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Teal)
        }
        return
    }

    val pending = leaves.filter { it.status == "pending" }
    val processed = leaves.filter { it.status != "pending" }

    if (leaves.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .glassCard()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.EventAvailable,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("No student leave requests", color = TextTertiary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Leave applications from parents in your class will appear here",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (pending.isNotEmpty()) {
            item {
                Text(
                    "Pending Requests (${pending.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = WarningAmber,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(pending, key = { it.id }) { leave ->
                StudentLeaveCard(
                    leave = leave,
                    isProcessing = processingId == leave.id,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }
        if (processed.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Processed (${processed.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(processed, key = { it.id }) { leave ->
                StudentLeaveCard(
                    leave = leave,
                    isProcessing = false,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }
    }
}

@Composable
private fun StudentLeaveCard(
    leave: com.schoolsync.teacher.data.model.firestore.LeaveApplicationDoc,
    isProcessing: Boolean,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit
) {
    var remarks by remember { mutableStateOf("") }
    val isPending = leave.status == "pending"

    val (statusColor, statusBg) = when (leave.status) {
        "approved" -> SuccessGreen to SuccessGreenSurface
        "rejected" -> ErrorRed to ErrorRedSurface
        "cancelled" -> TextTertiary to Glass
        else -> WarningAmber to WarningAmberSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp)
            .padding(16.dp)
    ) {
        // Top: name + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                val initials = leave.applicantName.split(" ").take(2)
                    .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Teal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials.ifEmpty { "?" }, color = BgStart, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        leave.applicantName.ifEmpty { leave.applicantId },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        "${leave.className} / ${leave.section}",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    leave.status.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Meta row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Type", fontSize = 10.sp, color = TextTertiary)
                Text(leave.leaveType, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text("From", fontSize = 10.sp, color = TextTertiary)
                Text(leave.startDate, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
            Column {
                Text("To", fontSize = 10.sp, color = TextTertiary)
                Text(leave.endDate, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
            Column {
                Text("Days", fontSize = 10.sp, color = TextTertiary)
                Text("${leave.numberOfDays}", fontSize = 13.sp, color = Teal, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Reason
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Glass.copy(alpha = 0.3f))
                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                leave.reason.ifEmpty { "No reason provided" },
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Approved/Rejected info
        if (leave.approvedBy.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Processed by ${leave.approvedBy}" + if (leave.remarks.isNotBlank()) " — \"${leave.remarks}\"" else "",
                fontSize = 11.sp,
                color = TextTertiary
            )
        }

        // Actions (pending only)
        if (isPending) {
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = DividerColor)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks", fontSize = 12.sp) },
                placeholder = { Text("Optional for approve, required for reject", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal,
                    unfocusedBorderColor = GlassBorder,
                    focusedLabelColor = Teal,
                    unfocusedLabelColor = TextTertiary,
                    cursorColor = Teal,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onApprove(leave.id, remarks) },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Approve", fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = { onReject(leave.id, remarks) },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reject", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
