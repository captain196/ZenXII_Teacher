package com.schoolsync.teacher.ui.results

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.marks.ExamInfo
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
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

@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(containerColor = Color.Transparent) { paddingValues ->
            val panel: @Composable (Boolean, Modifier) -> Unit = { compact, m ->
                ResultsSelectorPanel(
                    state = state,
                    onClassSelected = viewModel::selectClass,
                    onExamSelected = viewModel::selectExam,
                    modifier = m,
                    compact = compact
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val isNarrow = maxWidth < 600.dp
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
                        ResultsContent(
                            state = state,
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
                                .width(240.dp)
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                        )
                        ResultsContent(
                            state = state,
                            onRetry = viewModel::retry,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSelectorPanel(
    state: ResultsUiState,
    onClassSelected: (String, String) -> Unit,
    onExamSelected: (ExamInfo) -> Unit,
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
                text = "Results",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Divider(color = DividerColor, thickness = 0.5.dp)
        }

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

        SelectorDropdown(
            label = "Exam",
            selectedText = state.selectedExam?.examName ?: "Select Exam",
            items = state.availableExams.map { it.examName },
            onItemSelected = { index -> onExamSelected(state.availableExams[index]) },
            enabled = state.availableExams.isNotEmpty()
        )
    }
}

@Composable
private fun ResultsContent(
    state: ResultsUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            }
            state.error != null -> {
                CenteredMessage(
                    icon = Icons.Filled.ErrorOutline,
                    tint = ErrorRed,
                    title = state.error,
                    subtitle = null,
                    action = {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = BgStart),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Retry", fontWeight = FontWeight.SemiBold) }
                    }
                )
            }
            state.selectedExam == null -> {
                CenteredMessage(
                    icon = Icons.Filled.Leaderboard,
                    tint = TextTertiary,
                    title = "Select class and exam",
                    subtitle = "Choose an exam to view published results"
                )
            }
            state.notPublished -> {
                CenteredMessage(
                    icon = Icons.Filled.HelpOutline,
                    tint = WarningAmber,
                    title = "Results not published yet",
                    subtitle = "This exam's results are still a draft and haven't been published."
                )
            }
            else -> {
                // Header
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(
                        text = state.selectedExam.examName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.selectedClassName} - ${state.selectedSection} | ${state.results.size} students",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                val expanded = remember { mutableStateMapOf<String, Boolean>() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.results, key = { it.studentId }) { row ->
                        ResultRowCard(
                            row = row,
                            expanded = expanded[row.studentId] == true,
                            onToggle = {
                                expanded[row.studentId] = !(expanded[row.studentId] == true)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRowCard(
    row: ResultRow,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 12.dp)
            .clickable { onToggle() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Rank badge
            val rankText = if (row.rank > 0) row.rank.toString() else "—"
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TealSurface)
                    .semantics {
                        contentDescription = if (row.rank > 0) "Rank ${row.rank}" else "Rank not available"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rankText,
                    style = MaterialTheme.typography.titleSmall,
                    color = Teal,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(10.dp))

            // Name + roll
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Roll ${row.rollNo.ifBlank { "—" }} · ${row.cells.totalText} · ${row.cells.percentText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Grade
            Text(
                text = row.cells.gradeText,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.cells.gradeText == "—") TextTertiary else TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )

            // Pass/Fail chip
            PassFailChip(cells = row.cells)

            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse subjects" else "Expand subjects",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Divider(color = DividerColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))
                if (row.subjects.isEmpty()) {
                    Text(
                        text = "No subject breakdown available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                } else {
                    row.subjects.forEach { subject ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = subject.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subject.cells.totalText,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = subject.cells.gradeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (subject.cells.gradeText == "—") TextTertiary else TextPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.End
                            )
                            Spacer(Modifier.width(8.dp))
                            PassFailChip(cells = subject.cells)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassFailChip(cells: ResultDisplayCells) {
    val (bg, fg, icon) = when (cells.passState) {
        PassState.PASS -> Triple(SuccessGreen.copy(alpha = 0.14f), SuccessGreen, Icons.Filled.CheckCircle)
        PassState.FAIL -> Triple(ErrorRed.copy(alpha = 0.14f), ErrorRed, Icons.Filled.Cancel)
        PassState.ABSENT -> Triple(WarningAmber.copy(alpha = 0.14f), WarningAmber, Icons.Filled.HelpOutline)
        PassState.NONE -> Triple(TextTertiary.copy(alpha = 0.12f), TextTertiary, Icons.Filled.HelpOutline)
    }
    // Text label carries the signal (not color alone); single content description
    // for the whole chip so a screen reader announces the outcome once.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = "Result: ${cells.passLabel}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = cells.passLabel,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    action: (@Composable () -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
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
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
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
