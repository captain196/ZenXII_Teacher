package com.schoolsync.teacher.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import com.schoolsync.teacher.data.model.firestore.EventDoc
import com.schoolsync.teacher.ui.navigation.Route
import com.schoolsync.teacher.ui.theme.SuccessGreenSurface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.ErrorRedSurface
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.InfoBlue
import com.schoolsync.teacher.ui.theme.InfoBlueSurface
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.SurfaceElevated
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
    onOpenStory: (authorId: String) -> Unit = {},
    onNavigate: (route: String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    storyViewerViewModel: com.schoolsync.teacher.ui.stories.StoryViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val storyGroups by storyViewerViewModel.groups.collectAsStateWithLifecycle()
    val storiesLoading by storyViewerViewModel.isLoading.collectAsStateWithLifecycle()

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {
                // Top header bar
                DashboardHeader(
                    teacherName = state.teacherName,
                    classTeacherOf = state.classTeacherOf,
                    schedule = state.todaySchedule,
                    onRefresh = viewModel::refresh,
                    onNotificationsClick = onNotificationsClick,
                    onSearchClick = { onNavigate(Route.Search.route) }
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

                // Stories ring carousel — everyone's active stories. Tap a
                // ring to open the full-screen viewer. Shimmer while the first
                // snapshot loads; hidden once loaded with nothing to show.
                if (storyGroups.isNotEmpty()) {
                    com.schoolsync.teacher.ui.stories.StoriesSection(
                        groups = storyGroups,
                        onOpenStory = onOpenStory,
                        title = "Stories",
                        showWhenEmpty = false
                    )
                } else if (storiesLoading) {
                    StoriesRowShimmer()
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Today's Schedule (left, wider) + Today's numbers (right) ──
                // Both top-aligned on one line. The agenda lifts the live
                // period; the 2×2 glance shows the day's key counts. The outer
                // verticalScroll owns scrolling, so no panel is clipped.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ScheduleAgendaTile(
                        schedule = state.todaySchedule,
                        isError = state.scheduleError,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.weight(0.6f)
                    )
                    GlanceKpiGrid(
                        stats = state.quickStats,
                        modifier = Modifier.weight(0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Quick actions (horizontal rail) ──
                DashSectionHeader(title = "Quick actions", onViewAll = { onNavigate(Route.More.route) })
                QuickActionsRail(onNavigate = onNavigate)

                Spacer(modifier = Modifier.height(20.dp))

                // ── Upcoming events (horizontal rail) ──
                DashSectionHeader(title = "Upcoming events", onViewAll = { onNavigate(Route.Events.route) })
                if (state.upcomingEvents.isEmpty() && state.eventsLoading) {
                    // Shimmer while the (separately-launched) events fetch is
                    // still in flight, so the rail fades in instead of showing
                    // "No upcoming events" then popping to real cards.
                    EventsRailShimmer()
                } else {
                    UpcomingEventsRail(
                        events = state.upcomingEvents,
                        onClick = { onNavigate(Route.Events.route) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── All modules ──
                DashSectionHeader(title = "All modules", onViewAll = null)
                ModulesGrid(onNavigate = onNavigate)
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
    schedule: List<PeriodItem>,
    onRefresh: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            // Search box — jumps to Students (where a teacher looks people up).
            HeaderSearchBox(onClick = onSearchClick)

            // Live / next-class pill — real data from today's schedule.
            NextClassPill(schedule)

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

/** Compact search entry in the top bar — taps through to Students. */
@Composable
private fun HeaderSearchBox(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(min = 150.dp, max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Glass)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = "Search",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Live / next-class pill. Shows the ongoing class (accent-filled, "Now") if one
 * is in session, else the next upcoming class with its start time ("Next").
 * Renders nothing when there is no class left today. Real data from the
 * schedule the dashboard already loaded.
 */
@Composable
private fun NextClassPill(schedule: List<PeriodItem>) {
    val classes = schedule.filter { !it.isBreak }
    val live = classes.firstOrNull { it.status == PeriodStatus.CURRENT }
    val next = classes.firstOrNull { it.status == PeriodStatus.UPCOMING }
    val target = live ?: next ?: return
    val isNow = live != null

    val classSec = buildString {
        append(target.className.removePrefix("Class "))
        val sec = target.section.removePrefix("Section ")
        if (sec.isNotBlank()) append(" $sec")
    }
    val startTime = target.time.substringBefore("-").trim()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isNow) Teal else TealSurface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Schedule,
            null,
            tint = if (isNow) BgStart else Teal,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = buildString {
                append(if (isNow) "Now: " else "Next: ")
                append(target.subject)
                append(" · ")
                append(classSec)
                if (!isNow && startTime.isNotEmpty()) append(" · $startTime")
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isNow) BgStart else Teal,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
private fun ScheduleAgendaTile(
    schedule: List<PeriodItem>,
    isError: Boolean = false,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard()
            .heightIn(min = 180.dp)
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
            // Periods header — a tiny ring of day completion. "Done" is the
            // real count of finished class periods (status == DONE), so it
            // stays accurate all day, including after the last class ends.
            // Breaks/lunch are excluded from the count.
            val classPeriods = schedule.filter { !it.isBreak }
            if (classPeriods.isNotEmpty()) {
                val done = classPeriods.count { it.status == PeriodStatus.DONE }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dashboard_periods_count, classPeriods.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.schoolsync.teacher.ui.components.charts.ProgressRingMini(
                        progress = done.toFloat() / classPeriods.size.coerceAtLeast(1),
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

        if (isError) {
            // Distinct from the empty state: the fetch FAILED (network /
            // permission / missing index). Show an error + retry affordance
            // so the teacher knows the schedule is unknown, not absent.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Couldn't load your schedule",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRetry() }
                            .background(TealSurface)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge,
                            color = Teal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else if (schedule.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
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
            Column(modifier = Modifier.fillMaxWidth()) {
                schedule.forEachIndexed { i, period ->
                    if (i > 0) {
                        // Thin separator between two flat rows. The live row
                        // carries its own spacing (it's a lifted card), so
                        // skip dividers directly adjacent to it.
                        val prev = schedule[i - 1]
                        if (!period.isCurrent && !prev.isCurrent) {
                            HorizontalDivider(
                                color = Glass,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    when {
                        period.isBreak -> PeriodBreakRow(period)
                        period.isCurrent -> PeriodLiveRow(period)
                        else -> PeriodFlatRow(period)
                    }
                }
            }
        }
    }
}

/**
 * A finished / upcoming period in the agenda. A colored status bar (green =
 * done, muted = upcoming) precedes the time, subject, and class chip. Finished
 * periods are dimmed. Only the live period (see [PeriodLiveRow]) is raised.
 */
@Composable
private fun PeriodFlatRow(period: PeriodItem) {
    val done = period.status == PeriodStatus.DONE
    val barColor = if (done) SuccessGreen else GlassBorder
    val startTime = period.time.substringBefore("-").trim()
    val endTime = period.time.substringAfter("-", "").trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        PeriodTimeColumn(startTime, endTime, accent = false)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = period.subject,
            style = MaterialTheme.typography.titleMedium,
            color = if (done) TextTertiary else TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ClassChip(period, filled = false)
    }
}

/**
 * The period currently in session. Its whole row lifts off the list into a
 * raised card — elevation (a soft drop-shadow in light, a lighter elevated
 * surface in dark) plus an accent tint, a bold accent bar, and a pulsing
 * "In class now" cue. Elevation, not just color, makes "you are here" read at
 * a glance. Rendered only when a class is actually live.
 */
@Composable
private fun PeriodLiveRow(period: PeriodItem) {
    val startTime = period.time.substringBefore("-").trim()
    val endTime = period.time.substringAfter("-", "").trim()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceElevated,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, Teal.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Teal)
            )
            Spacer(modifier = Modifier.width(12.dp))
            PeriodTimeColumn(startTime, endTime, accent = true)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = period.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(Teal)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "In class now",
                        style = OverlineLabel,
                        color = Teal,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ClassChip(period, filled = true)
        }
    }
}

/**
 * A break / lunch slot — a slim, muted, centered marker so the gap in the day
 * is visible without competing with the class rows.
 */
@Composable
private fun PeriodBreakRow(period: PeriodItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = period.subject.ifBlank { "Break" }.uppercase(),
            style = OverlineLabel,
            color = TextTertiary
        )
        if (period.time.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = period.time.replace("-", "–"),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

/** Two-line start/end time column, shared by flat and live rows. */
@Composable
private fun PeriodTimeColumn(startTime: String, endTime: String, accent: Boolean) {
    Column(modifier = Modifier.width(52.dp)) {
        Text(
            text = startTime.ifEmpty { "—" },
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) Teal else TextSecondary,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
        if (endTime.isNotEmpty()) {
            Text(
                text = endTime,
                style = MaterialTheme.typography.labelSmall,
                color = if (accent) Teal.copy(alpha = 0.75f) else TextTertiary,
                maxLines = 1
            )
        }
    }
}

/** Class · section · period chip. Filled accent on the live row, outlined otherwise. */
@Composable
private fun ClassChip(period: PeriodItem, filled: Boolean) {
    val cls = period.className.removePrefix("Class ")
    val sec = period.section.removePrefix("Section ")
    val label = buildString {
        append(cls)
        if (sec.isNotBlank()) {
            append(" ")
            append(sec)
        }
        append(" · P")
        append(period.periodNumber)
    }
    val chipMod = if (filled) {
        Modifier.background(Teal)
    } else {
        Modifier
            .background(Glass)
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(chipMod)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) BgStart else TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

/** A softly pulsing dot — the "live" indicator on the current period. */
@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ═══════════════════════════════════════════════════════════════
// Dashboard sections: glance KPIs · quick actions · events · modules
// ═══════════════════════════════════════════════════════════════

/** An action / module tile: label, icon, destination route, and its colour. */
private data class DashItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val tint: androidx.compose.ui.graphics.Color,
    val bg: androidx.compose.ui.graphics.Color
)

/** Section header with an optional "View all ›" affordance. */
@Composable
private fun DashSectionHeader(title: String, onViewAll: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = OverlineLabel,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
        if (onViewAll != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onViewAll() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.labelMedium,
                    color = Teal,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Filled.ChevronRight, null, tint = Teal, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 2×2 "Today at a glance" KPI grid (no fabricated trend deltas). */
@Composable
private fun GlanceKpiGrid(stats: List<QuickStat>, modifier: Modifier = Modifier) {
    val dash = stringResource(R.string.stat_dash)
    val items = stats.ifEmpty {
        listOf(
            QuickStat(stringResource(R.string.stat_classes), dash, stringResource(R.string.stat_assigned)),
            QuickStat(stringResource(R.string.stat_today), dash, stringResource(R.string.stat_periods)),
            QuickStat(stringResource(R.string.stat_hw_due), dash, stringResource(R.string.stat_today_lower)),
            QuickStat(stringResource(R.string.stat_flags), dash, stringResource(R.string.stat_active))
        )
    }
    val meta = listOf(
        Triple(Icons.Filled.Class, Teal, TealSurface),
        Triple(Icons.Filled.Schedule, InfoBlue, InfoBlueSurface),
        Triple(Icons.Filled.MenuBook, WarningAmber, WarningAmberSurface),
        Triple(Icons.Filled.Flag, ErrorRed, ErrorRedSurface)
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (r in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until 2) {
                    val i = r * 2 + col
                    val s = items.getOrNull(i)
                    if (s != null) {
                        val (icon, tint, bg) = meta[i]
                        GlanceKpiCard(s, icon, tint, bg, Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceKpiCard(
    stat: QuickStat,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard(cornerRadius = 16.dp)
            .heightIn(min = 96.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(stat.value, style = MetricLarge.copy(fontSize = 26.sp), color = TextPrimary, maxLines = 1)
            Text(
                text = stat.label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Horizontal rail of one-tap quick actions. */
@Composable
private fun QuickActionsRail(onNavigate: (String) -> Unit) {
    val actions = listOf(
        DashItem("Take Attendance", Icons.Filled.HowToReg, Route.Attendance.route, SuccessGreen, SuccessGreenSurface),
        DashItem("Add Homework", Icons.Filled.MenuBook, Route.Homework.route, WarningAmber, WarningAmberSurface),
        DashItem("Enter Marks", Icons.Filled.Grade, Route.Marks.route, Teal, TealSurface),
        DashItem("Post Notice", Icons.Filled.Campaign, Route.Notices.route, InfoBlue, InfoBlueSurface),
        DashItem("Chat", Icons.AutoMirrored.Filled.Chat, Route.Messages.route, InfoBlue, InfoBlueSurface),
        DashItem("Clock In", Icons.Filled.Fingerprint, Route.MyAttendance.route, Teal, TealSurface),
        DashItem("Red Flags", Icons.Filled.Flag, Route.RedFlags.route, ErrorRed, ErrorRedSurface),
        DashItem("Apply Leave", Icons.Filled.EventNote, Route.Leave.route, InfoBlue, InfoBlueSurface)
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(actions) { a ->
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .glassCard(cornerRadius = 16.dp)
                    .clickable { onNavigate(a.route) }
                    .padding(vertical = 16.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(a.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(a.icon, null, tint = a.tint, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = a.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Dashboard skeletons (shimmer) ────────────────────────────────────────
// Lightweight pulse-alpha placeholders shown while the stories/events data
// is still arriving (both load in separate coroutines that finish AFTER the
// main dashboard spinner clears). Only composed while loading — no cost once
// data lands. Shapes mirror the real ring row / event rail so the fade-in
// doesn't shift layout.

@Composable
private fun rememberShimmerAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "dashShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dashShimmerAlpha"
    )
    return alpha
}

@Composable
private fun StoriesRowShimmer() {
    val alpha = rememberShimmerAlpha()
    val base = TextTertiary
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp)
                .width(70.dp).height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(base.copy(alpha = alpha))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(62.dp).clip(CircleShape)
                            .background(base.copy(alpha = alpha))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.width(44.dp).height(9.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(base.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun EventsRailShimmer() {
    val alpha = rememberShimmerAlpha()
    val base = TextTertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(150.dp).height(92.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(base.copy(alpha = alpha))
            )
        }
    }
}

/** Horizontal rail of upcoming school events. */
@Composable
private fun UpcomingEventsRail(events: List<EventDoc>, onClick: () -> Unit) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .glassCard(cornerRadius = 16.dp)
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No upcoming events",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(events) { e -> EventRailCard(e, onClick) }
    }
}

@Composable
private fun EventRailCard(event: EventDoc, onClick: () -> Unit) {
    val (day, mon) = remember(event.startDate) { parseEventDate(event.startDate) }
    val tint = when (event.category.lowercase()) {
        "exam", "examination", "test" -> ErrorRed
        "sports", "sport" -> WarningAmber
        "holiday" -> SuccessGreen
        "ptm", "meeting" -> InfoBlue
        else -> Teal
    }
    val bg = when (event.category.lowercase()) {
        "exam", "examination", "test" -> ErrorRedSurface
        "sports", "sport" -> WarningAmberSurface
        "holiday" -> SuccessGreenSurface
        "ptm", "meeting" -> InfoBlueSurface
        else -> TealSurface
    }
    Row(
        modifier = Modifier
            .width(258.dp)
            .height(IntrinsicSize.Min)
            .glassCard(cornerRadius = 16.dp)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(tint)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(day, style = MetricLarge.copy(fontSize = 20.sp), color = TextPrimary, maxLines = 1)
            Text(mon, style = OverlineLabel, color = TextTertiary, fontSize = 9.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (event.location.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (event.category.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = event.category.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/** "2026-07-08" → ("08", "JUL"). Blank pair when unparseable. */
private fun parseEventDate(iso: String): Pair<String, String> {
    return try {
        val p = iso.split("-")
        val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        p[2].padStart(2, '0') to months[p[1].toInt() - 1]
    } catch (_: Exception) {
        "" to ""
    }
}

/** Wrapping grid of every module — each tile jumps to its screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModulesGrid(onNavigate: (String) -> Unit) {
    val modules = listOf(
        DashItem("Attendance", Icons.Filled.HowToReg, Route.Attendance.route, Teal, TealSurface),
        DashItem("Marks", Icons.Filled.Grade, Route.Marks.route, Teal, TealSurface),
        DashItem("Homework", Icons.Filled.MenuBook, Route.Homework.route, Teal, TealSurface),
        DashItem("Timetable", Icons.Filled.Schedule, Route.Timetable.route, Teal, TealSurface),
        DashItem("Students", Icons.Filled.People, Route.Students.route, Teal, TealSurface),
        DashItem("Chat", Icons.AutoMirrored.Filled.Chat, Route.Messages.route, Teal, TealSurface),
        DashItem("Notices", Icons.Filled.Campaign, Route.Notices.route, Teal, TealSurface),
        DashItem("Leave", Icons.Filled.EventNote, Route.Leave.route, Teal, TealSurface),
        DashItem("Red Flags", Icons.Filled.Flag, Route.RedFlags.route, Teal, TealSurface),
        DashItem("My Attendance", Icons.Filled.Fingerprint, Route.MyAttendance.route, Teal, TealSurface),
        DashItem("Gallery", Icons.Filled.Image, Route.Gallery.route, Teal, TealSurface),
        DashItem("Events", Icons.Filled.Event, Route.Events.route, Teal, TealSurface),
        DashItem("Lesson Plan", Icons.Filled.Description, Route.LessonPlan.route, Teal, TealSurface),
        DashItem("Library", Icons.Filled.LocalLibrary, Route.Library.route, Teal, TealSurface),
        DashItem("PTM", Icons.Filled.Groups, Route.Ptm.route, Teal, TealSurface),
        DashItem("Payslips", Icons.Filled.Payments, Route.Payslips.route, Teal, TealSurface)
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        modules.forEach { m ->
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .glassCard(cornerRadius = 15.dp)
                    .clickable { onNavigate(m.route) }
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(m.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(m.icon, null, tint = m.tint, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = m.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 2×2 bento of the four headline stats (Classes / Today / HW Due / Flags).
 * Tiles in a row share equal height via IntrinsicSize so the grid reads as a
 * clean bento block beside the schedule timeline.
 */
@Composable
private fun StatBentoGrid(stats: List<QuickStat>) {
    val dash = stringResource(R.string.stat_dash)
    val defaultStats = stats.ifEmpty {
        listOf(
            QuickStat(stringResource(R.string.stat_classes), dash, stringResource(R.string.stat_assigned)),
            QuickStat(stringResource(R.string.stat_today), dash, stringResource(R.string.stat_periods)),
            QuickStat(stringResource(R.string.stat_hw_due), dash, stringResource(R.string.stat_today_lower)),
            QuickStat(stringResource(R.string.stat_flags), dash, stringResource(R.string.stat_active))
        )
    }

    val meta = listOf(
        Triple(Icons.Filled.Class, Teal, TealSurface),
        Triple(Icons.Filled.Schedule, InfoBlue, InfoBlueSurface),
        Triple(Icons.Filled.MenuBook, WarningAmber, WarningAmberSurface),
        Triple(Icons.Filled.Flag, ErrorRed, ErrorRedSurface)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (rowIdx in 0 until 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (colIdx in 0 until 2) {
                    val i = rowIdx * 2 + colIdx
                    val stat = defaultStats.getOrNull(i)
                    if (stat != null) {
                        val (icon, tint, bg) = meta[i]
                        StatTile(
                            stat = stat,
                            icon = icon,
                            iconTint = tint,
                            iconBg = bg,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    stat: QuickStat,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconBg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard(cornerRadius = 14.dp)
            .heightIn(min = 90.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stat.label.uppercase(),
                style = OverlineLabel,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stat.value,
                style = MetricLarge.copy(fontSize = 28.sp),
                color = TextPrimary
            )
            if (stat.subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stat.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
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
