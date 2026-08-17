package com.schoolsync.teacher.ui.redflags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.StudentFlag
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.ErrorRedSurface
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
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
import com.schoolsync.teacher.util.toRelativeTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RedFlagTeacherScreen(
    canEdit: Boolean = true,
    viewModel: RedFlagTeacherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var lastEventWasError by remember { mutableStateOf(false) }

    // Phase 6A — bottom-sheet state for the 1-tap quick flag flow.
    // Hosted at the screen level so any row-level 🚩 trigger inside
    // StudentFlagList can reuse a single sheet instance.
    val quickFlagState = rememberQuickFlagSheetState()

    // Auto-dismiss the Undo snackbar after 5s and drop the handle so
    // the next quick flag starts a fresh undo window.
    LaunchedEffect(state.lastCreatedFlagId) {
        if (state.lastCreatedFlagId != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearLastCreatedFlagId()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is RedFlagEvent.Success -> {
                    lastEventWasError = false
                    snackbarHostState.showSnackbar(event.message)
                }
                is RedFlagEvent.Error -> {
                    lastEventWasError = true
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    GradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    val isError = lastEventWasError
                    val accent = if (isError) ErrorRed else SuccessGreen
                    val accentBg = if (isError) ErrorRedSurface else SuccessGreenSurface
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDark)
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = data.visuals.message,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            floatingActionButton = {
                // Level-gated: a view-only grantee can read flags but not raise them.
                if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Phase 6A — FAB now opens the quick flag sheet
                        // for whichever student is currently selected.
                        // No selection → emit a hint event so the user
                        // knows to pick a row first (the inline 🚩 icon
                        // on each row is the canonical entry point).
                        val cs = state.selectedClass
                        val sid = state.selectedStudentId
                        val student = state.students.firstOrNull { it.studentId == sid }
                        if (cs != null && student != null) {
                            quickFlagState.showFor(
                                student = student,
                                classKey = cs.className,
                                sectionKey = cs.section,
                                defaultSubject = "",
                                forceSubjectPick = true,
                                subjectsForClass = state.subjectsForClass
                            )
                        } else {
                            viewModel.showHint(
                                "Tap the 🚩 next to a student to raise a flag"
                            )
                        }
                    },
                    containerColor = ErrorRed,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Flag", fontWeight = FontWeight.SemiBold)
                }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top bar
                RedFlagTopBar(
                    state = state,
                    onClassSelected = viewModel::selectClass,
                    onRefresh = viewModel::refresh
                )

                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Teal)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading flags...", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    // Responsive split. This used to be an unconditional Row at
                    // 35/65 — on a portrait phone that left the student list at
                    // ~125dp wide, clipping names and roll numbers. Side-by-side
                    // only once there is genuinely room for two panes; otherwise
                    // stack, giving the roster a fixed slice and the detail the
                    // rest. (Dialogs/sheets/lists must fit small screens and
                    // landscape — the most repeated UI bug class in this app.)
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val sideBySide = maxWidth >= 600.dp
                    if (sideBySide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Student list with flag counts
                        StudentFlagList(
                            students = state.students,
                            flagsByStudent = state.flagsByStudent,
                            selectedStudentId = state.selectedStudentId,
                            onStudentSelected = viewModel::selectStudent,
                            // Phase 6A — 🚩 trailing icon now opens the
                            // quick flag sheet. Subject is forced (Red
                            // Flags screen has no subject context); list
                            // of valid subjects comes from the teacher's
                            // assignments for this class+section.
                            onCreateFlag = { student ->
                                val cs = state.selectedClass
                                if (cs != null) {
                                    quickFlagState.showFor(
                                        student          = student,
                                        classKey         = cs.className,
                                        sectionKey       = cs.section,
                                        defaultSubject   = "",
                                        forceSubjectPick = true,
                                        subjectsForClass = state.subjectsForClass
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight()
                        )

                        // Right: Flag detail list
                        FlagDetailPanel(
                            selectedStudentId = state.selectedStudentId,
                            students = state.students,
                            flagsByStudent = state.flagsByStudent,
                            currentTeacherUid = state.currentTeacherUid,
                            canEdit = canEdit,
                            busyFlagIds = state.busyFlagIds,
                            onResolve = viewModel::resolveFlag,
                            onDelete = viewModel::deleteFlag,
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight()
                        )
                    }
                    } else {
                        // Narrow (portrait phone): stack. The roster keeps a
                        // usable full-width slice; the detail panel takes the
                        // remainder and scrolls internally.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudentFlagList(
                                students = state.students,
                                flagsByStudent = state.flagsByStudent,
                                selectedStudentId = state.selectedStudentId,
                                onStudentSelected = viewModel::selectStudent,
                                onCreateFlag = { student ->
                                    val cs = state.selectedClass
                                    if (cs != null) {
                                        quickFlagState.showFor(
                                            student          = student,
                                            classKey         = cs.className,
                                            sectionKey       = cs.section,
                                            defaultSubject   = "",
                                            forceSubjectPick = true,
                                            subjectsForClass = state.subjectsForClass
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.45f)
                            )
                            FlagDetailPanel(
                                selectedStudentId = state.selectedStudentId,
                                students = state.students,
                                flagsByStudent = state.flagsByStudent,
                                currentTeacherUid = state.currentTeacherUid,
                                canEdit = canEdit,
                                busyFlagIds = state.busyFlagIds,
                                onResolve = viewModel::resolveFlag,
                                onDelete = viewModel::deleteFlag,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.55f)
                            )
                        }
                    }
                    }
                }
            }
        }


        // Phase 6A — quick flag sheet, controlled by quickFlagState
        QuickFlagSheet(
            state = quickFlagState,
            isSaving = state.savingFlag,
            onSubmit = viewModel::submitQuickFlag
        )
        // Dismiss the sheet only once the create SUCCEEDS (lastCreatedFlagId is
        // set on success). On failure the sheet stays open (spinner clears) so
        // the teacher can retry without re-entering everything.
        LaunchedEffect(state.lastCreatedFlagId) {
            if (state.lastCreatedFlagId != null && quickFlagState.visible) {
                quickFlagState.dismiss()
            }
        }

        // Phase 6A — 5-second Undo banner, anchored bottom-center.
        // Visible only while lastCreatedFlagId is non-null (auto-cleared
        // by LaunchedEffect at the top of the composable).
        QuickFlagUndoBanner(
            visible = state.lastCreatedFlagId != null,
            onUndo = viewModel::undoLastQuickFlag
        )

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
    }
}

@Composable
private fun RedFlagTopBar(
    state: RedFlagUiState,
    onClassSelected: (FlagClassSection) -> Unit,
    onRefresh: () -> Unit
) {
    var classDropdownExpanded by remember { mutableStateOf(false) }

    // Count summary
    val totalActive = state.flagsByStudent.values.sumOf { flags ->
        flags.count { it.status == "active" }
    }
    val totalHigh = state.flagsByStudent.values.sumOf { flags ->
        flags.count { it.status == "active" && it.severity == "high" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Red Flags",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (totalActive > 0) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ErrorRedSurface)
                        .border(1.dp, ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$totalActive active" + if (totalHigh > 0) " ($totalHigh high)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Class selector
            Box {
                OutlinedButton(
                    onClick = { classDropdownExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(GlassBorder)
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
                    state.availableClasses.forEach { cs ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    cs.displayName,
                                    color = if (cs == state.selectedClass) Teal else TextPrimary
                                )
                            },
                            onClick = {
                                onClassSelected(cs)
                                classDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun StudentFlagList(
    students: List<StudentInfo>,
    flagsByStudent: Map<String, List<StudentFlag>>,
    selectedStudentId: String?,
    onStudentSelected: (String?) -> Unit,
    onCreateFlag: (StudentInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Students",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // "All" button to clear selection
            if (selectedStudentId != null) {
                TextButton(onClick = { onStudentSelected(null) }) {
                    Text("View All", color = Teal, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (students.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No students found", color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(students, key = { _, s -> s.studentId }) { index, student ->
                    val flags = flagsByStudent[student.studentId] ?: emptyList()
                    val activeFlags = flags.count { it.status == "active" }
                    val highFlags = flags.count { it.status == "active" && it.severity == "high" }
                    val isSelected = student.studentId == selectedStudentId

                    StudentFlagRow(
                        student = student,
                        // Row index used as fallback when the student
                        // record has no `rollNo` populated — "-" was
                        // confusing teachers who expected a serial number.
                        rowIndex = index + 1,
                        activeFlagCount = activeFlags,
                        highFlagCount = highFlags,
                        isSelected = isSelected,
                        onClick = { onStudentSelected(student.studentId) },
                        onAddFlag = { onCreateFlag(student) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentFlagRow(
    student: StudentInfo,
    rowIndex: Int,
    activeFlagCount: Int,
    highFlagCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAddFlag: () -> Unit
) {
    val bgColor = when {
        isSelected -> TealSurface
        highFlagCount > 0 -> ErrorRedSurface
        activeFlagCount > 0 -> WarningAmberSurface
        else -> Glass.copy(alpha = 0.3f)
    }
    val borderColor = when {
        isSelected -> Teal.copy(alpha = 0.4f)
        highFlagCount > 0 -> ErrorRed.copy(alpha = 0.3f)
        activeFlagCount > 0 -> WarningAmber.copy(alpha = 0.2f)
        else -> GlassBorder.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Roll
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Glass),
            contentAlignment = Alignment.Center
        ) {
            Text(
                // Show explicit roll number when populated; otherwise
                // fall back to the row index so every student gets a
                // visible serial number.
                text = student.rollNo.ifBlank { rowIndex.toString() },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = student.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )

        // Flag count badge
        if (activeFlagCount > 0) {
            val badgeColor = if (highFlagCount > 0) ErrorRed else WarningAmber
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$activeFlagCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Add flag button
        IconButton(onClick = onAddFlag, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Flag,
                contentDescription = "Add flag",
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun FlagDetailPanel(
    selectedStudentId: String?,
    students: List<StudentInfo>,
    flagsByStudent: Map<String, List<StudentFlag>>,
    currentTeacherUid: String,
    /**
     * Level-gated write affordance (F15). A `view`-level Red Flags grantee can
     * read flags but must not be offered Resolve/Delete — the Firestore rule now
     * denies those too, so showing the buttons would only produce a
     * PERMISSION_DENIED toast. Mirrors how the FAB is already gated.
     */
    canEdit: Boolean,
    busyFlagIds: Set<String>,
    onResolve: (studentId: String, flagId: String) -> Unit,
    onDelete: (flagId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayFlags: List<Pair<String, StudentFlag>> = if (selectedStudentId != null) {
        // Show flags for the selected student
        val flags = flagsByStudent[selectedStudentId] ?: emptyList()
        flags.map { selectedStudentId to it }
    } else {
        // Show all flags across all students, sorted by timestamp
        flagsByStudent.flatMap { (studentId, flags) ->
            flags.map { studentId to it }
        }.sortedByDescending { it.second.createdAtMs }
    }

    val selectedStudent = students.find { it.studentId == selectedStudentId }

    Column(
        modifier = modifier
            .glassCard()
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedStudent != null) "Flags - ${selectedStudent.displayName}" else "All Flags",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Summary chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val activeCount = displayFlags.count { it.second.status == "active" }
                val resolvedCount = displayFlags.count { it.second.status == "resolved" }
                if (activeCount > 0) {
                    MiniChip("$activeCount active", ErrorRed, ErrorRedSurface)
                }
                if (resolvedCount > 0) {
                    MiniChip("$resolvedCount resolved", SuccessGreen, SuccessGreenSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayFlags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedStudentId != null) "No flags for this student" else "No flags in this class",
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
                items(displayFlags, key = { "${it.first}_${it.second.flagId}" }) { (studentId, flag) ->
                    // Only show actions this teacher can actually perform, so
                    // the buttons match the Firestore rule instead of tapping
                    // into a PERMISSION_DENIED. A (non-admin) teacher may
                    // resolve/delete ONLY their own flags (teacherId == uid);
                    // delete additionally requires it be a teacher-created flag
                    // (admin-issued flags routed to them are resolve-only).
                    val isOwn = currentTeacherUid.isNotBlank() && flag.teacherId == currentTeacherUid
                    val canResolve = isOwn && canEdit
                    val canDelete = isOwn && canEdit && flag.status != "deleted" && flag.createdByRole == "teacher"
                    FlagCard(
                        flag = flag,
                        showStudentName = selectedStudentId == null,
                        canResolve = canResolve,
                        isBusy = flag.flagId in busyFlagIds,
                        onResolve = { onResolve(studentId, flag.flagId) },
                        canDelete = canDelete,
                        onDelete = { onDelete(flag.flagId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 9.sp)
    }
}

@Composable
private fun FlagCard(
    flag: StudentFlag,
    showStudentName: Boolean,
    canResolve: Boolean = false,
    isBusy: Boolean = false,
    onResolve: () -> Unit,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this flag?", color = TextPrimary) },
            text = {
                Text(
                    "The flag will be hidden from your list and the parent's app. " +
                    "Only an admin can bring it back.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(14.dp)
        )
    }
    val (severityColor, severityBg) = when (flag.severity) {
        "high" -> ErrorRed to ErrorRedSurface
        "medium" -> WarningAmber to WarningAmberSurface
        else -> WarningAmber.copy(alpha = 0.6f) to WarningAmberSurface.copy(alpha = 0.5f)
    }
    val isResolved = flag.status == "resolved"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isResolved) Glass.copy(alpha = 0.2f) else Glass)
            .border(
                1.dp,
                if (isResolved) GlassBorder.copy(alpha = 0.3f) else severityColor.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Severity indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isResolved) SuccessGreen else severityColor)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Type + severity row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Type chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Glass)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = flag.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Severity chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(severityBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = flag.severity.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isResolved) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SuccessGreenSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "RESOLVED",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (flag.subject.isNotBlank()) {
                    Text(
                        text = flag.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Student name (when in all-flags view)
            if (showStudentName && flag.studentName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = flag.studentName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Message
            Text(
                text = flag.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isResolved) TextTertiary else TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp + teacher
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (flag.createdAtMs > 0) {
                    Text(
                        text = flag.createdAtMs.toRelativeTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
                if (flag.teacherName.isNotBlank()) {
                    Text(
                        text = "by ${flag.teacherName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Action column — Resolve (if not resolved) + Delete (own teacher
        // flags only). Stacked so the row layout doesn't get crowded on
        // narrow tablets.
        if ((!isResolved && canResolve) || canDelete) {
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (!isResolved && canResolve) {
                    OutlinedButton(
                        onClick = onResolve,
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = SolidColor(SuccessGreen.copy(alpha = 0.4f))
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SuccessGreen)
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBusy) "Resolving…" else "Resolve", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (canDelete) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = SolidColor(ErrorRed.copy(alpha = 0.4f))
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ErrorRed)
                        } else {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBusy) "Deleting…" else "Delete", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
