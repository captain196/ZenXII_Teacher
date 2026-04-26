package com.schoolsync.teacher.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.R
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.LocalAppColors
import com.schoolsync.teacher.ui.theme.MetricLarge
import com.schoolsync.teacher.ui.theme.OverlineLabel
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
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.WarningAmberSurface
import com.schoolsync.teacher.ui.theme.glassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onNotificationsClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh when the dashboard becomes visible again (e.g. after coming
    // back from Red Flags screen) so counts like "Flags: N active" reflect
    // any flag the teacher just created or deleted. Skips the very first
    // resume — init already loaded everything once.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (firstResume) {
                    firstResume = false
                } else {
                    viewModel.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GradientBackground {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar
                DashboardHeader(
                    teacherName = state.teacherName,
                    classTeacherOf = state.classTeacherOf,
                    onRefresh = viewModel::refresh,
                    onNotificationsClick = onNotificationsClick
                )

                // Substitute info banner
                state.substituteInfo?.let { subInfo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WarningAmber.copy(alpha = 0.12f))
                            .border(
                                width = 1.dp,
                                color = WarningAmber.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Substitute covering your classes: $subInfo",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }

                // Two-column landscape layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left panel: Today's Schedule
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    ) {
                        SchedulePanel(schedule = state.todaySchedule)
                    }

                    // Right panel: Stats + Activity
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        // Quick stats row
                        QuickStatsRow(stats = state.quickStats)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Phase 10f: Today's class attendance widget
                        if (state.todayAttendance.totalStudents > 0) {
                            TodayAttendanceWidget(state.todayAttendance)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Recent activity
                        RecentActivityPanel(
                            activities = state.recentActivity,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Error overlay
        state.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .glassCard(cornerRadius = 12.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = error,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    teacherName: String,
    classTeacherOf: List<String>,
    onRefresh: () -> Unit,
    onNotificationsClick: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val dateFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    val today = dateFormat.format(Date())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        c.accent.copy(alpha = 0.15f),
                        c.accentSecondary.copy(alpha = 0.08f),
                        c.glass
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (teacherName.isNotEmpty())
                        stringResource(R.string.dashboard_greeting, teacherName)
                    else
                        stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = today,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (classTeacherOf.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ClassTeacherBadgeRow(sections = classTeacherOf)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = onNotificationsClick, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.cd_notifications),
                            tint = c.accent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pill row shown under the date when the logged-in teacher is the
 * designated class teacher of one or more sections. Sourced from the
 * canonical `subjectAssignments.isClassTeacher` flag via the dashboard VM.
 */
@Composable
private fun ClassTeacherBadgeRow(sections: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Teal.copy(alpha = 0.18f))
            .border(
                width = 1.dp,
                color = Teal.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Class,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = stringResource(
                R.string.dashboard_class_teacher_one,
                if (sections.size == 1) sections[0] else sections.joinToString("  •  ")
            ),
            color = Teal,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SchedulePanel(schedule: List<PeriodItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .glassCard()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_today_schedule),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // Periods header — tiny progress ring shows day completion at a
            // glance. We approximate "completed" as the index of the current
            // period (everything before it is done). When no current period
            // is detected we fall back to "0 done", so the ring still renders
            // meaningfully without inventing data.
            if (schedule.isNotEmpty()) {
                val currentIdx = schedule.indexOfFirst { it.isCurrent }
                val done = if (currentIdx >= 0) currentIdx else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dashboard_periods_count, schedule.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.schoolsync.teacher.ui.components.charts.ProgressRingMini(
                        progress = done.toFloat() / schedule.size.coerceAtLeast(1),
                        diameter = 28.dp,
                        strokeWidth = 3.dp,
                        showPercent = false,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.dashboard_periods_count, schedule.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (schedule.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.dashboard_no_classes_today),
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
                items(schedule) { period ->
                    PeriodCard(period = period)
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(period: PeriodItem) {
    val bgColor by animateColorAsState(
        targetValue = if (period.isCurrent) TealSurface else Glass.copy(alpha = 0.3f),
        label = "periodBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (period.isCurrent) Teal.copy(alpha = 0.4f) else GlassBorder,
        label = "periodBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Period number badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (period.isCurrent) Teal else Glass),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.dashboard_period_label, period.periodNumber),
                style = MaterialTheme.typography.labelLarge,
                color = if (period.isCurrent) BgStart else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = period.subject,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${period.className.removePrefix("Class ")} - ${period.section.removePrefix("Section ")}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
        }

        Text(
            text = period.time,
            style = MaterialTheme.typography.labelMedium,
            color = if (period.isCurrent) Teal else TextTertiary
        )

        if (period.isCurrent) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Teal)
            )
        }
    }
}

@Composable
private fun QuickStatsRow(stats: List<QuickStat>) {
    val dash = stringResource(R.string.stat_dash)
    val defaultStats = stats.ifEmpty {
        listOf(
            QuickStat(stringResource(R.string.stat_classes), dash, stringResource(R.string.stat_assigned)),
            QuickStat(stringResource(R.string.stat_today), dash, stringResource(R.string.stat_periods)),
            QuickStat(stringResource(R.string.stat_hw_due), dash, stringResource(R.string.stat_today_lower)),
            QuickStat(stringResource(R.string.stat_flags), dash, stringResource(R.string.stat_active))
        )
    }

    val icons = listOf(
        Icons.Filled.Class to TealSurface,
        Icons.Filled.Schedule to InfoBlueSurface,
        Icons.Filled.MenuBook to WarningAmberSurface,
        Icons.Filled.Flag to ErrorRedSurface
    )
    val iconTints = listOf(Teal, InfoBlue, WarningAmber, ErrorRed)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        defaultStats.forEachIndexed { index, stat ->
            val (icon, surfaceColor) = icons.getOrElse(index) {
                Icons.Filled.Class to TealSurface
            }
            val tint = iconTints.getOrElse(index) { Teal }

            StatCard(
                stat = stat,
                icon = icon,
                iconTint = tint,
                iconBg = surfaceColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    stat: QuickStat,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconBg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard(cornerRadius = 12.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stat.label.uppercase(),
                style = OverlineLabel,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stat.value,
            style = MetricLarge.copy(fontSize = 24.sp),
            color = TextPrimary
        )
        if (stat.subtitle.isNotEmpty()) {
            Text(
                text = stat.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun RecentActivityPanel(
    activities: List<ActivityItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dashboard_recent_activity),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dashboard_no_recent_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(activities) { activity ->
                    ActivityRow(activity = activity)
                    Divider(color = DividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: ActivityItem) {
    val (icon, color) = when (activity.type) {
        ActivityType.ATTENDANCE -> Icons.Filled.CheckCircle to SuccessGreen
        ActivityType.MARKS -> Icons.Filled.Edit to InfoBlue
        ActivityType.NOTICE -> Icons.Filled.Campaign to WarningAmber
        ActivityType.INFO -> Icons.Filled.Notifications to TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = activity.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = activity.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Phase 10f: Today's Class Attendance Widget
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TodayAttendanceWidget(att: TodayAttendanceSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today's Attendance",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            // Donut viz of present / absent / leave — small enough to sit
            // in the header without disrupting the existing progress bar
            // and stat-dots layout below.
            val total = att.present + att.absent + att.tardy + att.leave
            if (total > 0) {
                com.schoolsync.teacher.ui.components.charts.DonutMini(
                    segments = listOf(
                        (att.present + att.tardy).toFloat() to SuccessGreen,
                        att.absent.toFloat() to ErrorRed,
                        att.leave.toFloat() to WarningAmber,
                    ),
                    diameter = 56.dp,
                    strokeWidth = 7.dp,
                    centerLabel = "${att.percentage.toInt()}%",
                )
            } else {
                Text(
                    "${att.percentage.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = Teal,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        val total = att.present + att.absent + att.tardy + att.leave
        if (total > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Glass)
            ) {
                val presentFraction = (att.present + att.tardy).toFloat() / total
                val absentFraction = att.absent.toFloat() / total
                val leaveFraction = att.leave.toFloat() / total

                if (presentFraction > 0) {
                    Box(
                        modifier = Modifier
                            .weight(presentFraction)
                            .fillMaxHeight()
                            .background(SuccessGreen)
                    )
                }
                if (absentFraction > 0) {
                    Box(
                        modifier = Modifier
                            .weight(absentFraction)
                            .fillMaxHeight()
                            .background(ErrorRed)
                    )
                }
                if (leaveFraction > 0) {
                    Box(
                        modifier = Modifier
                            .weight(leaveFraction)
                            .fillMaxHeight()
                            .background(WarningAmber)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stat dots row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttDot(color = SuccessGreen, count = att.present + att.tardy, label = "Present")
            AttDot(color = ErrorRed, count = att.absent, label = "Absent")
            AttDot(color = WarningAmber, count = att.leave, label = "Leave")
            AttDot(color = TextTertiary, count = att.unmarked, label = "Unmarked")
        }
    }
}

@Composable
private fun AttDot(color: Color, count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
        }
        Text(label, fontSize = 10.sp, color = TextTertiary)
    }
}
