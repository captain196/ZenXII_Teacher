package com.schoolsync.teacher.ui.attendance

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.theme.AttendanceAbsent
import com.schoolsync.teacher.ui.theme.AttendanceHoliday
import com.schoolsync.teacher.ui.theme.AttendanceLeave
import com.schoolsync.teacher.ui.theme.AttendancePresent
import com.schoolsync.teacher.ui.theme.AttendanceTardy
import com.schoolsync.teacher.ui.theme.AttendanceVacation
import com.schoolsync.teacher.ui.theme.BgStart
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
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.WarningAmberSurface
import com.schoolsync.teacher.ui.theme.glassCard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AttendanceEvent.SaveSuccess -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is AttendanceEvent.SaveError -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
            }
        }
    }

    GradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Glass,
                        contentColor = TextPrimary,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            floatingActionButton = {
                if (state.isClassTeacher && state.hasUnsavedChanges && !state.isSaving) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::saveAttendance,
                        containerColor = Teal,
                        contentColor = BgStart,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Attendance", fontWeight = FontWeight.SemiBold)
                    }
                } else if (state.isSaving) {
                    ExtendedFloatingActionButton(
                        onClick = { },
                        containerColor = Teal.copy(alpha = 0.7f),
                        contentColor = BgStart,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BgStart,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top bar with class selector, month navigation, bulk actions
                AttendanceTopBar(
                    state = state,
                    onClassSelected = viewModel::selectClass,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onMarkAllPresent = viewModel::markAllPresentToday,
                    onMarkAllAbsent = viewModel::markAllAbsentToday,
                    onRefresh = viewModel::refresh
                )

                // Class teacher permission warning
                if (!state.isClassTeacher && state.selectedClass != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(
                                WarningAmberSurface,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                WarningAmber.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Only class teachers can mark attendance. You are viewing in read-only mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Attendance spreadsheet grid
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Teal)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Loading attendance...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else if (state.students.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.PersonOff,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No students found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "Select a class to view attendance",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                } else {
                    AttendanceGrid(
                        state = state,
                        onCellClick = viewModel::cycleStatus
                    )
                }

                // Legend
                if (state.students.isNotEmpty()) {
                    AttendanceLegend()
                }
            }
        }

        // Error dialog
        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("Error", color = TextPrimary) },
                text = { Text(error, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) {
                        Text("OK", color = Teal)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Phase 10f: Tardy arrival time dialog
        if (state.showTardyDialog) {
            var timeInput by remember { mutableStateOf(
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
            ) }

            AlertDialog(
                onDismissRequest = viewModel::dismissTardyDialog,
                title = { Text("Arrival Time", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter the student's arrival time (HH:mm, 24-hour):",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = timeInput,
                            onValueChange = { if (it.length <= 5) timeInput = it },
                            placeholder = { Text("08:47") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = Teal,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // TODO: store the time in lateTimes when writing summary
                        viewModel.dismissTardyDialog()
                    }) {
                        Text("OK", color = Teal, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissTardyDialog) {
                        Text("Skip", color = TextTertiary)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun AttendanceTopBar(
    state: AttendanceUiState,
    onClassSelected: (ClassSection) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMarkAllPresent: () -> Unit,
    onMarkAllAbsent: () -> Unit,
    onRefresh: () -> Unit
) {
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var showBulkActions by remember { mutableStateOf(false) }

    val monthYear = remember(state.selectedMonth, state.selectedYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, state.selectedMonth)
            set(Calendar.YEAR, state.selectedYear)
        }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Class selector
        Box {
            OutlinedButton(
                onClick = { classDropdownExpanded = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(GlassBorder)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = state.selectedClass?.displayName ?: "Select Class",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = classDropdownExpanded,
                onDismissRequest = { classDropdownExpanded = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                state.availableClasses.forEach { classSection ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                classSection.displayName,
                                color = if (classSection == state.selectedClass) Teal else TextPrimary
                            )
                        },
                        onClick = {
                            onClassSelected(classSection)
                            classDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Month navigation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = TextSecondary
                )
            }
            Text(
                text = monthYear,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Next month",
                    tint = TextSecondary
                )
            }
        }

        // Bulk actions + Refresh
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mark All Present (only for class teachers)
            OutlinedButton(
                onClick = onMarkAllPresent,
                enabled = state.isClassTeacher,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AttendancePresent,
                    disabledContentColor = TextTertiary.copy(alpha = 0.4f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (state.isClassTeacher) AttendancePresent.copy(alpha = 0.4f)
                        else TextTertiary.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("All P", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Mark All Absent (only for class teachers)
            OutlinedButton(
                onClick = onMarkAllAbsent,
                enabled = state.isClassTeacher,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AttendanceAbsent,
                    disabledContentColor = TextTertiary.copy(alpha = 0.4f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (state.isClassTeacher) AttendanceAbsent.copy(alpha = 0.4f)
                        else TextTertiary.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("All A", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun AttendanceGrid(
    state: AttendanceUiState,
    onCellClick: (studentId: String, day: Int) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val rollWidth = 40.dp
    val nameWidth = 120.dp
    val dayCellSize = 36.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glassCard(cornerRadius = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Fixed columns: Roll No + Name
            Column(modifier = Modifier.fillMaxHeight()) {
                // Header row for fixed columns
                Row(
                    modifier = Modifier
                        .background(SurfaceDark)
                        .border(0.5.dp, DividerColor)
                ) {
                    // Roll No header
                    Box(
                        modifier = Modifier
                            .width(rollWidth)
                            .height(dayCellSize)
                            .border(0.5.dp, DividerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    // Name header
                    Box(
                        modifier = Modifier
                            .width(nameWidth)
                            .height(dayCellSize)
                            .border(0.5.dp, DividerColor),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Student",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                // Student rows - fixed part
                LazyColumn {
                    items(state.students, key = { it.studentId }) { student ->
                        Row(
                            modifier = Modifier
                                .border(0.5.dp, DividerColor.copy(alpha = 0.5f))
                        ) {
                            // Roll number
                            Box(
                                modifier = Modifier
                                    .width(rollWidth)
                                    .height(dayCellSize)
                                    .background(Glass.copy(alpha = 0.15f))
                                    .border(0.5.dp, DividerColor.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = student.rollNo.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                            // Student name
                            Box(
                                modifier = Modifier
                                    .width(nameWidth)
                                    .height(dayCellSize)
                                    .background(Glass.copy(alpha = 0.1f))
                                    .border(0.5.dp, DividerColor.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = student.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 6.dp, end = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Scrollable day columns (1 to daysInMonth)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Day number headers
                Row(
                    modifier = Modifier
                        .background(SurfaceDark)
                        .border(0.5.dp, DividerColor)
                ) {
                    for (day in 1..state.daysInMonth) {
                        val isToday = day == state.todayDay
                        Box(
                            modifier = Modifier
                                .width(dayCellSize)
                                .height(dayCellSize)
                                .background(
                                    if (isToday) Teal.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .border(0.5.dp, if (isToday) Teal.copy(alpha = 0.3f) else DividerColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isToday) Teal else TextSecondary,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp
                                )
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(Teal)
                                    )
                                }
                            }
                        }
                    }
                }

                // Student attendance cells
                LazyColumn {
                    items(state.students, key = { it.studentId }) { student ->
                        Row {
                            for (day in 1..state.daysInMonth) {
                                val status = student.dayStatuses[day]
                                val isToday = day == state.todayDay

                                AttendanceCell(
                                    status = status,
                                    isToday = isToday,
                                    size = dayCellSize,
                                    onClick = { onCellClick(student.studentId, day) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceCell(
    status: AttendanceStatus?,
    isToday: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val statusColor = status?.let { getStatusColor(it) } ?: Color.Transparent
    val statusBg = status?.let { getStatusColor(it).copy(alpha = 0.12f) } ?: Color.Transparent

    val bgColor by animateColorAsState(
        targetValue = when {
            status != null -> statusBg
            isToday -> TealSurface.copy(alpha = 0.3f)
            else -> Glass.copy(alpha = 0.05f)
        },
        animationSpec = tween(150),
        label = "cellBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isToday && status != null -> statusColor.copy(alpha = 0.4f)
            isToday -> Teal.copy(alpha = 0.2f)
            status != null -> statusColor.copy(alpha = 0.15f)
            else -> DividerColor.copy(alpha = 0.3f)
        },
        animationSpec = tween(150),
        label = "cellBorder"
    )

    Box(
        modifier = Modifier
            .width(size)
            .height(size)
            .background(bgColor)
            .border(0.5.dp, borderColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (status != null) {
            Text(
                text = status.code,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "-",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary.copy(alpha = 0.3f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AttendanceLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttendanceStatus.entries.forEach { status ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(getStatusColor(status))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${status.code}=${status.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun getStatusColor(status: AttendanceStatus): Color {
    return when (status) {
        AttendanceStatus.PRESENT -> AttendancePresent
        AttendanceStatus.ABSENT -> AttendanceAbsent
        AttendanceStatus.LEAVE -> AttendanceLeave
        AttendanceStatus.HOLIDAY -> AttendanceHoliday
        AttendanceStatus.TARDY -> AttendanceTardy
        AttendanceStatus.VACATION -> AttendanceVacation
    }
}
