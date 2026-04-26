package com.schoolsync.teacher.ui.homework

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.foundation.lazy.itemsIndexed
import com.schoolsync.teacher.ui.components.bouncyClickable
import com.schoolsync.teacher.ui.components.staggerIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
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
import com.schoolsync.teacher.data.model.HomeworkStatusEntry
import com.schoolsync.teacher.data.model.HomeworkTeacher
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.ErrorRedSurface
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.InfoBlue
import com.schoolsync.teacher.ui.theme.InfoBlueSurface
import com.schoolsync.teacher.ui.theme.SubjectDefault
import com.schoolsync.teacher.ui.theme.SubjectEnglish
import com.schoolsync.teacher.ui.theme.SubjectHindi
import com.schoolsync.teacher.ui.theme.SubjectMath
import com.schoolsync.teacher.ui.theme.SubjectScience
import com.schoolsync.teacher.ui.theme.SubjectSocial
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
import com.schoolsync.teacher.util.toFormattedDate
import com.schoolsync.teacher.util.toRelativeTime
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkTeacherScreen(
    viewModel: HomeworkTeacherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeworkEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is HomeworkEvent.Error -> snackbarHostState.showSnackbar("Error: ${event.message}")
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
                if (!state.showDetailSheet) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::showCreateDialog,
                        containerColor = Teal,
                        contentColor = BgStart,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Homework", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Main content: either list or detail
                if (state.showDetailSheet && state.selectedHomework != null) {
                    // Two-panel: list on left, detail on right
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                    ) {
                        HomeworkTopBar(
                            state = state,
                            onClassSelected = viewModel::selectClass,
                            onSubjectFilter = viewModel::selectSubjectFilter,
                            onRefresh = viewModel::refresh
                        )
                        HomeworkListContent(
                            state = state,
                            onHomeworkClick = viewModel::selectHomework,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Detail panel
                    HomeworkDetailPanel(
                        homework = state.selectedHomework!!,
                        students = state.students,
                        submissions = state.submissions,
                        isLoading = state.isLoadingSubmissions,
                        onMarkStatus = viewModel::markStudentStatus,
                        onDelete = { viewModel.confirmDelete(it) },
                        onClose = viewModel::hideDetailSheet,
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    )
                } else {
                    // Full width list
                    Column(modifier = Modifier.fillMaxSize()) {
                        HomeworkTopBar(
                            state = state,
                            onClassSelected = viewModel::selectClass,
                            onSubjectFilter = viewModel::selectSubjectFilter,
                            onRefresh = viewModel::refresh
                        )
                        HomeworkListContent(
                            state = state,
                            onHomeworkClick = viewModel::selectHomework,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Create homework dialog
        if (state.showCreateDialog) {
            CreateHomeworkDialog(
                formState = state.formState,
                subjects = state.subjectsForClass,
                onTitleChange = viewModel::updateFormTitle,
                onDescriptionChange = viewModel::updateFormDescription,
                onSubjectChange = viewModel::updateFormSubject,
                onDueDateChange = viewModel::updateFormDueDate,
                onCreate = viewModel::createHomework,
                onDismiss = viewModel::hideCreateDialog
            )
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

        // Delete confirmation dialog
        state.homeworkToDelete?.let { hw ->
            AlertDialog(
                onDismissRequest = { viewModel.confirmDelete(null) },
                title = { Text("Delete Homework?", color = TextPrimary) },
                text = {
                    Text(
                        "\"${hw.title}\" and all its submissions will be permanently deleted.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::executeDelete) {
                        Text("Delete", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.confirmDelete(null) }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun HomeworkTopBar(
    state: HomeworkUiState,
    onClassSelected: (HomeworkClassSection) -> Unit,
    onSubjectFilter: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var subjectDropdownExpanded by remember { mutableStateOf(false) }

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
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Homework",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
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

            // Subject filter
            Box {
                OutlinedButton(
                    onClick = { subjectDropdownExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (state.selectedSubjectFilter != null) Teal else TextSecondary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(
                            if (state.selectedSubjectFilter != null) Teal.copy(alpha = 0.4f)
                            else GlassBorder
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.selectedSubjectFilter ?: "All Subjects",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenu(
                    expanded = subjectDropdownExpanded,
                    onDismissRequest = { subjectDropdownExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Subjects", color = if (state.selectedSubjectFilter == null) Teal else TextPrimary) },
                        onClick = {
                            onSubjectFilter(null)
                            subjectDropdownExpanded = false
                        }
                    )
                    state.subjectsForClass.forEach { subject ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    subject,
                                    color = if (subject == state.selectedSubjectFilter) Teal else TextPrimary
                                )
                            },
                            onClick = {
                                onSubjectFilter(subject)
                                subjectDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Refresh
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun HomeworkListContent(
    state: HomeworkUiState,
    onHomeworkClick: (HomeworkTeacher) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading homework...", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else if (state.homeworkList.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Assignment,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No homework assigned",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    text = "Tap + to create new homework",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = state.homeworkList,
                key = { _, it -> it.hwId }
            ) { index, hw ->
                Box(modifier = Modifier.staggerIn(index)) {
                    HomeworkCard(
                        homework = hw,
                        isSelected = hw.hwId == state.selectedHomework?.hwId,
                        onClick = { onHomeworkClick(hw) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(
    homework: HomeworkTeacher,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val subjectColor = getSubjectColor(homework.subject)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) subjectColor.copy(alpha = 0.08f) else Glass)
            .border(
                1.dp,
                if (isSelected) subjectColor.copy(alpha = 0.3f) else GlassBorder,
                RoundedCornerShape(14.dp)
            )
            .bouncyClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Subject color bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(subjectColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = homework.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subject chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(subjectColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = homework.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = subjectColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }

                if (homework.description.isNotBlank()) {
                    Text(
                        text = homework.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Due date and time
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = homework.dueDate.ifBlank { "No due date" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
            if (homework.createdAt > 0) {
                Text(
                    text = homework.createdAt.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun HomeworkDetailPanel(
    homework: HomeworkTeacher,
    students: List<StudentInfo>,
    submissions: List<HomeworkStatusEntry>,
    isLoading: Boolean,
    onMarkStatus: (studentId: String, studentName: String, status: String, remark: String, score: Int) -> Unit,
    onDelete: (HomeworkTeacher) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subjectColor = getSubjectColor(homework.subject)
    val submissionMap = submissions.associateBy { it.studentId }

    // Counts
    val totalStudents = students.size
    val completedCount = submissions.count { it.status == "complete" }
    val submittedCount = submissions.count { it.status == "submitted" }
    val incompleteCount = submissions.count { it.status == "incomplete" }
    val pendingCount = totalStudents - submissions.size

    Column(
        modifier = modifier
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 14.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(subjectColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = homework.subject,
                        style = MaterialTheme.typography.labelMedium,
                        color = subjectColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = homework.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (homework.description.isNotBlank()) {
                    Text(
                        text = homework.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Due: ${homework.dueDate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "By: ${homework.teacherName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onDelete(homework) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status summary chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip("Complete", completedCount.toString(), SuccessGreen, SuccessGreenSurface, Modifier.weight(1f))
            StatusChip("Submitted", submittedCount.toString(), InfoBlue, InfoBlueSurface, Modifier.weight(1f))
            StatusChip("Incomplete", incompleteCount.toString(), WarningAmber, WarningAmberSurface, Modifier.weight(1f))
            StatusChip("Pending", pendingCount.toString(), ErrorRed, ErrorRedSurface, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Student list
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(students, key = { it.studentId }) { student ->
                    val entry = submissionMap[student.studentId]
                    StudentSubmissionRow(
                        student = student,
                        entry = entry,
                        onStatusChange = { newStatus, remark, score ->
                            onMarkStatus(student.studentId, student.displayName, newStatus, remark, score)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    count: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun StudentSubmissionRow(
    student: StudentInfo,
    entry: HomeworkStatusEntry?,
    onStatusChange: (status: String, remark: String, score: Int) -> Unit
) {
    val currentStatus = entry?.status ?: "pending"
    val (statusColor, statusIcon) = when (currentStatus) {
        "complete" -> SuccessGreen to Icons.Filled.CheckCircle
        "reviewed" -> SuccessGreen to Icons.Filled.CheckCircle
        "submitted" -> InfoBlue to Icons.Filled.Assignment
        "incomplete" -> WarningAmber to Icons.Filled.HourglassEmpty
        else -> TextTertiary to Icons.Filled.Pending
    }

    var showStatusMenu by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Glass.copy(alpha = 0.4f))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Roll number
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Glass),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = student.rollNo.ifBlank { "-" },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Student name + submitted text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show what the student submitted
            if (entry != null && entry.text.isNotBlank()) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Show score if graded
            if (entry != null && entry.score >= 0) {
                Text(
                    text = "Score: ${entry.score}" + if (entry.remark.isNotBlank()) " — ${entry.remark}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Status button
        Box {
            OutlinedButton(
                onClick = { showStatusMenu = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColor),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(statusColor.copy(alpha = 0.4f))
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(statusIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentStatus.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            DropdownMenu(
                expanded = showStatusMenu,
                onDismissRequest = { showStatusMenu = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                listOf("pending", "submitted", "reviewed", "incomplete", "complete").forEach { status ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                status.replaceFirstChar { it.uppercase() },
                                color = if (status == currentStatus) Teal else TextPrimary
                            )
                        },
                        onClick = {
                            showStatusMenu = false
                            if (status == "reviewed") {
                                showReviewDialog = true
                            } else {
                                onStatusChange(status, "", -1)
                            }
                        }
                    )
                }
            }
        }
    }

    // Review dialog — score + remark input
    if (showReviewDialog) {
        var scoreText by remember { mutableStateOf(if (entry != null && entry.score >= 0) entry.score.toString() else "") }
        var remarkText by remember { mutableStateOf(entry?.remark ?: "") }

        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = {
                Text("Review — ${student.displayName}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entry != null && entry.text.isNotBlank()) {
                        Text("Student's response:", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            entry.text,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Glass)
                                .padding(10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Score") },
                        placeholder = { Text("e.g. 8") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Teal,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remarkText,
                        onValueChange = { remarkText = it },
                        label = { Text("Remark") },
                        placeholder = { Text("e.g. Good work, keep it up!") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = Teal,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val score = scoreText.toIntOrNull() ?: -1
                    onStatusChange("reviewed", remarkText.trim(), score)
                    showReviewDialog = false
                }) {
                    Text("Submit Review", color = Teal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateHomeworkDialog(
    formState: HomeworkFormState,
    subjects: List<String>,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    var subjectDropdownExpanded by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Teal,
        focusedBorderColor = Teal,
        unfocusedBorderColor = GlassBorder,
        focusedLabelColor = Teal,
        unfocusedLabelColor = TextSecondary
    )

    AlertDialog(
        onDismissRequest = { if (!formState.isSubmitting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Homework", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = onTitleChange,
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )

                OutlinedTextField(
                    value = formState.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )

                // Subject selector
                Box {
                    OutlinedTextField(
                        value = formState.subject,
                        onValueChange = {},
                        label = { Text("Subject *") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { subjectDropdownExpanded = true },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                        },
                        colors = textFieldColors
                    )
                    DropdownMenu(
                        expanded = subjectDropdownExpanded,
                        onDismissRequest = { subjectDropdownExpanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        subject,
                                        color = if (subject == formState.subject) Teal else TextPrimary
                                    )
                                },
                                onClick = {
                                    onSubjectChange(subject)
                                    subjectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Fix #1: Due date with clickable field + DatePicker
                var showDatePicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = formState.dueDate.ifBlank { "Tap to select date" },
                    onValueChange = {},
                    label = { Text("Due Date") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false, // makes entire field clickable
                    colors = textFieldColors
                )

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L // default: 1 week from now
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    onDueDateChange(sdf.format(java.util.Date(millis)))
                                }
                                showDatePicker = false
                            }) { Text("OK", color = Teal) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancel", color = TextSecondary)
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = !formState.isSubmitting && formState.title.isNotBlank() && formState.subject.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = BgStart
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = BgStart,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (formState.isSubmitting) "Creating..." else "Create",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !formState.isSubmitting
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp)
    )
}

/** Map subject names to theme colors. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun getSubjectColor(subject: String): Color {
    val lower = subject.lowercase()
    return when {
        "math" in lower || "maths" in lower -> SubjectMath
        "science" in lower || "physics" in lower || "chemistry" in lower || "bio" in lower -> SubjectScience
        "english" in lower -> SubjectEnglish
        "hindi" in lower || "sanskrit" in lower -> SubjectHindi
        "social" in lower || "history" in lower || "geography" in lower || "civics" in lower -> SubjectSocial
        "computer" in lower || "comp" in lower || "it" == lower -> InfoBlue
        else -> SubjectDefault
    }
}
