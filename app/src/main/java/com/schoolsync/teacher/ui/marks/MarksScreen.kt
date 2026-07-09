package com.schoolsync.teacher.ui.marks

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.glassCard
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun MarksScreen(
    viewModel: MarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MarksEvent.SaveSuccess -> snackbarHostState.showSnackbar(event.message)
                is MarksEvent.SaveError -> snackbarHostState.showSnackbar("Error: ${event.message}")
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
                if (state.hasUnsavedChanges && !state.isSaving) {
                    ExtendedFloatingActionButton(
                        onClick = viewModel::saveMarks,
                        containerColor = Teal,
                        contentColor = BgStart,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Marks", fontWeight = FontWeight.SemiBold)
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
            // Guard selector changes when there are unsaved marks: stash the
            // pending action and confirm before discarding it (HIGH-2).
            var pendingSelection by remember { mutableStateOf<(() -> Unit)?>(null) }
            val onGuarded: (() -> Unit) -> Unit = { action ->
                if (state.hasUnsavedChanges) pendingSelection = action else action()
            }

            val panel: @Composable (Boolean, Modifier) -> Unit = { compact, m ->
                MarksLeftPanel(
                    state = state,
                    onExamSelected = { exam -> onGuarded { viewModel.selectExam(exam) } },
                    onSubjectSelected = { subj -> onGuarded { viewModel.selectSubject(subj) } },
                    onClassSelected = { className, section ->
                        onGuarded { viewModel.selectClass(className, section) }
                    },
                    modifier = m,
                    compact = compact
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Below ~600dp of available width the 220dp panel + ~210dp of frozen
                // grid columns overflow, so stack the panel above the grid instead of
                // side-by-side — the feature stays usable on a portrait phone (HIGH-1).
                val isNarrow = maxWidth < 600.dp
                // Captured here because Compose's @LayoutScopeMarker blocks reaching
                // BoxWithConstraintsScope.maxHeight from inside the nested Column.
                val panelMaxHeight = maxHeight * 0.45f
                if (isNarrow) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        panel(
                            true,
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = panelMaxHeight)
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp)
                        )
                        MarksContent(
                            state = state,
                            onTheoryChange = viewModel::updateTheory,
                            onPracticalChange = viewModel::updatePractical,
                            onToggleAbsent = viewModel::toggleAbsent,
                            onRetry = viewModel::retry,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp, bottom = 8.dp, end = 12.dp)
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        panel(
                            false,
                            Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                        )
                        MarksContent(
                            state = state,
                            onTheoryChange = viewModel::updateTheory,
                            onPracticalChange = viewModel::updatePractical,
                            onToggleAbsent = viewModel::toggleAbsent,
                            onRetry = viewModel::retry,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                        )
                    }
                }
            }

            pendingSelection?.let { action ->
                AlertDialog(
                    onDismissRequest = { pendingSelection = null },
                    containerColor = SurfaceDark,
                    title = { Text("Discard unsaved marks?", color = TextPrimary) },
                    text = {
                        Text(
                            "You have unsaved marks on this subject. Switching now will discard them.",
                            color = TextSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val run = action
                            pendingSelection = null
                            run()
                        }) { Text("Discard", color = ErrorRed) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingSelection = null }) {
                            Text("Keep editing", color = Teal)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Right-hand content area: loading spinner, an error state with Retry (MED-7),
 * the "select exam & subject" empty state, or the marks grid. Extracted so the
 * responsive layout can drop it below the panel on narrow screens.
 */
@Composable
private fun MarksContent(
    state: MarksUiState,
    onTheoryChange: (String, String) -> Unit,
    onPracticalChange: (String, String) -> Unit,
    onToggleAbsent: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMsg = state.error
    Column(modifier = modifier) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal,
                                contentColor = BgStart
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Retry", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            state.selectedExam == null || state.selectedSubject == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Grade,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select exam and subject",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Choose an exam and subject to start entering marks",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                // Header info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${state.selectedExam?.examName} - ${state.selectedSubject?.subjectName}",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.selectedClassName} - ${state.selectedSection} | Max: ${state.selectedSubject?.maxTotal}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (state.hasUnsavedChanges) {
                        Text(
                            text = "Unsaved changes",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Marks spreadsheet
                MarksGrid(
                    state = state,
                    onTheoryChange = onTheoryChange,
                    onPracticalChange = onPracticalChange,
                    onToggleAbsent = onToggleAbsent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MarksLeftPanel(
    state: MarksUiState,
    onExamSelected: (ExamInfo) -> Unit,
    onSubjectSelected: (SubjectInfo) -> Unit,
    onClassSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .glassCard()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!compact) {
            Text(
                text = "Marks Entry",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Divider(color = DividerColor, thickness = 0.5.dp)
        }

        // Class selector
        SelectorDropdown(
            label = "Class",
            selectedText = if (state.selectedClassName.isNotEmpty())
                "${state.selectedClassName} - ${state.selectedSection}" else "Select",
            items = state.availableClasses.map { "${it.first} - ${it.second}" },
            onItemSelected = { index ->
                val pair = state.availableClasses[index]
                onClassSelected(pair.first, pair.second)
            }
        )

        // Exam selector
        SelectorDropdown(
            label = "Exam",
            selectedText = state.selectedExam?.examName ?: "Select Exam",
            items = state.availableExams.map { it.examName },
            onItemSelected = { index ->
                onExamSelected(state.availableExams[index])
            }
        )

        // Subject selector
        SelectorDropdown(
            label = "Subject",
            selectedText = state.selectedSubject?.subjectName ?: "Select Subject",
            items = state.availableSubjects.map { it.subjectName },
            onItemSelected = { index ->
                onSubjectSelected(state.availableSubjects[index])
            },
            enabled = state.availableSubjects.isNotEmpty()
        )

        // Max marks info — only in the wide layout; the grid header already
        // shows per-column maxes, so this card is dropped when stacked/compact.
        if (!compact) {
            state.selectedSubject?.let { subject ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TealSurface)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Max Marks",
                        style = MaterialTheme.typography.labelMedium,
                        color = Teal,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Theory:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${subject.maxTheory}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Practical:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${subject.maxPractical}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${subject.maxTotal}", style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorDropdown(
    label: String,
    selectedText: String,
    items: List<String>,
    onItemSelected: (Int) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { if (enabled) expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (enabled) TextPrimary else TextTertiary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(if (enabled) GlassBorder else GlassBorder.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = selectedText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                items.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                item,
                                color = if (item == selectedText) Teal else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onItemSelected(index)
                            expanded = false
                        }
                    )
                }
                if (items.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "No items available",
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        onClick = { expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarksGrid(
    state: MarksUiState,
    onTheoryChange: (String, String) -> Unit,
    onPracticalChange: (String, String) -> Unit,
    onToggleAbsent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    val subject = state.selectedSubject ?: return

    val rollWidth = 60.dp
    val nameWidth = 150.dp
    val markCellWidth = 80.dp
    val totalWidth = 70.dp
    val absentWidth = 50.dp
    val headerHeight = 40.dp
    val rowHeight = 44.dp

    Box(
        modifier = modifier
            .glassCard(cornerRadius = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .border(0.5.dp, DividerColor)
            ) {
                // Fixed headers
                HeaderCell("Roll No", rollWidth, headerHeight)
                HeaderCell("Student (ID)", nameWidth, headerHeight)

                // Scrollable headers
                Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    HeaderCell("Theory (${subject.maxTheory})", markCellWidth, headerHeight)
                    HeaderCell("Practical (${subject.maxPractical})", markCellWidth, headerHeight)
                    HeaderCell("Total", totalWidth, headerHeight)
                    HeaderCell("AB", absentWidth, headerHeight)
                }
            }

            // Student rows
            LazyColumn {
                items(state.studentMarks, key = { it.studentId }) { mark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, DividerColor.copy(alpha = 0.3f))
                            .background(
                                if (mark.isAbsent) ErrorRed.copy(alpha = 0.05f) else Color.Transparent
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Roll number
                        Box(
                            modifier = Modifier
                                .width(rollWidth)
                                .height(rowHeight)
                                .background(Glass.copy(alpha = 0.15f))
                                .border(0.5.dp, DividerColor.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mark.rollNo.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        // Name
                        Box(
                            modifier = Modifier
                                .width(nameWidth)
                                .height(rowHeight)
                                .background(Glass.copy(alpha = 0.08f))
                                .border(0.5.dp, DividerColor.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text(
                                    text = mark.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (mark.isAbsent) TextTertiary else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = mark.studentId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Scrollable mark fields
                        Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                            // Theory input
                            MarkInputCell(
                                value = mark.theory,
                                onValueChange = { onTheoryChange(mark.studentId, it) },
                                isError = mark.theoryError != null,
                                errorText = mark.theoryError,
                                enabled = !mark.isAbsent,
                                width = markCellWidth,
                                height = rowHeight,
                                semanticLabel = "Theory marks for ${mark.name}, roll ${mark.rollNo}"
                            )

                            // Practical input
                            MarkInputCell(
                                value = mark.practical,
                                onValueChange = { onPracticalChange(mark.studentId, it) },
                                isError = mark.practicalError != null,
                                errorText = mark.practicalError,
                                enabled = !mark.isAbsent,
                                width = markCellWidth,
                                height = rowHeight,
                                semanticLabel = "Practical marks for ${mark.name}, roll ${mark.rollNo}"
                            )

                            // Total (read-only, auto-calculated). Turns red when the
                            // combined total exceeds the subject's maxTotal (MED-6).
                            Box(
                                modifier = Modifier
                                    .width(totalWidth)
                                    .height(rowHeight)
                                    .background(
                                        when {
                                            mark.totalError != null -> ErrorRed.copy(alpha = 0.12f)
                                            mark.isAbsent -> ErrorRed.copy(alpha = 0.08f)
                                            mark.total.isNotEmpty() -> SuccessGreen.copy(alpha = 0.06f)
                                            else -> Glass.copy(alpha = 0.05f)
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        if (mark.totalError != null) ErrorRed.copy(alpha = 0.4f)
                                        else DividerColor.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mark.total.ifEmpty { "-" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        mark.totalError != null -> ErrorRed
                                        mark.isAbsent -> ErrorRed
                                        mark.total.isNotEmpty() -> SuccessGreen
                                        else -> TextTertiary
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (mark.totalError != null) {
                                    Text(
                                        text = mark.totalError!!,
                                        color = ErrorRed,
                                        fontSize = 7.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 1.dp)
                                    )
                                }
                            }

                            // Absent checkbox
                            Box(
                                modifier = Modifier
                                    .width(absentWidth)
                                    .height(rowHeight)
                                    .border(0.5.dp, DividerColor.copy(alpha = 0.3f))
                                    .semantics { contentDescription = "Toggle absent for ${mark.name}" }
                                    .clickable { onToggleAbsent(mark.studentId) },
                                contentAlignment = Alignment.Center
                            ) {
                                Checkbox(
                                    checked = mark.isAbsent,
                                    onCheckedChange = { onToggleAbsent(mark.studentId) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = ErrorRed,
                                        uncheckedColor = TextTertiary,
                                        checkmarkColor = TextPrimary
                                    ),
                                    modifier = Modifier.size(20.dp)
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
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .border(0.5.dp, DividerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun MarkInputCell(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorText: String?,
    enabled: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    semanticLabel: String = ""
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .semantics { if (semanticLabel.isNotEmpty()) contentDescription = semanticLabel }
            .background(
                when {
                    isError -> ErrorRed.copy(alpha = 0.08f)
                    !enabled -> Glass.copy(alpha = 0.03f)
                    else -> Glass.copy(alpha = 0.05f)
                }
            )
            .border(
                0.5.dp,
                when {
                    isError -> ErrorRed.copy(alpha = 0.4f)
                    else -> DividerColor.copy(alpha = 0.3f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            BasicTextField(
                value = value,
                onValueChange = { newVal ->
                    // Accept digits + one optional decimal point — marks are Double,
                    // so both "40" and "37.5" are valid (no silent truncation).
                    if (isValidMarkInput(newVal)) onValueChange(newVal)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textStyle = TextStyle(
                    color = if (isError) ErrorRed else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                cursorBrush = SolidColor(Teal),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "-",
                                color = TextTertiary.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Text(
                text = "AB",
                style = MaterialTheme.typography.labelMedium,
                color = ErrorRed.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }

        // Error indicator
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = ErrorRed,
                fontSize = 7.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
            )
        }
    }
}
