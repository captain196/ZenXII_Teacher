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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalContext
import com.schoolsync.teacher.util.AttachmentUrlValidator
import com.schoolsync.teacher.util.HomeworkAttachmentUploader
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
                    // Two-panel: list on left, detail on right.
                    // 0.38 / 0.62 — list pane is wide enough to keep the
                    // homework cards readable (avoiding the cramped 0.32
                    // experiment) while still giving the detail pane the
                    // majority of the screen since that's where the
                    // student-roster + status work happens. A thin
                    // vertical divider between panes makes the boundary
                    // read as deliberate, not accidental whitespace.
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                    ) {
                        HomeworkTopBar(
                            state = state,
                            onClassSelected = viewModel::selectClass,
                            onSubjectFilter = viewModel::selectSubjectFilter,
                            onRefresh = viewModel::refresh,
                            compact = true   // sticky two-row header in two-panel view
                        )
                        HomeworkListContent(
                            state = state,
                            onHomeworkClick = viewModel::selectHomework,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Vertical separator between list + detail panes.
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(GlassBorder)
                    )

                    // Detail panel
                    HomeworkDetailPanel(
                        homework = state.selectedHomework!!,
                        students = state.students,
                        submissions = state.submissions,
                        teacherMarks = state.teacherMarks,
                        isLoading = state.isLoadingSubmissions,
                        onMarkStatus = viewModel::markStudentStatus,
                        onDelete = { viewModel.confirmDelete(it) },
                        onClose = viewModel::hideDetailSheet,
                        modifier = Modifier
                            .weight(0.62f)
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
            val context = LocalContext.current
            CreateHomeworkDialog(
                formState = state.formState,
                subjects = state.subjectsForClass,
                onTitleChange = viewModel::updateFormTitle,
                onDescriptionChange = viewModel::updateFormDescription,
                onSubjectChange = viewModel::updateFormSubject,
                onDueDateChange = viewModel::updateFormDueDate,
                onCreate = { viewModel.createHomework(context) },
                onDismiss = viewModel::hideCreateDialog,
                onAttachmentsPicked = { uris -> viewModel.onAttachmentsPicked(context, uris) },
                onRemoveAttachment = viewModel::removeSelectedAttachment,
                onClearAttachmentError = viewModel::clearAttachmentError
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
    onRefresh: () -> Unit,
    compact: Boolean = false
) {
    if (compact) {
        // ── Two-panel mode: sticky two-row header with proper hierarchy ──
        // Row 1: page title + count + refresh icon (chrome).
        // Row 2: class chip + subject filter chip (context).
        // Divider underneath so the cards visibly belong to a section.
        // Was previously a single SpaceBetween row with the class pill
        // floating in negative space — read as wasted whitespace.
        CompactHomeworkTopBar(
            state = state,
            onClassSelected = onClassSelected,
            onSubjectFilter = onSubjectFilter,
            onRefresh = onRefresh
        )
        return
    }

    var classDropdownExpanded by remember { mutableStateOf(false) }
    var subjectDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title — kept as titleLarge (was headlineSmall) to free up
        // vertical space; left pane was wasting ~80dp before the first
        // homework card on standard phones in landscape.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Homework",
                style = MaterialTheme.typography.titleLarge,
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

/**
 * Compact two-row sticky header used in the two-pane layout. Built from
 * scratch (rather than reusing the wide top bar's SpaceBetween row) because
 * at narrow widths that layout left a noticeable gap between the title and
 * the class pill — looked like wasted whitespace, not deliberate spacing.
 *
 * Row 1 (chrome): app icon + "Homework" title + count badge + refresh.
 * Row 2 (context): class chip + subject filter chip.
 * Bottom-edge divider: visually anchors the cards below as belonging to
 * "this class's homework", not floating in space.
 */
@Composable
private fun CompactHomeworkTopBar(
    state: HomeworkUiState,
    onClassSelected: (HomeworkClassSection) -> Unit,
    onSubjectFilter: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var subjectDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgStart.copy(alpha = 0.2f))
    ) {
        // Row 1 — chrome
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Homework",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            // Count badge — surfaces "how much homework am I looking at"
            // without making the user count cards.
            if (state.homeworkList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Teal.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${state.homeworkList.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Row 2 — context chips (class + subject filter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Class chip — uses the same OutlinedButton pattern as the wide
            // top bar but with smaller padding/typography so it reads as a
            // pill, not a button.
            Box {
                OutlinedButton(
                    onClick = { classDropdownExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(GlassBorder)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = state.selectedClass?.displayName ?: "Select Class",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
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

            // Subject filter — collapses to a small chip showing either the
            // selected subject (highlighted teal) or the icon for "no filter".
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
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.selectedSubjectFilter ?: "All Subjects",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = subjectDropdownExpanded,
                    onDismissRequest = { subjectDropdownExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "All Subjects",
                                color = if (state.selectedSubjectFilter == null) Teal else TextPrimary
                            )
                        },
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
        }

        // Bottom-edge separator under the header so the cards visibly belong
        // to a section rather than floating below empty space.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GlassBorder.copy(alpha = 0.6f))
        )
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
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

    // Vertical card layout — the previous horizontal "title left, due-date
    // column right" was eating ~150dp of the narrow list pane just to show
    // "Due tomorrow, 11:59 PM IST", which truncated titles to "Test ..."
    // and forced the subject chip to wrap character-by-character. The new
    // layout gives the title the full card width, then puts the subject
    // chip + due date together on a single meta row underneath.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) subjectColor.copy(alpha = 0.10f) else Glass)
            .border(
                1.dp,
                if (isSelected) subjectColor.copy(alpha = 0.40f) else GlassBorder,
                RoundedCornerShape(14.dp)
            )
            .bouncyClickable(onClick = onClick)
            .padding(start = 0.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Subject-coloured side bar — full card height via IntrinsicSize.Max
        // so the bar doesn't go shorter than the content (looked detached).
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(IntrinsicSize.Max)
                .background(subjectColor)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Title — gets the full card width now. Allow up to 2 lines so
            // long titles ("Test Case 22 - Real numbers") aren't clipped.
            Text(
                text = homework.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Meta row — subject chip + due date on the same line. Both get
            // their natural width; the chip never wraps because the row is
            // not weight-constrained.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subject pill — single-line, capped at sensible width so a
                // ridiculous subject name doesn't push the date off-screen.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(subjectColor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = homework.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = subjectColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Attachment indicator — a compact paperclip (+ count when
                // more than one) so teachers can tell at a glance which cards
                // carry files without opening each one. Grouped with the
                // subject pill as a "tag"; placed before the due date, whose
                // weight(1f, fill=false) yields space so this never clips it.
                if (homework.attachments.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Teal.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "Has attachments",
                            tint = Teal,
                            modifier = Modifier.size(11.dp)
                        )
                        if (homework.attachments.size > 1) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${homework.attachments.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Due date — calendar icon + compact label. Truncates if the
                // pane is somehow even narrower than expected, instead of
                // forcing the row to break.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDueDateIST(homework.dueDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Relative-time hint goes on its own thin row when present —
            // moved here from the right column so it doesn't fight the
            // due-date for horizontal space.
            if (homework.createdAt > 0) {
                Text(
                    text = "Created ${homework.createdAt.toRelativeTime()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1
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
    teacherMarks: Map<String, com.schoolsync.teacher.data.repository.firestore.TeacherMarkEntry>,
    isLoading: Boolean,
    onMarkStatus: (studentId: String, studentName: String, status: String, remark: String, score: Int) -> Unit,
    onDelete: (HomeworkTeacher) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subjectColor = getSubjectColor(homework.subject)
    val submissionMap = submissions.associateBy { it.studentId }

    // ── Status counts ─────────────────────────────────────────────
    // Each student lands in EXACTLY ONE chip. Source of truth per student:
    //   1. Submission doc (if exists) — read its `status` field
    //   2. Else teacherMark doc (if exists) — read its `status` field
    //      (added 2026-05-06; previously every mark counted as Done
    //      regardless of what the teacher actually selected)
    //   3. Else Pending
    //
    // Done = reviewed OR complete (both mean "teacher signed off"),
    // Submitted = parent has turned in but teacher hasn't signed off,
    // Incomplete = teacher explicitly flagged as not enough,
    // Pending = no record OR submission/mark with status "pending".
    var doneCount = 0
    var submittedCount = 0
    var incompleteCount = 0
    var pendingCount = 0
    for (student in students) {
        val sub = submissionMap[student.studentId]
        val mark = if (sub == null) teacherMarks[student.studentId] else null
        val effectiveStatus = sub?.status ?: mark?.status ?: "pending"
        when (effectiveStatus.lowercase()) {
            "complete", "reviewed" -> doneCount++
            "submitted"            -> submittedCount++
            "incomplete"           -> incompleteCount++
            else                   -> pendingCount++   // "pending" + any unknown
        }
    }

    LazyColumn(
        modifier = modifier
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp)
    ) {
        // Header
        item {
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
                            text = formatDueDateIST(homework.dueDate),
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
        }

        // Status summary chips. "Done" merges Reviewed + Complete +
        // teacher marks (the three end-states where the teacher has
        // actioned the row); previously Reviewed had no chip and the
        // students with that status disappeared from the totals.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip("Done", doneCount.toString(), SuccessGreen, SuccessGreenSurface, Modifier.weight(1f))
                StatusChip("Submitted", submittedCount.toString(), InfoBlue, InfoBlueSurface, Modifier.weight(1f))
                StatusChip("Incomplete", incompleteCount.toString(), WarningAmber, WarningAmberSurface, Modifier.weight(1f))
                StatusChip("Pending", pendingCount.toString(), ErrorRed, ErrorRedSurface, Modifier.weight(1f))
            }
        }

        // ── Phase 1b Attachments review section ───────────────────────
        // Lives as a header item in the SAME LazyColumn as the student
        // roster, so a tall attachment list scrolls with the page instead
        // of squeezing the roster (it was previously a fixed Column above
        // a separate list). Each tap routes through
        // [AttachmentUrlValidator.openAttachmentSafely] which enforces the
        // https + firebasestorage.googleapis.com allowlist before
        // Intent.ACTION_VIEW dispatch. Images render a real Coil thumbnail;
        // PDFs/other show a type-coloured icon. The display name is decoded
        // from the URL (see [attachmentDisplayName]) so the teacher sees
        // "photo.jpg" rather than the %2F-encoded storage path.
        if (homework.attachments.isNotEmpty()) {
            item {
                val attachmentContext = LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ATTACHMENTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                    homework.attachments.forEach { attachment ->
                        val fileName = attachmentDisplayName(attachment)
                        val isPdf = fileName.endsWith(".pdf", ignoreCase = true)
                        val isImage = isImageAttachment(fileName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(cornerRadius = 10.dp)
                                .clickable {
                                    AttachmentUrlValidator.openAttachmentSafely(
                                        context = attachmentContext,
                                        rawUrl = attachment,
                                        fileName = fileName
                                    )
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isImage) {
                                AsyncImage(
                                    model = attachment,
                                    contentDescription = fileName,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isPdf) ErrorRedSurface else TealSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPdf)
                                            Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = if (isPdf) ErrorRed else Teal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isImage) "Image" else if (isPdf) "PDF" else "Document",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    fontSize = 10.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Open",
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Student roster — rows live in the same scrolling list. The
        // header/chips/attachments above are header items, so the roster
        // keeps its full height regardless of attachment count.
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Teal)
                }
            }
        } else {
            items(students, key = { it.studentId }) { student ->
                val entry = submissionMap[student.studentId]
                val mark = if (entry == null) teacherMarks[student.studentId] else null
                StudentSubmissionRow(
                    student = student,
                    entry = entry,
                    teacherMark = mark,
                    onStatusChange = { newStatus, remark, score ->
                        onMarkStatus(student.studentId, student.displayName, newStatus, remark, score)
                    }
                )
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
    teacherMark: com.schoolsync.teacher.data.repository.firestore.TeacherMarkEntry? = null,
    onStatusChange: (status: String, remark: String, score: Int) -> Unit
) {
    // Effective status priority: submission → teacherMark → pending. The
    // teacherMark fallback was missing, so a non-submitter the teacher had
    // marked "Incomplete" still rendered as "Pending" in the row pill while
    // being counted as something else in the chip totals — confusing.
    val currentStatus = entry?.status ?: teacherMark?.status ?: "pending"
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
            // IST submission time — sourced directly from Firestore submittedAt.
            if (entry != null && entry.submittedAt > 0L) {
                Text(
                    text = formatSubmittedAtIST(entry.submittedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp,
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
            // Phase HW: teacher recorded an evaluation for this student even
            // though they didn't submit (lives in teacherMarks collection).
            // Score only shown when actually graded (>=0); the status itself
            // already conveys "Incomplete" / "Pending" without a number.
            if (entry == null && teacherMark != null) {
                val scoreSuffix = if (teacherMark.score >= 0) " · score: ${teacherMark.score}" else ""
                val remarkSuffix = if (teacherMark.remark.isNotBlank()) " — ${teacherMark.remark}" else ""
                Text(
                    text = "Marked (no submission)$scoreSuffix$remarkSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber,
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
        // Score bounds: empty input ⇒ -1 sentinel ("ungraded"). Otherwise must
        // parse to 0..200. 200 is generous (handles per-/200 papers) while
        // still catching obvious typos like 999. Without a maxMarks field on
        // HomeworkDoc this is a reasonable cap.
        val parsedScore = scoreText.toIntOrNull()
        val scoreValid = scoreText.isEmpty() || (parsedScore != null && parsedScore in 0..200)

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
                        if (entry.submittedAt > 0L) {
                            Text(
                                formatSubmittedAtIST(entry.submittedAt),
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Score") },
                        placeholder = { Text("e.g. 8") },
                        singleLine = true,
                        isError = !scoreValid,
                        supportingText = if (!scoreValid) {
                            { Text("Score must be 0–200", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        } else null,
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
                        // Cap at 500 chars at input time so paste of a large
                        // body can't bloat the Firestore doc or push payload.
                        // Visible counter keeps the limit obvious.
                        onValueChange = { if (it.length <= 500) remarkText = it },
                        label = { Text("Remark") },
                        placeholder = { Text("e.g. Good work, keep it up!") },
                        maxLines = 3,
                        supportingText = { Text("${remarkText.length} / 500", fontSize = 11.sp, color = TextSecondary) },
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
                TextButton(
                    onClick = {
                        // Empty ⇒ -1 sentinel; otherwise the bounds-validated value.
                        val score = if (scoreText.isEmpty()) -1 else (parsedScore ?: -1)
                        onStatusChange("reviewed", remarkText.trim(), score)
                        showReviewDialog = false
                    },
                    enabled = scoreValid
                ) {
                    Text(
                        "Submit Review",
                        color = if (scoreValid) Teal else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
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
    onDismiss: () -> Unit,
    // Step 4 (2026-05-15) attachment integration. Three callbacks:
    //   • onAttachmentsPicked  → file picker activity-result lambda; the
    //                            ViewModel validates each URI's MIME +
    //                            size and stages valid ones in state
    //   • onRemoveAttachment   → pre-submit chip removal
    //   • onClearAttachmentError → dismiss the inline error banner
    onAttachmentsPicked: (List<android.net.Uri>) -> Unit,
    onRemoveAttachment: (android.net.Uri) -> Unit,
    onClearAttachmentError: () -> Unit
) {
    var subjectDropdownExpanded by remember { mutableStateOf(false) }
    // Hoisted out of the AlertDialog text slot — nesting a Dialog inside
    // another Dialog clips the inner dialog's buttons on smaller screens
    // (was causing the "DatePicker auto-closes with no OK option" bug).
    var showDatePicker by remember { mutableStateOf(false) }

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
        // Outside-tap must NOT dismiss — teacher may tap the form's edge while
        // typing and lose entered data. Only the X button or Cancel closes.
        onDismissRequest = { },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Teal, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Create Homework",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (!formState.isSubmitting) onDismiss() },
                    enabled = !formState.isSubmitting
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
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

                // Due date — tapping opens DatePickerDialog rendered as a
                // sibling of this AlertDialog (see end of function). The
                // dialog itself is NOT nested here, otherwise its OK/Cancel
                // buttons get clipped on smaller screens.
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

                // ─── Step 4 (2026-05-15) Attachments section ──────────
                // Layout: header row with count + Add Files button →
                // optional error banner → chip-style list of selected
                // files (each with file-type icon, name, size, and a
                // remove [X] button). The Add Files button is disabled
                // when the count cap is reached or while uploads are in
                // flight. Each chip's [X] is disabled during submit so a
                // teacher can't yank a file out from under the uploader.
                //
                // File picker uses ActivityResultContracts.OpenMultipleDocuments
                // with the homework MIME allowlist as the type filter.
                // The system picker enforces type filtering at the OS
                // level, so a teacher physically cannot select a .zip
                // or .docx — but the ViewModel re-validates MIME + size
                // server-side (well, app-side) as defense in depth.
                val attachmentPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                    onResult = onAttachmentsPicked
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attachments (${formState.selectedAttachments.size}/" +
                                "${HomeworkAttachmentUploader.MAX_FILES_PER_HOMEWORK})",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        val canAdd = !formState.isSubmitting &&
                            formState.selectedAttachments.size <
                                HomeworkAttachmentUploader.MAX_FILES_PER_HOMEWORK
                        OutlinedButton(
                            onClick = {
                                attachmentPickerLauncher.launch(
                                    HomeworkAttachmentUploader.ALLOWED_MIME_TYPES.toTypedArray()
                                )
                            },
                            enabled = canAdd,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (canAdd) Teal else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Add files",
                                fontSize = 13.sp,
                                color = if (canAdd) Teal else TextSecondary
                            )
                        }
                    }

                    // Inline error banner (per-pick validation or upload
                    // failure surfacing). Tap dismisses.
                    formState.attachmentError?.let { err ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !formState.isSubmitting) { onClearAttachmentError() }
                                .background(
                                    Color(0xFF5C1F1F).copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    Color(0xFF7A2C2C),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = err,
                                color = Color(0xFFE57373),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Chip list — one row per selected attachment.
                    formState.selectedAttachments.forEachIndexed { idx, attachment ->
                        val isUploading = formState.uploadingIndex == idx
                        val isPdf = attachment.mimeType.equals("application/pdf", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    GlassBorder.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Filled.PictureAsPdf
                                              else Icons.Filled.Image,
                                contentDescription = null,
                                tint = if (isPdf) Color(0xFFE57373) else Teal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachment.displayName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(attachment.sizeBytes),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Teal,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { onRemoveAttachment(attachment.uri) },
                                    enabled = !formState.isSubmitting,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
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

    // DatePickerDialog rendered as a sibling of the parent AlertDialog so it
    // gets its own dialog window. Nesting it inside AlertDialog.text { } caused
    // the OK/Cancel buttons to be clipped on the iQOO and similar devices —
    // teacher would tap a date and the picker would auto-close with no
    // confirm option.
    if (showDatePicker) {
        // 2026-05-15 timezone-correctness pass: Material3 stores
        // selectedDateMillis as UTC midnight of the chosen day. ALL parsing
        // and formatting in this dialog block uses a UTC-pinned SDF so that
        // the "yyyy-MM-dd" string round-trips cleanly with the picker's
        // internal representation. Without this, IST teachers saw the date
        // decrement by one on each reopen (UTC-midnight formatted in local
        // zone rendered as the previous day) and the dialog auto-closed
        // immediately on open (the local-zone seed never equalled the
        // picker's UTC-normalized state).
        val ymdUtc = remember {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
        }
        // Initialise the picker with the form's currently-set due date, if any,
        // so re-opening the picker shows the existing pick instead of defaulting
        // back to "+7 days from now". Falls back to +7 days when blank/invalid.
        val initialMillis = remember(formState.dueDate) {
            val parsed = try {
                if (formState.dueDate.isNotBlank()) {
                    ymdUtc.parse(formState.dueDate)?.time
                } else null
            } catch (e: Exception) { null }
            parsed ?: (System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L)
        }
        // Finding #25 2026-05-14 — picker-level past-date guard.
        // Without selectableDates, teachers could scroll the picker back to
        // any past year and create homework "born overdue" — Parent app's
        // isOverdue() immediately flags it, students see panic-state for
        // homework they never had a chance to submit. Two realistic vectors:
        //   • Operator-error: typo / wrong-year scroll (dominant scenario)
        //   • Insider abuse: disgruntled teacher weaponising deadlines
        // SelectableDates blocks past dates at picker UI level (greys them
        // out + hides past years in the year selector for snappier UX).
        // The "today" boundary is computed by mapping the teacher's locally
        // perceived calendar date (year/month/day from the device wall
        // clock) to UTC midnight of that same calendar date — matching the
        // unit Material3 uses to identify each day cell. This works in
        // every zone (IST, UTC, PST, etc.) without timezone-specific drift.
        //
        // UI-LAYER PROTECTION ONLY — server-side enforcement remains
        // intentionally deferred (Finding #1C for rule-layer dueDate
        // rejection; Finding #28 for repo-layer normalizeDueDate bounds).
        // Modified-APK bypass is therefore still possible; the same threat
        // scope applies to both deferred siblings. Picker-layer fix is the
        // accessible first-line defense for the dominant operator-error path.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val nowLocal = java.util.Calendar.getInstance()
                    val startOfTodayUtc = java.util.Calendar.getInstance(
                        java.util.TimeZone.getTimeZone("UTC")
                    ).apply {
                        set(java.util.Calendar.YEAR, nowLocal.get(java.util.Calendar.YEAR))
                        set(java.util.Calendar.MONTH, nowLocal.get(java.util.Calendar.MONTH))
                        set(java.util.Calendar.DAY_OF_MONTH, nowLocal.get(java.util.Calendar.DAY_OF_MONTH))
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    return utcTimeMillis >= startOfTodayUtc
                }
                override fun isSelectableYear(year: Int): Boolean {
                    return year >= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                }
            }
        )

        // Capture the picker's OWN post-normalization seed (UTC midnight of
        // the seeded day) so the auto-close guard compares like with like.
        // Previously this compared against the pre-normalization local-zone
        // initialMillis, which always differed from the picker's internal
        // value by the local UTC offset — causing instant auto-close on
        // every open (the "picker closes the moment I tap it" symptom).
        val pickerSeed = remember { datePickerState.selectedDateMillis }

        // Auto-fill + auto-close as soon as the user picks a date — was a UX
        // complaint that the dialog wouldn't close until the OK button was
        // tapped, and the field wouldn't update either. Compare against the
        // picker's own seed so the LaunchedEffect doesn't fire on the
        // initial state delivery.
        androidx.compose.runtime.LaunchedEffect(datePickerState.selectedDateMillis) {
            val ms = datePickerState.selectedDateMillis ?: return@LaunchedEffect
            if (ms != pickerSeed) {
                onDueDateChange(ymdUtc.format(java.util.Date(ms)))
                showDatePicker = false
            }
        }

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            // OK + Cancel kept as a fallback for users who want to confirm the
            // initial preselected value (auto-fill skips OK only when the
            // user actually picks a *different* date).
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDueDateChange(ymdUtc.format(java.util.Date(millis)))
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

/**
 * Human-readable byte count. Used by the Create Homework dialog's
 * attachment chip subtitles (e.g. "324 KB", "1.4 MB"). 0 or negative
 * size renders as "—" since some content providers don't expose length.
 */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "%.1f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
}

/**
 * Derive a clean, human-readable filename from a Firebase Storage download
 * URL. The object path's separators are %2F-encoded in the URL, so a naive
 * substringAfterLast("/") returns the whole encoded path (e.g.
 * "homework%2FSCH_X%2F..%2F1779989378030_photo.jpg") — which is what the
 * teacher used to see. We decode first, take the final path segment, then
 * strip the "{epochMillis}_" prefix the uploader prepends to keep storage
 * keys unique (see HomeworkAttachmentUploader). Falls back to "Attachment".
 */
private fun attachmentDisplayName(rawUrl: String): String {
    val noQuery = rawUrl.substringBefore("?")
    val decoded = try {
        java.net.URLDecoder.decode(noQuery, "UTF-8")
    } catch (_: Exception) {
        noQuery
    }
    val tail = decoded.substringAfterLast("/")
    val stripped = tail.replaceFirst(Regex("^\\d{10,}_"), "")
    return when {
        stripped.isBlank() -> "Attachment"
        !stripped.contains('.') -> "Attachment"
        else -> stripped
    }
}

/** True when the resolved filename looks like a viewable image. */
private fun isImageAttachment(fileName: String): Boolean {
    val l = fileName.lowercase()
    return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") ||
        l.endsWith(".webp") || l.endsWith(".gif") || l.endsWith(".bmp") ||
        l.endsWith(".heic") || l.endsWith(".heif")
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

/**
 * Format an ISO 8601 (or legacy YYYY-MM-DD) dueDate string into an
 * IST-friendly label:
 *   - same day  → "Due today (h:mm a)"
 *   - next day  → "Due tomorrow"
 *   - otherwise → "Due on dd MMM"
 * Returns "No due date" for blank input and the raw string if unparseable.
 */
private fun formatDueDateIST(raw: String): String {
    if (raw.isBlank()) return "No due date"
    val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd",
        "dd-MM-yyyy",
        "dd/MM/yyyy"
    )
    var due: java.util.Date? = null
    for (p in patterns) {
        try {
            val sdf = java.text.SimpleDateFormat(p, java.util.Locale.US)
            if (p.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val d = sdf.parse(raw)
            if (d != null) { due = d; break }
        } catch (_: Exception) {}
    }
    if (due == null) return raw
    fun istDay(d: java.util.Date): Long {
        val c = java.util.Calendar.getInstance(tz).apply {
            time = d
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis / 86_400_000L
    }
    val diff = istDay(due) - istDay(java.util.Date())
    val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply { timeZone = tz }
    val dateFmtShort = java.text.SimpleDateFormat("dd MMM", java.util.Locale.US).apply { timeZone = tz }
    val dateFmtLong  = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).apply { timeZone = tz }
    val time = timeFmt.format(due)
    return when {
        diff == 0L  -> "Due today, $time IST"
        diff == 1L  -> "Due tomorrow, $time IST"
        diff in 2..6 -> "Due ${dateFmtShort.format(due)}, $time IST"
        else        -> "Due ${dateFmtLong.format(due)}, $time IST"
    }
}

/**
 * Format a submission timestamp (Long millis) as IST date/time.
 * "Submitted just now" / "Submitted 5m ago" / "Submitted today, 11:30 AM" /
 * "Submitted 5 May, 11:30 AM IST" / "Submitted 5 May 2026, 11:30 AM IST".
 * Returns empty string for 0 / negative input.
 */
private fun formatSubmittedAtIST(ms: Long): String {
    if (ms <= 0L) return ""
    val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    val now = System.currentTimeMillis()
    val diffMs = now - ms
    if (diffMs in 0..59_000L) return "Submitted just now"
    if (diffMs in 60_000L..3_599_000L) return "Submitted ${diffMs / 60_000}m ago"
    val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply { timeZone = tz }
    val dateFmtShort = java.text.SimpleDateFormat("dd MMM", java.util.Locale.US).apply { timeZone = tz }
    val dateFmtLong  = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).apply { timeZone = tz }
    fun istDay(t: Long): Long {
        val c = java.util.Calendar.getInstance(tz).apply {
            timeInMillis = t
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis / 86_400_000L
    }
    val daysAgo = istDay(now) - istDay(ms)
    val time = timeFmt.format(java.util.Date(ms))
    return when {
        daysAgo == 0L -> "Submitted today, $time"
        daysAgo == 1L -> "Submitted yesterday, $time"
        daysAgo in 2..6 -> "Submitted ${dateFmtShort.format(java.util.Date(ms))}, $time IST"
        else -> "Submitted ${dateFmtLong.format(java.util.Date(ms))}, $time IST"
    }
}
