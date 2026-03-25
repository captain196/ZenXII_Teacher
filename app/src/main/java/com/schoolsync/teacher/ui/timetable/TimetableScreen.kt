package com.schoolsync.teacher.ui.timetable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.Divider
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SubjectArt
import com.schoolsync.teacher.ui.theme.SubjectComputer
import com.schoolsync.teacher.ui.theme.SubjectDefault
import com.schoolsync.teacher.ui.theme.SubjectEnglish
import com.schoolsync.teacher.ui.theme.SubjectHindi
import com.schoolsync.teacher.ui.theme.SubjectMath
import com.schoolsync.teacher.ui.theme.SubjectPhysEd
import com.schoolsync.teacher.ui.theme.SubjectScience
import com.schoolsync.teacher.ui.theme.SubjectSocial
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.glassCard
import java.util.Calendar

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Weekly Timetable",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            } else {
                // Timetable grid
                TimetableGrid(state = state)
            }
        }
    }
}

@Composable
private fun TimetableGrid(state: TimetableUiState) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    val cellWidth = 140.dp
    val cellHeight = 64.dp
    val timeColumnWidth = 80.dp

    val currentDayName = when (state.currentDay) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glassCard()
            .padding(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Fixed time column
            Column(
                modifier = Modifier
                    .width(timeColumnWidth)
                    .verticalScroll(verticalScroll)
            ) {
                // Header cell (empty corner)
                Box(
                    modifier = Modifier
                        .width(timeColumnWidth)
                        .height(44.dp)
                        .background(SurfaceDark)
                        .border(0.5.dp, Divider),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Period time cells
                state.periodTimes.forEachIndexed { index, time ->
                    val isCurrentPeriod = index == state.currentPeriod
                    Box(
                        modifier = Modifier
                            .width(timeColumnWidth)
                            .height(cellHeight)
                            .background(
                                if (isCurrentPeriod) TealSurface else SurfaceDark
                            )
                            .border(0.5.dp, Divider),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "P${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrentPeriod) Teal else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrentPeriod) Teal else TextTertiary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Scrollable day columns
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
            ) {
                // Day headers row
                Row {
                    state.days.forEach { day ->
                        val isToday = day == currentDayName
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(44.dp)
                                .background(
                                    if (isToday) Teal.copy(alpha = 0.08f) else SurfaceDark
                                )
                                .border(0.5.dp, if (isToday) Teal.copy(alpha = 0.3f) else Divider),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.take(3),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isToday) Teal else TextPrimary,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Teal)
                                    )
                                }
                            }
                        }
                    }
                }

                // Period rows
                for (periodIndex in 0 until state.totalPeriods) {
                    Row {
                        state.days.forEach { day ->
                            val entries = state.weekGrid[day] ?: emptyList()
                            val entry = entries.getOrNull(periodIndex)
                            val isCurrentCell = day == currentDayName && periodIndex == state.currentPeriod

                            TimetableCell(
                                entry = entry,
                                isCurrent = isCurrentCell,
                                width = cellWidth,
                                height = cellHeight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableCell(
    entry: TimetableEntry?,
    isCurrent: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    val subjectColor = if (entry != null) getSubjectColor(entry.subject) else SubjectDefault
    val bgColor by animateColorAsState(
        targetValue = when {
            isCurrent -> Teal.copy(alpha = 0.15f)
            entry != null -> subjectColor.copy(alpha = 0.08f)
            else -> Glass.copy(alpha = 0.15f)
        },
        label = "cellBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrent -> Teal.copy(alpha = 0.5f)
            entry != null -> subjectColor.copy(alpha = 0.2f)
            else -> Divider
        },
        label = "cellBorder"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(bgColor)
            .border(0.5.dp, borderColor),
        contentAlignment = Alignment.Center
    ) {
        if (entry != null) {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Subject color indicator + name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(subjectColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.subject,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) Teal else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "${entry.className}-${entry.section}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                if (entry.room.isNotEmpty()) {
                    Text(
                        text = entry.room,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        } else {
            Text(
                text = "-",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary.copy(alpha = 0.4f)
            )
        }
    }
}

private fun getSubjectColor(subject: String): androidx.compose.ui.graphics.Color {
    val lower = subject.lowercase()
    return when {
        lower.contains("math") || lower.contains("maths") -> SubjectMath
        lower.contains("science") || lower.contains("physics") || lower.contains("chemistry") || lower.contains("biology") -> SubjectScience
        lower.contains("english") -> SubjectEnglish
        lower.contains("hindi") || lower.contains("sanskrit") -> SubjectHindi
        lower.contains("social") || lower.contains("history") || lower.contains("geography") || lower.contains("civics") -> SubjectSocial
        lower.contains("computer") || lower.contains("it") -> SubjectComputer
        lower.contains("physical") || lower.contains("sports") || lower.contains("pt") -> SubjectPhysEd
        lower.contains("art") || lower.contains("craft") || lower.contains("draw") -> SubjectArt
        else -> SubjectDefault
    }
}
