package com.schoolsync.teacher.ui.attendance

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HourglassEmpty
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.repository.MyCorrection
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/* ════════════════════════════════════════════════════════════════════════
   ATTENDANCE SCREEN — Today-first commercial-ERP redesign
   ────────────────────────────────────────────────────────────────────────
   Two presentation modes over the SAME ViewModel state + APIs:
     • Today   (default) → fast per-student marking for TODAY
     • History          → the original month spreadsheet (view + edit)
   No ViewModel / Repository / API / Firestore changes. Stage governance,
   the reason field, the tardy dialog and the correction flow are unchanged.
   ════════════════════════════════════════════════════════════════════════ */

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Presentation-only view switch. Today is always the default landing view.
    var showHistory by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AttendanceEvent.SaveSuccess -> snackbarHostState.showSnackbar(event.message)
                is AttendanceEvent.SaveError -> snackbarHostState.showSnackbar("Error: ${event.message}")
                is AttendanceEvent.Locked -> snackbarHostState.showSnackbar("Attendance is locked. File a correction request.")
                is AttendanceEvent.ReasonRequired -> snackbarHostState.showSnackbar("A reason (10+ chars) is required after 10:30 AM.")
                is AttendanceEvent.CorrectionSubmitted -> snackbarHostState.showSnackbar(event.message)
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
                // Sticky Save / Request-Correction FAB (same logic in both views).
                when {
                    state.isSaving -> {
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
                            Text("Saving…", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // NOTE: no auto-FAB in the locked state — a correction is filed by
                    // tapping the specific student/cell (the old FAB always opened a
                    // correction for the FIRST student, which was wrong). The locked
                    // banner + row hint tell the teacher to tap a student.
                    state.isClassTeacher && state.hasUnsavedChanges -> {
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
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    showRequests -> RequestsView(
                        state = state,
                        onBack = { showRequests = false },
                        onRefresh = viewModel::loadMyCorrections
                    )
                    showHistory -> HistoryView(
                        state = state,
                        viewModel = viewModel,
                        // Reset to the current month when returning to Today so the
                        // home view doesn't keep showing the browsed month's marks
                        // for today's day-number (the "June 10 on the home page" bug).
                        onBackToToday = {
                            showHistory = false
                            viewModel.returnToCurrentMonth()
                        }
                    )
                    else -> TodayView(
                        state = state,
                        viewModel = viewModel,
                        onOpenHistory = { showHistory = true },
                        onOpenRequests = {
                            showRequests = true
                            viewModel.loadMyCorrections()
                        }
                    )
                }
            }
        }

        // ── Dialogs (unchanged behaviour) ────────────────────────────────
        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("Error", color = TextPrimary) },
                text = { Text(error, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) { Text("OK", color = Teal) }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (state.showTardyDialog) {
            var timeInput by remember {
                mutableStateOf(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                )
            }
            AlertDialog(
                onDismissRequest = viewModel::dismissTardyDialog,
                title = { Text("Arrival Time", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    TextButton(onClick = { viewModel.confirmTardyTime(timeInput) }) {
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

        if (state.showCorrectionDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeCorrectionDialog,
                title = { Text("Request Correction", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(
                            "${state.correctionStudentName}  ·  ${state.correctionDate}",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Currently: ${state.correctionCurrentStatus.label}",
                            color = TextTertiary, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Change to:", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                AttendanceStatus.PRESENT,
                                AttendanceStatus.ABSENT,
                                AttendanceStatus.LEAVE,
                                AttendanceStatus.TARDY
                            ).forEach { st ->
                                val sel = state.correctionRequestedStatus == st
                                OutlinedButton(
                                    onClick = { viewModel.setCorrectionRequestedStatus(st) },
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .height(34.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (sel) Teal.copy(alpha = 0.2f) else Color.Transparent,
                                        contentColor = if (sel) Teal else TextSecondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(st.label, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.correctionReason,
                            onValueChange = viewModel::setCorrectionReason,
                            placeholder = { Text("Reason (10+ chars)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = Teal,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 3
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.correctionReason.trim().length} / 10+",
                            color = if (state.correctionReason.trim().length >= 10) AttendancePresent else TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::submitCorrectionRequest,
                        enabled = !state.isSubmittingCorrection && state.correctionReason.trim().length >= 10
                    ) {
                        Text(
                            if (state.isSubmittingCorrection) "Submitting…" else "Submit Request",
                            color = if (state.isSubmittingCorrection || state.correctionReason.trim().length < 10) TextTertiary else Teal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::closeCorrectionDialog,
                        enabled = !state.isSubmittingCorrection
                    ) {
                        Text("Cancel", color = TextTertiary)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Discard-unsaved-changes confirmation. Refresh / class-switch / month-switch
        // all route through the ViewModel's guardUnsaved(): when there are unsaved
        // marks it raises showDiscardDialog and stashes the navigation. Without this
        // dialog rendered, that navigation was stashed and NEVER run — so Refresh
        // silently did nothing and the stale local edit stuck on screen (looked
        // "saved" but wasn't). Confirming discards the edits and runs the reload.
        if (state.showDiscardDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDiscardChanges,
                title = { Text("Discard unsaved changes?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "You have attendance marks that haven't been saved. Continuing will discard them and reload the latest saved data.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDiscardChanges) {
                        Text("Discard & reload", color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDiscardChanges) {
                        Text("Keep editing", color = TextSecondary)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════
   TODAY VIEW — the default, marking-optimised surface
   ════════════════════════════════════════════════════════════════════════ */

@Composable
private fun TodayView(
    state: AttendanceUiState,
    viewModel: AttendanceViewModel,
    onOpenHistory: () -> Unit,
    onOpenRequests: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TodayHeader(
            state = state,
            onClassSelected = viewModel::selectClass,
            onOpenHistory = onOpenHistory,
            onOpenRequests = onOpenRequests,
            onRefresh = viewModel::refresh
        )

        // NOTE: the governance banner (incl. the S2 reason field) and the
        // read-only notice now live INSIDE TodayScrollBody's LazyColumn so they
        // scroll away with the content — keeping only the compact header pinned
        // gives the roster the full screen while marking.
        when {
            state.isLoading -> LoadingState(modifier = Modifier.weight(1f))
            state.students.isEmpty() -> EmptyState(
                modifier = Modifier.weight(1f),
                selectedClass = state.selectedClass,
                hasOtherClasses = state.availableClasses.size > 1
            )
            else -> {
                TodayScrollBody(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onAllPresent = viewModel::markAllPresentToday,
                    onAllAbsent = viewModel::markAllAbsentToday,
                    onReasonChange = viewModel::setEditReason,
                    onStudentTap = { studentId ->
                        when {
                            !state.isClassTeacher -> Unit
                            state.stage == Stage.S3_LOCKED ->
                                viewModel.openCorrectionDialog(studentId, state.todayDay)
                            else ->
                                viewModel.cycleStatus(studentId, state.todayDay)
                        }
                    },
                    // TCH-C1: long-press → mark Late for today (arrival-time dialog).
                    // Only meaningful when the teacher can mark and the run isn't
                    // locked; markTardyToday re-guards these internally too.
                    onStudentLongPress = { studentId ->
                        if (state.isClassTeacher && state.stage != Stage.S3_LOCKED) {
                            viewModel.markTardyToday(studentId)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TodayHeader(
    state: AttendanceUiState,
    onClassSelected: (ClassSection) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRequests: () -> Unit,
    onRefresh: () -> Unit
) {
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val todayShort = remember {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
            .format(Calendar.getInstance().time)
    }
    val cls = state.selectedClass

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Attendance",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = todayShort,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Top-bar actions: class selector + refresh + history, all inline.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { classDropdownExpanded = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(GlassBorder)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        modifier = Modifier.widthIn(max = 150.dp)
                    ) {
                        Text(
                            text = cls?.let { "${it.className} · ${it.section}" } ?: "Select Class",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change class", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = classDropdownExpanded,
                        onDismissRequest = { classDropdownExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        if (state.availableClasses.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No classes assigned", color = TextTertiary) },
                                onClick = { classDropdownExpanded = false }
                            )
                        } else {
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
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
                IconButton(onClick = onOpenHistory, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.DateRange, contentDescription = "History", tint = Teal)
                }
                // My correction requests — right of the History button.
                IconButton(onClick = onOpenRequests, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "My Requests", tint = Teal)
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(state: AttendanceUiState) {
    val today = state.todayDay
    val marks = remember(state.students, today) { state.students.map { it.dayStatuses[today] } }
    val present = marks.count { it == AttendanceStatus.PRESENT }
    val absent = marks.count { it == AttendanceStatus.ABSENT }
    val leave = marks.count { it == AttendanceStatus.LEAVE }
    val tardy = marks.count { it == AttendanceStatus.TARDY }
    val total = state.students.size
    val marked = marks.count { it != null }
    val remaining = (total - marked).coerceAtLeast(0)
    val fraction = if (total > 0) marked.toFloat() / total.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glassCard(cornerRadius = 16.dp)
            .padding(16.dp)
    ) {
        // Statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatPill("Present", present, AttendancePresent, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatPill("Absent", absent, AttendanceAbsent, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatPill("Leave", leave, AttendanceLeave, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatPill("Late", tardy, AttendanceTardy, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // Progress: Marked / Remaining / Total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$marked of $total marked",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (remaining == 0) "All marked ✓" else "$remaining remaining",
                color = if (remaining == 0) AttendancePresent else WarningAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(8.dp))
        // Custom progress track (no dependency on LinearProgressIndicator API surface)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Glass.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (remaining == 0) AttendancePresent else Teal)
            )
        }
    }
}

@Composable
private fun StatPill(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BulkActionsRow(
    onAllPresent: () -> Unit,
    onAllAbsent: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAllPresent,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AttendancePresent.copy(alpha = 0.18f),
                contentColor = AttendancePresent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("All Present", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onAllAbsent,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AttendanceAbsent.copy(alpha = 0.18f),
                contentColor = AttendanceAbsent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("All Absent", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TodayScrollBody(
    state: AttendanceUiState,
    onStudentTap: (studentId: String) -> Unit,
    onStudentLongPress: (studentId: String) -> Unit,
    onAllPresent: () -> Unit,
    onAllAbsent: () -> Unit,
    onReasonChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = state.todayDay
    // Everything scrolls TOGETHER — the governance/reason banner, summary/stat
    // counts and bulk actions are NOT pinned; they scroll up with the roster so
    // the student list gets the full screen as you scroll (previously the banner
    // + summary were a fixed band and only the list scrolled beneath them).
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 96.dp) // clear the FAB
    ) {
        // Governance banner (S1/S2/S3) incl. the S2 "reason" field — scrolls away.
        if (state.selectedClass != null && state.stage != Stage.UNKNOWN) {
            item(key = "stage") {
                StageBanner(
                    stage = state.stage,
                    lockedBy = state.lockedBy,
                    editReason = state.editReason,
                    onReasonChange = onReasonChange
                )
            }
        }

        if (!state.isClassTeacher && state.selectedClass != null) {
            item(key = "readonly") { ReadOnlyWarning() }
        }

        item(key = "summary") { TodaySummaryCard(state = state) }

        if (state.isClassTeacher && state.stage != Stage.S3_LOCKED) {
            item(key = "bulk") {
                BulkActionsRow(onAllPresent = onAllPresent, onAllAbsent = onAllAbsent)
            }
        }

        item(key = "hint") {
            Text(
                text = when {
                    !state.isClassTeacher ->
                        "Read-only — only the class teacher can mark attendance."
                    state.stage == Stage.S3_LOCKED ->
                        "Attendance is locked for today. Tap a student to request a correction."
                    else ->
                        "Tap a student to cycle Present / Absent / Leave · long-press to mark Late."
                },
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        items(state.students, key = { it.studentId }) { student ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                StudentRow(
                    rollNo = student.rollNo,
                    name = student.name,
                    status = student.dayStatuses[today],
                    enabled = state.isClassTeacher,
                    pending = state.pendingCorrectionIds.contains(student.studentId),
                    onClick = { onStudentTap(student.studentId) },
                    onLongClick = { onStudentLongPress(student.studentId) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudentRow(
    rollNo: Int,
    name: String,
    status: AttendanceStatus?,
    enabled: Boolean,
    pending: Boolean = false,
    onClick: () -> Unit,
    // TCH-C1: long-press marks the student Late for today (opens the arrival-time
    // dialog). The tap cycle can't reach Tardy, so this is its discoverable entry.
    onLongClick: () -> Unit = {}
) {
    val statusColor = status?.let { getStatusColor(it) } ?: TextTertiary
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    val rollText = if (rollNo > 0) rollNo.toString().padStart(2, '0') else "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Glass.copy(alpha = 0.10f))
            .border(1.dp, GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (initial)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        // Roll + name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Roll $rollText", color = TextTertiary, fontSize = 12.sp)
                // Correction-request pending marker: the teacher filed a
                // correction for this student and it's awaiting admin review.
                // Without it the row looked unchanged after submit and a teacher
                // could re-tap (the server dedups, but it read as "nothing happened").
                if (pending) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarningAmberSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Correction pending",
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // Status chip (tap to cycle)
        StatusChip(status = status, color = statusColor, enabled = enabled)
    }
}

@Composable
private fun StatusChip(status: AttendanceStatus?, color: Color, enabled: Boolean) {
    val bg by animateColorAsState(
        targetValue = if (status != null) color.copy(alpha = 0.20f) else Glass.copy(alpha = 0.2f),
        animationSpec = tween(150),
        label = "chipBg"
    )
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.5.dp, color.copy(alpha = if (status != null) 0.6f else 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (status != null) color else TextTertiary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = status?.label ?: "Not marked",
            color = if (status != null) color else TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (enabled) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Tap to change",
                tint = (if (status != null) color else TextTertiary).copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/* ── Shared small states ─────────────────────────────────────────────── */

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Teal)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading attendance…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    selectedClass: ClassSection? = null,
    hasOtherClasses: Boolean = false
) {
    // Distinguish "no class chosen yet" from "this section has no students".
    // The latter is the common real case (a section with zero enrolled
    // students) and previously showed the misleading "Select a class" even
    // though a class WAS selected — so the teacher had no idea to switch.
    val title: String
    val subtitle: String
    when {
        selectedClass == null -> {
            title = "No students found"
            subtitle = "Select a class to view attendance"
        }
        hasOtherClasses -> {
            title = "No students in ${selectedClass.displayName}"
            subtitle = "This section has no enrolled students. Pick another class from the selector above."
        }
        else -> {
            title = "No students in ${selectedClass.displayName}"
            subtitle = "This section has no enrolled students yet."
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Filled.PersonOff,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReadOnlyWarning() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(WarningAmberSurface, shape = RoundedCornerShape(8.dp))
            .border(1.dp, WarningAmber.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Only class teachers can mark attendance. You are viewing in read-only mode.",
            style = MaterialTheme.typography.bodySmall,
            color = WarningAmber,
            fontWeight = FontWeight.Medium
        )
    }
}

/* ════════════════════════════════════════════════════════════════════════
   MY REQUESTS — the teacher's own correction requests (pending/approved/rejected)
   ════════════════════════════════════════════════════════════════════════ */

@Composable
private fun RequestsView(
    state: AttendanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("My Correction Requests", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Requests you filed and their status", color = TextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }

        when {
            state.isLoadingRequests -> LoadingState(modifier = Modifier.weight(1f))
            state.requestsError != null -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.requestsError,
                    color = ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            state.myCorrections.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No correction requests yet", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("Requests you file from a locked or past day appear here.", color = TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.myCorrections, key = { it.requestId }) { req -> RequestCard(req) }
            }
        }
    }
}

@Composable
private fun RequestCard(req: MyCorrection) {
    val badgeColor: Color
    val badgeBg: Color
    when (req.status) {
        "approved" -> { badgeColor = AttendancePresent; badgeBg = AttendancePresent.copy(alpha = 0.15f) }
        "rejected" -> { badgeColor = ErrorRed;          badgeBg = ErrorRed.copy(alpha = 0.15f) }
        else       -> { badgeColor = WarningAmber;      badgeBg = WarningAmberSurface }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glass.copy(alpha = 0.10f))
            .border(1.dp, GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    req.studentName.ifBlank { req.studentId },
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(req.date, "${req.className} ${req.section}".trim())
                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                    color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                req.status.replaceFirstChar { it.uppercase() },
                color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeBg).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusLabelOf(req.currentStatus), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
            Text(statusLabelOf(req.requestedStatus), color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (req.reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("“${req.reason}”", color = TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun statusLabelOf(code: String): String = when (code.uppercase()) {
    "P" -> "Present"
    "A" -> "Absent"
    "L" -> "Leave"
    "T" -> "Tardy"
    "H" -> "Holiday"
    "V" -> "Vacation"
    else -> code.ifBlank { "—" }
}

/* ════════════════════════════════════════════════════════════════════════
   HISTORY VIEW — the original month spreadsheet (view + edit where permitted)
   ════════════════════════════════════════════════════════════════════════ */

@Composable
private fun HistoryView(
    state: AttendanceUiState,
    viewModel: AttendanceViewModel,
    onBackToToday: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Back-to-today bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToToday) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                Text("Today", color = Teal, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "Attendance History",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        AttendanceTopBar(
            state = state,
            onClassSelected = viewModel::selectClass,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onMarkAllPresent = viewModel::markAllPresentToday,
            onMarkAllAbsent = viewModel::markAllAbsentToday,
            onRefresh = viewModel::refresh
        )

        if (state.selectedClass != null && state.stage != Stage.UNKNOWN) {
            StageBanner(
                stage = state.stage,
                lockedBy = state.lockedBy,
                editReason = state.editReason,
                onReasonChange = viewModel::setEditReason
            )
        }

        if (!state.isClassTeacher && state.selectedClass != null) {
            ReadOnlyWarning()
        }

        when {
            state.isLoading -> LoadingState(modifier = Modifier.weight(1f))
            state.students.isEmpty() -> EmptyState(
                modifier = Modifier.weight(1f),
                selectedClass = state.selectedClass,
                hasOtherClasses = state.availableClasses.size > 1
            )
            else -> {
                AttendanceGrid(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onCellClick = { studentId, day ->
                        // History grid: gate on class-teacher, then route each tap.
                        // Today's cell is a free cycle-edit ONLY when the run is not
                        // locked — when S3-locked it must go to the correction flow,
                        // exactly like the Today view. (Previously History cycled a
                        // locked today-cell, which can never save: a misleading
                        // dead-end that looked like it changed but silently reverted.)
                        // Past days always go to correction; future days aren't markable.
                        if (state.isClassTeacher) {
                            when {
                                viewModel.isViewingCurrentMonth() && day == state.todayDay ->
                                    if (state.stage == Stage.S3_LOCKED)
                                        viewModel.openCorrectionDialog(studentId, day)
                                    else
                                        viewModel.cycleStatus(studentId, day)
                                viewModel.isViewingCurrentMonth() && day > state.todayDay ->
                                    Unit // future day — not markable
                                else ->
                                    viewModel.openCorrectionDialog(studentId, day)
                            }
                        }
                    }
                )
                AttendanceLegend()
            }
        }
    }
}

/* ── Stage banner (Phase 1+2 server-driven UI hint) — unchanged ────────── */

@Composable
private fun StageBanner(
    stage: Stage,
    lockedBy: String?,
    editReason: String,
    onReasonChange: (String) -> Unit
) {
    val spec = when (stage) {
        Stage.S1_FREE -> StageBannerSpec(
            bg = AttendancePresent.copy(alpha = 0.15f),
            fg = AttendancePresent,
            label = "Open",
            message = "Free edit. No reason required."
        )
        Stage.S2_RESTRICTED -> StageBannerSpec(
            bg = AttendanceTardy.copy(alpha = 0.15f),
            fg = AttendanceTardy,
            label = "Restricted",
            message = "Reason required for edits (10+ chars)."
        )
        Stage.S3_LOCKED -> StageBannerSpec(
            bg = ErrorRed.copy(alpha = 0.12f),
            fg = ErrorRed,
            label = "Locked",
            message = lockedBy?.let { "Locked by $it. File a correction request." }
                ?: "Locked. File a correction request to change marks."
        )
        Stage.UNKNOWN -> StageBannerSpec(
            bg = Glass,
            fg = TextSecondary,
            label = "—",
            message = "Loading…"
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(spec.bg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(spec.fg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(spec.label, color = BgStart, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(spec.message, color = spec.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (stage == Stage.S2_RESTRICTED) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editReason,
                onValueChange = onReasonChange,
                placeholder = { Text("Reason (e.g. 'Bus delay correction')", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = spec.fg,
                    unfocusedBorderColor = spec.fg.copy(alpha = 0.4f),
                    cursorColor = spec.fg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Text(
                text = "${editReason.trim().length} / 10+",
                color = if (editReason.trim().length >= 10) AttendancePresent else spec.fg.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private data class StageBannerSpec(
    val bg: Color,
    val fg: Color,
    val label: String,
    val message: String
)

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

    val monthYear = remember(state.selectedMonth, state.selectedYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, state.selectedMonth)
            set(Calendar.YEAR, state.selectedYear)
        }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }
    // "All P/All A" mark TODAY only, so they are meaningless on a past month —
    // disable them there instead of showing an enabled button that silently no-ops.
    val isCurrentMonth = remember(state.selectedMonth, state.selectedYear) {
        val now = Calendar.getInstance()
        state.selectedMonth == now.get(Calendar.MONTH) && state.selectedYear == now.get(Calendar.YEAR)
    }
    val bulkEnabled = state.isClassTeacher && isCurrentMonth

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OutlinedButton(
                onClick = { classDropdownExpanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
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
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = TextSecondary)
            }
            Text(
                text = monthYear,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = TextSecondary)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onMarkAllPresent,
                enabled = bulkEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AttendancePresent,
                    disabledContentColor = TextTertiary.copy(alpha = 0.4f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (bulkEnabled) AttendancePresent.copy(alpha = 0.4f)
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
            OutlinedButton(
                onClick = onMarkAllAbsent,
                enabled = bulkEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AttendanceAbsent,
                    disabledContentColor = TextTertiary.copy(alpha = 0.4f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (bulkEnabled) AttendanceAbsent.copy(alpha = 0.4f)
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
    onCellClick: (studentId: String, day: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    // Shared vertical scroll for BOTH the frozen roll/name column and the
    // day-cell grid. Without this each LazyColumn kept its OWN scroll offset,
    // so vertical scrolling desynced the name column from the marks and a
    // teacher could read one student's name against another's row.
    val rowScrollState = rememberLazyListState()
    val rollWidth = 40.dp
    val nameWidth = 120.dp
    val dayCellSize = 36.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glassCard(cornerRadius = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .background(SurfaceDark)
                        .border(0.5.dp, DividerColor)
                ) {
                    Box(
                        modifier = Modifier
                            .width(rollWidth)
                            .height(dayCellSize)
                            .border(0.5.dp, DividerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("#", style = MaterialTheme.typography.labelMedium, color = TextTertiary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .width(nameWidth)
                            .height(dayCellSize)
                            .border(0.5.dp, DividerColor),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Student", style = MaterialTheme.typography.labelMedium, color = TextTertiary, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                LazyColumn(state = rowScrollState) {
                    items(state.students, key = { it.studentId }) { student ->
                        Row(modifier = Modifier.border(0.5.dp, DividerColor.copy(alpha = 0.5f))) {
                            Box(
                                modifier = Modifier
                                    .width(rollWidth)
                                    .height(dayCellSize)
                                    .background(Glass.copy(alpha = 0.15f))
                                    .border(0.5.dp, DividerColor.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(student.rollNo.toString(), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .width(nameWidth)
                                    .height(dayCellSize)
                                    .background(Glass.copy(alpha = 0.1f))
                                    .border(0.5.dp, DividerColor.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(student.name, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
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
                                .background(if (isToday) Teal.copy(alpha = 0.12f) else Color.Transparent)
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
                LazyColumn(state = rowScrollState) {
                    items(state.students, key = { it.studentId }) { student ->
                        Row {
                            for (day in 1..state.daysInMonth) {
                                AttendanceCell(
                                    status = student.dayStatuses[day],
                                    isToday = day == state.todayDay,
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
            Text(status.code, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
        } else {
            Text("-", style = MaterialTheme.typography.labelSmall, color = TextTertiary.copy(alpha = 0.3f), fontSize = 9.sp)
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
                Text("${status.code}=${status.label}", style = MaterialTheme.typography.labelSmall, color = TextTertiary, fontSize = 9.sp)
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
