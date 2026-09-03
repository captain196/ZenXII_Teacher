package com.schoolsync.teacher.ui.leave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R
import androidx.compose.ui.platform.LocalContext
import com.schoolsync.teacher.util.displayLabel
import androidx.compose.ui.text.style.TextAlign
import com.schoolsync.teacher.util.localizedString
import com.schoolsync.teacher.util.localizedPlural

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

    // P2 — Refresh on screen resume so the balance reflects admin's
    // approval/rejection without requiring the user to pull-to-refresh.
    // MEDIUM #9: throttled in the VM so a brief background trip doesn't fire a
    // full multi-read reload on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { },
            floatingActionButton = {
                // Only show stringResource(R.string.dash_apply_leave) FAB on the My Leave tab (tab 0)
                if (state.selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::showApplyDialog,
                        containerColor = Teal,
                        contentColor = BgStart,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dash_apply_leave), fontWeight = FontWeight.SemiBold)
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
                            Text(stringResource(R.string.leave_submitted), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen)
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
                            Text(stringResource(R.string.common_error), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ErrorRed)
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
                            text = stringResource(R.string.leave_management),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = TextSecondary)
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
                        listOf(stringResource(R.string.leave_tab_my) to 0,
                               stringResource(R.string.leave_tab_student) to 1).forEach { (label, idx) ->
                            val selected = state.selectedTab == idx
                            val pendingCount = if (idx == 1) state.studentLeaves.count { it.status.equals("pending", ignoreCase = true) } else 0
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
                        error = state.studentLeavesError,
                        processingId = state.processingLeaveId,
                        onApprove = viewModel::approveStudentLeave,
                        onReject = viewModel::rejectStudentLeave,
                        onRefresh = viewModel::loadStudentLeaves
                    )
                } else {
                    // MEDIUM #8: responsive — side-by-side on wide screens
                    // (tablet / landscape), stacked single-column on portrait
                    // phones so the balance panel no longer clips history.
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        val isNarrow = maxWidth < 600.dp
                        val balancePanelMaxHeight = maxHeight * 0.4f
                        if (isNarrow) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LeaveBalancePanel(
                                    state = state,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = balancePanelMaxHeight),
                                    onRetry = viewModel::loadLeaveData
                                )
                                LeaveHistoryPanel(
                                    state = state,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    onRetry = viewModel::loadLeaveData,
                                    onCancel = viewModel::cancelLeave
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LeaveBalancePanel(
                                    state = state,
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight(),
                                    onRetry = viewModel::loadLeaveData
                                )
                                LeaveHistoryPanel(
                                    state = state,
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .fillMaxHeight(),
                                    onRetry = viewModel::loadLeaveData,
                                    onCancel = viewModel::cancelLeave
                                )
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
                onHalfDayChange = viewModel::setApplyHalfDay,
                onHalfDayPeriodChange = viewModel::setApplyHalfDayPeriod,
                onSubmit = viewModel::submitLeaveRequest
            )
        }
    }
}

// MEDIUM #5: render a Double day-count cleanly — "0.5", "1", "3" (no ".0").
private fun formatDays(days: Double): String =
    if (days == days.toLong().toDouble()) days.toLong().toString() else days.toString()

// "0.5 day" / "1 day" / "3 days" — takes a Context because the day/days
// distinction is a plural rule, and half-days mean the quantity is not
// always an integer, so the formatted value is passed as a String arg.
private fun formatDayLabel(ctx: android.content.Context, days: Double): String =
    ctx.resources.getQuantityString(
        R.plurals.leave_days_count, if (days == 1.0) 1 else 2, formatDays(days))

// H11: reusable error+retry box, distinct from a genuine empty state.
@Composable
private fun LeaveErrorBox(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.gal_load_error),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Teal)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

@Composable
private fun LeaveBalancePanel(
    state: LeaveUiState,
    modifier: Modifier,
    onRetry: () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.leave_balance),
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
            when {
                // H11: distinguish load failure from a genuine empty config.
                state.balancesError != null -> LeaveErrorBox(state.balancesError, onRetry)
                state.balances.isEmpty() -> {
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
                            text = stringResource(R.string.leave_no_balances),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> state.balances.forEach { balance -> LeaveBalanceCard(balance = balance) }
            }
        }
    }
}

@Composable
private fun LeaveHistoryPanel(
    state: LeaveUiState,
    modifier: Modifier,
    onRetry: () -> Unit,
    onCancel: (String) -> Unit
) {
    // Tapping a history card opens a detail sheet (details + cancel action live
    // there now, so the card itself carries no inline button that the floating
    // stringResource(R.string.dash_apply_leave) FAB could overlap).
    var detailLeave by remember { mutableStateOf<LeaveRequest?>(null) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.leave_history),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        when {
            // H11: history-load error was previously swallowed into an empty state.
            state.error != null && state.leaveHistory.isEmpty() -> LeaveErrorBox(state.error, onRetry)
            state.leaveHistory.isEmpty() -> {
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
                            text = stringResource(R.string.leave_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // Bottom inset clears the floating stringResource(R.string.dash_apply_leave) FAB so the last
                    // card is always fully tappable and never covered.
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(state.leaveHistory, key = { it.requestId }) { request ->
                        LeaveHistoryCard(request = request, onClick = { detailLeave = request })
                    }
                }
            }
        }
    }

    detailLeave?.let { req ->
        LeaveDetailSheet(
            request = req,
            onDismiss = { detailLeave = null },
            onCancel = onCancel
        )
    }
}

@Composable
private fun LeaveBalanceCard(balance: LeaveBalance) {
    val lower = balance.type.lowercase()
    val (color, surfaceColor) = when {
        lower.contains("casual") -> Teal to TealSurface
        lower.contains("sick") -> WarningAmber to WarningAmberSurface
        lower.contains("earned") -> InfoBlue to InfoBlueSurface
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
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Prominent remaining-of-total summary.
            Text(
                text = stringResource(R.string.leave_remaining_of, formatDays(balance.remaining), formatDays(balance.total)),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Progress bar — fraction of the total that has been used.
            val progress = if (balance.total > 0) (balance.used / balance.total).toFloat().coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
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

            // Balance line. Two fixes, both found on device: SpaceBetween with
            // unconstrained Texts ran the labels together, and a weight(1f)
            // half-and-half split then clipped the Tamil mid-word. FlowRow
            // gives each label its natural width and wraps to a second line.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    // No weight(1f): "Used 2" is six characters, but
                    // "2 பயன்படுத்தப்பட்டது" is twenty-five, and forcing it into
                    // half the row made it wrap to three lines and clip
                    // mid-word. Each label takes the width it needs and the
                    // FlowRow moves the second one to its own line when they
                    // no longer fit side by side.
                    text = stringResource(R.string.leave_used_fmt, formatDays(balance.used)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
                Text(
                    text = if (balance.carried > 0)
                        stringResource(R.string.leave_allotted_carried,
                            formatDays(balance.allocated), formatDays(balance.carried))
                    else stringResource(R.string.leave_allotted, formatDays(balance.allocated)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LeaveHistoryCard(
    request: LeaveRequest,
    onClick: () -> Unit
) {
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
            .clickable(onClickLabel = stringResource(R.string.leave_view_details_cd)) { onClick() }
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = request.status.displayLabel(LocalContext.current),
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
                    text = formatDayLabel(LocalContext.current, request.days),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.leave_view_details),
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// Detail view for a single leave, opened by tapping a history card. Full
// details + the owner-cancel action live here (a scrollable bottom sheet that
// fits small screens & landscape), so the history cards stay clean and nothing
// collides with the floating stringResource(R.string.dash_apply_leave) FAB.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveDetailSheet(
    request: LeaveRequest,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCancelConfirm by remember { mutableStateOf(false) }
    val (statusColor, statusBg) = when (request.status) {
        LeaveStatus.PENDING -> WarningAmber to WarningAmberSurface
        LeaveStatus.APPROVED -> SuccessGreen to SuccessGreenSurface
        LeaveStatus.REJECTED -> ErrorRed to ErrorRedSurface
        LeaveStatus.CANCELLED -> TextTertiary to Glass
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.type,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = request.status.displayLabel(LocalContext.current),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LeaveDetailRow(stringResource(R.string.common_duration), "${request.startDate}  →  ${request.endDate}")
            LeaveDetailRow(stringResource(R.string.common_days), formatDayLabel(LocalContext.current, request.days))
            if (request.reason.isNotEmpty()) LeaveDetailRow(stringResource(R.string.common_reason), request.reason)
            if (request.remarks.isNotEmpty()) LeaveDetailRow(stringResource(R.string.common_remarks), request.remarks, valueColor = statusColor)

            if (request.status == LeaveStatus.PENDING) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { showCancelConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(ErrorRed.copy(alpha = 0.6f))),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.leave_cancel_request))
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.leave_cancel_q), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.leave_withdraw_confirm_fmt, request.type, request.startDate, request.endDate),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancel(request.requestId)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(stringResource(R.string.leave_yes_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(stringResource(R.string.leave_keep_it), color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun LeaveDetailRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
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
    onHalfDayChange: (Boolean) -> Unit,
    onHalfDayPeriodChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        // Tap-outside no longer dismisses — the user must explicitly hit the
        // cross icon. Back press still closes (standard Android UX) so the
        // user isn't trapped if they invoke the system back gesture.
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.leave_apply_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                // LOW #10: 48dp minimum touch target for accessibility.
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Leave type selector
                Column {
                    Text(
                        stringResource(R.string.leave_type_label),
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

                // Date fields with click-to-pick.
                // C5: rememberSaveable so a rotation while the picker flag is
                // set survives configuration change safely.
                var showStartPicker by rememberSaveable { mutableStateOf(false) }
                var showEndPicker by rememberSaveable { mutableStateOf(false) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (state.applyStartDate.isNotBlank()) {
                            try { java.time.LocalDate.parse(state.applyStartDate).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) }
                            catch (_: Exception) { state.applyStartDate }
                        } else "",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.common_start_date)) },
                        placeholder = { Text(stringResource(R.string.common_tap_to_select)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true,
                        colors = leaveTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { showStartPicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, stringResource(R.string.common_pick_date), tint = Teal)
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
                        label = { Text(stringResource(R.string.common_end_date)) },
                        placeholder = { Text(if (state.applyHalfDay) "Same as start" else stringResource(R.string.common_tap_to_select)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true,
                        enabled = !state.applyHalfDay,
                        colors = leaveTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { showEndPicker = true },
                                enabled = !state.applyHalfDay
                            ) {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    stringResource(R.string.common_pick_date),
                                    tint = if (state.applyHalfDay) TextTertiary else Teal
                                )
                            }
                        },
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also {
                            LaunchedEffect(it) {
                                it.interactions.collect { interaction ->
                                    if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release && !state.applyHalfDay) {
                                        showEndPicker = true
                                    }
                                }
                            }
                        }
                    )
                }

                // Half-day toggle + period picker.
                // When ON, the leave is a single-date 0.5-day deduction; the End
                // Date field is disabled and auto-synced to Start Date by the VM.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Glass.copy(alpha = 0.25f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.leave_half_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.leave_half_day_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = state.applyHalfDay,
                        onCheckedChange = onHalfDayChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgStart,
                            checkedTrackColor = Teal,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = Glass
                        )
                    )
                }

                if (state.applyHalfDay) {
                    var periodDropdownExpanded by remember { mutableStateOf(false) }
                    Column {
                        Text(
                            "Period",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Box {
                            OutlinedButton(
                                onClick = { periodDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(GlassBorder)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (state.applyHalfDayPeriod == "PM")  /* i18n-ignore: wire value */ stringResource(R.string.leave_afternoon) else stringResource(R.string.leave_morning),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = periodDropdownExpanded,
                                onDismissRequest = { periodDropdownExpanded = false },
                                modifier = Modifier.background(SurfaceDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.leave_morning), color = TextPrimary) },
                                    onClick = {
                                        onHalfDayPeriodChange("AM")
                                        periodDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.leave_afternoon), color = TextPrimary) },
                                    onClick = {
                                        onHalfDayPeriodChange("PM")
                                        periodDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Active-mode badge — confirms what will be submitted at a
                    // glance, especially useful since the End Date field is
                    // disabled and could otherwise look like an error.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(TealSurface)
                                .border(1.dp, Teal.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.leave_half_day_fmt, state.applyHalfDayPeriod),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }
                    }

                    // Balance-impact warning so the teacher knows the
                    // deduction before they hit Submit.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(WarningAmberSurface)
                            .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.leave_halfday_note),
                            fontSize = 12.sp,
                            color = WarningAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // C5: Compose Material3 date pickers, shown via state flags.
                // The old raw android.app.DatePickerDialog was constructed in
                // the composable body → re-shown on every recomposition and
                // leaked the Activity. These are constructed exactly once per
                // flag and dismissed properly.
                if (showStartPicker) {
                    LeaveDatePickerDialog(
                        initialDateIso = state.applyStartDate,
                        minDateIso = java.time.LocalDate.now().toString(),
                        onDismiss = { showStartPicker = false },
                        onDateSelected = { iso ->
                            onStartDateChange(iso)
                            showStartPicker = false
                        }
                    )
                }
                // Single-date invariant for half-day: never show the End picker
                // while half-day is on.
                if (showEndPicker && state.applyHalfDay) {
                    showEndPicker = false
                }
                if (showEndPicker && !state.applyHalfDay) {
                    LeaveDatePickerDialog(
                        initialDateIso = state.applyEndDate.ifBlank { state.applyStartDate },
                        minDateIso = state.applyStartDate.ifBlank { java.time.LocalDate.now().toString() },
                        onDismiss = { showEndPicker = false },
                        onDateSelected = { iso ->
                            onEndDateChange(iso)
                            showEndPicker = false
                        }
                    )
                }

                // Reason
                OutlinedTextField(
                    value = state.applyReason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(R.string.common_reason)) },
                    placeholder = { Text(stringResource(R.string.leave_enter_reason)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    colors = leaveTextFieldColors(),
                    shape = RoundedCornerShape(8.dp)
                )

                // Submit preview — single line summarising what will be sent.
                // Hidden until both dates are set so an empty preview never
                // misleads the user; updates live as they edit.
                if (state.applyStartDate.isNotBlank() && state.applyEndDate.isNotBlank()) {
                    val previewText = buildLeavePreview(LocalContext.current, state)
                    if (previewText.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(InfoBlueSurface)
                                .border(1.dp, InfoBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = previewText,
                                fontSize = 12.sp,
                                color = InfoBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
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
                // Hoisted: Modifier.semantics {} is not a composable scope.
                val submittingCd = stringResource(R.string.leave_submitting)
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics {
                                contentDescription = submittingCd
                                liveRegion = LiveRegionMode.Polite
                            },
                        color = BgStart,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.common_submit), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

// Build the human-readable preview line shown above the Submit button.
// Returns "" if dates can't be parsed so the caller can hide the row.
private fun buildLeavePreview(ctx: android.content.Context, state: LeaveUiState): String {
    return try {
        val start = java.time.LocalDate.parse(state.applyStartDate)
        val end = java.time.LocalDate.parse(state.applyEndDate)
        val dayMonth = java.time.format.DateTimeFormatter.ofPattern("d MMM")
        val typeLabel = state.applyLeaveType.ifBlank { ctx.localizedString(R.string.leave_type_default) }

        if (state.applyHalfDay) {
            val periodWord = ctx.localizedString(
                if (state.applyHalfDayPeriod == "PM") R.string.leave_half_afternoon  // i18n-ignore: wire value
                else R.string.leave_half_morning
            )
            ctx.localizedString(R.string.leave_applying_half_fmt, typeLabel, periodWord, start.format(dayMonth))
        } else {
            val days = (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1)
                .toInt().coerceAtLeast(1)
            if (days == 1) {
                ctx.localizedString(R.string.leave_applying_one_fmt, typeLabel, start.format(dayMonth))
            } else {
                ctx.localizedPlural(
                    R.plurals.leave_applying_range_fmt, days,
                    typeLabel, start.format(dayMonth), end.format(dayMonth), days)
            }
        }
    } catch (_: Exception) {
        ""
    }
}

// C5: Compose Material3 date-picker dialog. Constructed once per show flag,
// dismissed via onDismiss. Uses UTC epoch-day math to keep the "yyyy-MM-dd"
// string stable regardless of device timezone, and gates selectable days at
// or after [minDateIso].
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveDatePickerDialog(
    initialDateIso: String,
    minDateIso: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val zone = java.time.ZoneOffset.UTC
    val minEpochDay = try {
        java.time.LocalDate.parse(minDateIso).toEpochDay()
    } catch (_: Exception) {
        java.time.LocalDate.now().toEpochDay()
    }
    val initialMillis = try {
        java.time.LocalDate.parse(initialDateIso).atStartOfDay(zone).toInstant().toEpochMilli()
    } catch (_: Exception) {
        java.time.LocalDate.ofEpochDay(minEpochDay).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp.dp

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        // On short/landscape screens start in the compact text-input mode so the
        // tall calendar grid never pushes the action buttons off-screen; portrait
        // keeps the full calendar. The mode toggle lets users switch either way.
        initialDisplayMode = if (isLandscape) DisplayMode.Input else DisplayMode.Picker,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (utcTimeMillis / 86_400_000L) >= minEpochDay
            override fun isSelectableYear(year: Int): Boolean =
                year >= java.time.LocalDate.ofEpochDay(minEpochDay).year
        }
    )

    // Custom responsive dialog: height-capped surface, the picker lives in a
    // scrollable region, and the Cancel/OK row is a sticky footer that is ALWAYS
    // visible regardless of orientation or calendar height (fixes the landscape
    // regression where the M3 DatePickerDialog pushed its buttons below the screen).
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = if (isLandscape) 40.dp else 20.dp, vertical = 20.dp)
                .fillMaxWidth(if (isLandscape) 0.85f else 1f)
                .heightIn(max = screenHeightDp - 40.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    DatePicker(
                        state = pickerState,
                        title = null,
                        showModeToggle = true,
                        colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Divider(color = GlassBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            val date = java.time.Instant.ofEpochMilli(millis)
                                .atZone(zone).toLocalDate()
                            onDateSelected(date.toString())
                        } else onDismiss()
                    }) { Text(stringResource(R.string.common_ok), color = Teal, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
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
    error: String?,
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

    // H11: distinguish a load failure from a genuine empty list.
    if (error != null && leaves.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            LeaveErrorBox(error, onRefresh)
        }
        return
    }

    // LOW #11: case-insensitive so legacy CapitalCase HR docs classify right.
    val pending = leaves.filter { it.status.equals("pending", ignoreCase = true) }
    val processed = leaves.filter { !it.status.equals("pending", ignoreCase = true) }

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
                Text(stringResource(R.string.leave_no_student_requests), color = TextTertiary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.leave_parent_empty),
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
                    stringResource(R.string.leave_pending_requests_fmt, pending.size),
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
                    stringResource(R.string.leave_processed_count_fmt, processed.size),
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
    // LOW #11: tolerate legacy CapitalCase status from HR-decided docs.
    val isPending = leave.status.equals("pending", ignoreCase = true)

    val (statusColor, statusBg) = when (leave.status.lowercase()) {
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

        // Meta row — FOUR label/value columns. Type/From/To/Days are 2-4
        // characters each in English and up to three times that in Tamil and
        // Telugu, so under SpaceBetween the four columns overflow the row.
        // FlowRow wraps them onto a second line instead of clipping.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column {
                Text(stringResource(R.string.common_type), fontSize = 10.sp, color = TextTertiary)
                Text(leave.leaveType, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text(stringResource(R.string.common_from), fontSize = 10.sp, color = TextTertiary)
                Text(leave.startDate, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
            Column {
                Text(stringResource(R.string.common_to), fontSize = 10.sp, color = TextTertiary)
                Text(leave.endDate, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
            Column {
                Text(stringResource(R.string.common_days), fontSize = 10.sp, color = TextTertiary)
                Text(formatDays(leave.numberOfDays), fontSize = 13.sp, color = Teal, fontWeight = FontWeight.Bold)
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
                leave.reason.ifEmpty { stringResource(R.string.leave_no_reason) },
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
                stringResource(R.string.leave_processed_by_fmt, leave.approvedBy) +
                    if (leave.remarks.isNotBlank()) " — \"${leave.remarks}\"" else "",
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
                label = { Text(stringResource(R.string.common_remarks), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.leave_remark_hint), fontSize = 12.sp) },
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
                    // Hoisted: Modifier.semantics {} is not a composable scope.
                    val processingCd = stringResource(R.string.leave_processing)
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .semantics {
                                    contentDescription = processingCd
                                    liveRegion = LiveRegionMode.Polite
                                },
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.leave_approve), fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = { onReject(leave.id, remarks) },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.leave_reject), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
