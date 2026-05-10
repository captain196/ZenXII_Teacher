package com.schoolsync.teacher.ui.fees

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.ClassFeeOverview
import com.schoolsync.teacher.data.model.StudentFeeStatus
import com.schoolsync.teacher.ui.attendance.ClassSection
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
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FeesTeacherScreen(viewModel: FeesTeacherViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    GradientBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Screen title — matches the Parent app's "Fees" header
                // so a teacher landing here from the bottom nav reads it
                // the same way regardless of which app they opened.
                Text(
                    "Fees",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
                )

                FeesTopBar(state, viewModel::selectClass, viewModel::refresh)

                if (state.selectedClass != null && !state.isLoading) {
                    SearchBar(searchQuery, { searchQuery = it })
                }

                when {
                    state.isLoading -> LoadingState()
                    state.selectedClass == null -> EmptyState(state.availableClasses.isEmpty())
                    else -> MainContent(
                        overview = state.classOverview,
                        monthlyBreakdown = state.monthlyBreakdown,
                        annualFee = state.annualFee,
                        students = filterStudents(state.studentStatuses, searchQuery, state.selectedFilter),
                        // AnnualFeeCard needs the unfiltered roster so it
                        // can resolve student names regardless of which
                        // filter chip the teacher has active.
                        allStudents = state.studentStatuses,
                        selectedFilter = state.selectedFilter,
                        onFilter = viewModel::setFilter,
                        lastReminderByStudent = state.lastReminderByStudent,
                        defaultersById = state.defaulters.associateBy { it.studentId }
                    )
                }
            }
        }

        state.errorMessage?.let { ErrorDialog(it, viewModel::clearError) }
    }
}

// ── Loading / Empty / Error ─────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Teal)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading fee data...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun EmptyState(noClasses: Boolean) {
    com.schoolsync.teacher.ui.components.EmptyStatePro(
        icon = Icons.Filled.CurrencyRupee,
        title = if (noClasses) "No classes assigned" else "Select a class",
        description = if (noClasses)
            "Contact admin to assign classes."
        else
            "Choose a class from the dropdown above to view fee status."
    )
}

@Composable
private fun ErrorDialog(error: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error", color = TextPrimary) },
        text = { Text(error, color = TextSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Teal) } },
        containerColor = SurfaceDark, shape = RoundedCornerShape(16.dp)
    )
}

// ── Top bar ─────────────────────────────────────────────────────────────

@Composable
private fun FeesTopBar(
    state: FeesUiState,
    onClass: (ClassSection) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(GlassBorder)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    state.selectedClass?.displayName ?: "Select Class",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
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
                        onClick = { onClass(cs); expanded = false }
                    )
                }
            }
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Search bar ──────────────────────────────────────────────────────────

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glassCard(cornerRadius = 10.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(Teal),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            "Search student name or roll...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, "Clear", tint = TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Main content ────────────────────────────────────────────────────────

@Composable
private fun MainContent(
    overview: ClassFeeOverview?,
    monthlyBreakdown: List<MonthlyClassBreakdown>,
    annualFee: AnnualFeeInfo?,
    students: List<StudentFeeStatus>,
    allStudents: List<StudentFeeStatus>,
    selectedFilter: StudentFilter,
    onFilter: (StudentFilter) -> Unit,
    lastReminderByStudent: Map<String, String>,
    defaultersById: Map<String, com.schoolsync.teacher.data.model.FeeDefaulter>
) {
    // Stable keys on each top-of-screen card so rememberSaveable inside
    // CollapsibleCard can persist expanded/collapsed state across the
    // dispose-recompose cycle that LazyColumn applies when an item
    // scrolls out of and back into the viewport.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (overview != null) {
            item(key = "card-class-snapshot") { ClassSnapshotCard(overview) }
        }

        if (monthlyBreakdown.isNotEmpty()) {
            item(key = "card-monthly-collection") { MonthlyCollectionCard(monthlyBreakdown) }
        }

        if (annualFee != null) {
            item(key = "card-annual-fee") {
                AnnualFeeCard(annualFee, allStudents)
            }
        }

        // Student section header + filter pills.
        item(key = "students-header") {
            StudentsSectionHeader(
                overview = overview,
                shownCount = students.size,
                selectedFilter = selectedFilter,
                onFilter = onFilter
            )
        }

        if (students.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No students match the filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }

        items(students, key = { it.studentId }) { s ->
            StudentRow(
                s = s,
                defaulter = defaultersById[s.studentId],
                lastReminderIso = lastReminderByStudent[s.studentId]
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Collapsible card wrapper ────────────────────────────────────────────

/**
 * Glass card with a tappable header that collapses/expands its body —
 * uses the same up/down chevron idiom as student rows so the gesture
 * feels consistent across the screen. `subtitle` lets a card show a
 * one-line summary while collapsed (useful for the Annual Fee card,
 * which defaults closed because it's a niche billing case).
 */
@Composable
private fun CollapsibleCard(
    /** Stable key used for [rememberSaveable] — pass a unique value per
     *  card mount site so each card's expanded/collapsed state survives
     *  LazyColumn item disposal independently. Without a key, scrolling
     *  a card off-screen and back resets it to defaultExpanded. */
    saveKey: String,
    title: String,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = Teal,
    subtitle: String? = null,
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    // rememberSaveable (NOT plain remember) — LazyColumn disposes items
    // that scroll out of the viewport, which would otherwise reset the
    // expanded flag to defaultExpanded every time the card scrolled
    // back into view. Saveable state is kept in the parent
    // SaveableStateHolder, keyed by saveKey, so it survives dispose.
    var expanded by rememberSaveable(saveKey) { mutableStateOf(defaultExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = leadingTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                              else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                content()
            }
        }
    }
}

// ── Class Snapshot card ─────────────────────────────────────────────────

/**
 * Top-of-screen "is my class on track?" card. Single source of truth for
 * the headline number — collection % — with a contextual explanation
 * underneath. Restriction badges (exam blocked / result held) only
 * surface when their count is > 0 so the card stays calm when nothing's
 * wrong. Wrapped in [CollapsibleCard] so a teacher with a long student
 * list can fold it away once they've checked the headline.
 */
@Composable
private fun ClassSnapshotCard(o: ClassFeeOverview) {
    CollapsibleCard(
        saveKey = "class-snapshot",
        title = "Class Snapshot",
        subtitle = "${o.paidCount} of ${o.totalStudents} cleared · ${formatRs(o.totalPending)} pending",
        defaultExpanded = true
    ) {
        val pctColor = collectionTint(o.collectionPercent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${o.collectionPercent}%",
                style = MaterialTheme.typography.headlineMedium,
                color = pctColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { (o.collectionPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = pctColor,
                    trackColor = GlassBorder,
                    strokeCap = StrokeCap.Round
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${o.paidCount} of ${o.totalStudents} cleared all dues",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Money line — collected vs pending. Always shown so the teacher
        // sees the actual rupee amounts the school is chasing.
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MoneyStat("Collected", formatRs(o.totalCollected), SuccessGreen)
            MoneyStat(
                "Pending",
                formatRs(o.totalPending),
                if (o.totalPending > 0) ErrorRed else SuccessGreen,
                alignEnd = true
            )
        }

        // Restriction summary — only renders when something is non-zero.
        // Keeps the card uncluttered for healthy classes.
        if (o.examBlockedCount > 0 || o.resultWithheldCount > 0) {
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (o.examBlockedCount > 0) {
                    RestrictionPill(
                        icon = Icons.Filled.EventBusy,
                        label = "${o.examBlockedCount} exam blocked",
                        color = ErrorRed,
                        bg = ErrorRedSurface
                    )
                }
                if (o.resultWithheldCount > 0) {
                    RestrictionPill(
                        icon = Icons.Filled.Block,
                        label = "${o.resultWithheldCount} result held",
                        color = WarningAmber,
                        bg = WarningAmberSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyStat(label: String, value: String, color: Color, alignEnd: Boolean = false) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontSize = 10.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RestrictionPill(icon: ImageVector, label: String, color: Color, bg: Color) {
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Monthly Collection card ─────────────────────────────────────────────

/**
 * Vertical list of months with a progress bar each. Replaces the old
 * horizontally-scrolling cluster of tiny "Apr 1/3" chips that read
 * unprofessionally. Yearly demands are excluded here — they get their
 * own [AnnualFeeCard] so collection % isn't muddled by a single
 * one-time fee.
 */
@Composable
private fun MonthlyCollectionCard(items: List<MonthlyClassBreakdown>) {
    // Compute a quick "best/worst month" hint for the collapsed subtitle —
    // gives the teacher at-a-glance value even when the card is folded.
    val totalPaid = items.sumOf { it.paidStudents }
    val totalDemands = items.sumOf { it.totalStudents }
    val avgPct = if (totalDemands > 0) (totalPaid * 100) / totalDemands else 0
    CollapsibleCard(
        saveKey = "monthly-collection",
        title = "Monthly Collection",
        subtitle = "$avgPct% across ${items.size} ${if (items.size == 1) "month" else "months"}",
        leadingIcon = Icons.Filled.CalendarMonth,
        leadingTint = Teal,
        defaultExpanded = true
    ) {
        items.forEachIndexed { index, mb ->
            MonthRow(mb)
            if (index < items.size - 1) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MonthRow(mb: MonthlyClassBreakdown) {
    val pct = mb.percent
    val color = collectionTint(pct)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            mb.month,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(78.dp)
        )
        LinearProgressIndicator(
            progress = { (pct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = GlassBorder,
            strokeCap = StrokeCap.Round
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$pct%",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
        Text(
            "${mb.paidStudents}/${mb.totalStudents}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}

// ── Annual Fee card ─────────────────────────────────────────────────────

/**
 * Card for students whose annual fee is a SEPARATE demand (Pattern B
 * billing — see forensic doc). Most students get their annual fee
 * bundled into April and never appear here; this card surfaces the
 * niche group whose annual fee is billed standalone, so a teacher can
 * call/follow up on those parents specifically.
 *
 * Default-collapsed because Pattern B is rare. When expanded the body
 * lists each affected student by name with their yearly-fee status —
 * far more useful than an aggregate "Yearly: 0/2" bar that just
 * duplicates the subtitle.
 *
 * Card doesn't mount at all when no Pattern B demands exist
 * (annualFee == null), so Pattern-A-only classes never see it.
 */
@Composable
private fun AnnualFeeCard(
    info: AnnualFeeInfo,
    studentStatuses: List<StudentFeeStatus>
) {
    val statusById = remember(studentStatuses) { studentStatuses.associateBy { it.studentId } }
    CollapsibleCard(
        saveKey = "annual-fee",
        title = "Annual Fee",
        subtitle = "${info.paidStudents} of ${info.totalStudents} paid · separate yearly billing",
        leadingIcon = Icons.Filled.Star,
        leadingTint = WarningAmber,
        defaultExpanded = false
    ) {
        info.students.forEachIndexed { index, s ->
            val ss = statusById[s.studentId]
            AnnualFeeStudentRow(
                rollNo = ss?.rollNo.orEmpty(),
                name = ss?.studentName?.takeIf { it.isNotBlank() } ?: s.studentName,
                fatherName = ss?.fatherName.orEmpty(),
                isPaid = s.isPaid,
                balance = s.balance
            )
            if (index < info.students.size - 1) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AnnualFeeStudentRow(
    rollNo: String,
    name: String,
    fatherName: String,
    isPaid: Boolean,
    balance: Double
) {
    val statusColor = if (isPaid) SuccessGreen else ErrorRed
    val statusBg    = if (isPaid) SuccessGreenSurface else ErrorRedSurface
    val statusText  = if (isPaid) "Paid" else formatRs(balance) + " due"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RollBadge(rollNo)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name.ifBlank { "Unknown student" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (fatherName.isNotBlank()) {
                Text(
                    "F: $fatherName",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .background(statusBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Students section ────────────────────────────────────────────────────

/**
 * Header above the student list — section title, count of currently
 * shown rows, and the unified filter pill row. Replaces the old
 * Overview/Defaulters toggle + All/Clear/Dues chips with one consistent
 * control surface.
 */
@Composable
private fun StudentsSectionHeader(
    overview: ClassFeeOverview?,
    shownCount: Int,
    selectedFilter: StudentFilter,
    onFilter: (StudentFilter) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Students",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$shownCount shown",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }

        val total = overview?.totalStudents ?: 0
        val clear = overview?.clearCount ?: 0
        val dues = overview?.defaulterCount ?: 0
        val blocked = overview?.examBlockedCount ?: 0
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FilterPill("All", total, selectedFilter == StudentFilter.ALL, Teal) {
                onFilter(StudentFilter.ALL)
            }
            FilterPill("Clear", clear, selectedFilter == StudentFilter.CLEAR, SuccessGreen) {
                onFilter(StudentFilter.CLEAR)
            }
            FilterPill("Dues", dues, selectedFilter == StudentFilter.DUES, ErrorRed) {
                onFilter(StudentFilter.DUES)
            }
            // Blocked filter only shown when the class actually has any
            // — clutters the row otherwise.
            if (blocked > 0) {
                FilterPill("Blocked", blocked, selectedFilter == StudentFilter.BLOCKED, WarningAmber) {
                    onFilter(StudentFilter.BLOCKED)
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) color.copy(alpha = 0.5f) else GlassBorder,
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) color else TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        if (selected) color.copy(alpha = 0.25f) else GlassBorder.copy(alpha = 0.4f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) color else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Student row (inline-expanding) ──────────────────────────────────────

/**
 * Single student row that expands inline on tap to reveal restriction
 * badges, unpaid months ("Yearly Fees" relabelled to "Annual"), parent
 * phone, and the most recent fee-reminder if one was sent. Inline beats
 * a modal here because most teacher actions are visual (skim the
 * unpaid-month chips, glance at the call icon) — a modal interrupts
 * that scan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentRow(
    s: StudentFeeStatus,
    defaulter: com.schoolsync.teacher.data.model.FeeDefaulter?,
    lastReminderIso: String?
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val statusColor = if (s.isDefaulter) ErrorRed else SuccessGreen
    val statusBg    = if (s.isDefaulter) ErrorRedSurface else SuccessGreenSurface
    val statusLabel = if (s.totalPending > 0) formatRs(s.totalPending) else "Clear"
    val reminderLabel = remember(lastReminderIso) { formatReminderAge(lastReminderIso) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 12.dp)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Header row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RollBadge(s.rollNo)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.studentName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (s.examBlocked) {
                        Spacer(Modifier.width(6.dp))
                        SmallBadge("Blocked", ErrorRed, ErrorRedSurface)
                    }
                    if (reminderLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        SmallBadge(reminderLabel, Teal, TealSurface)
                    }
                }
                if (s.fatherName.isNotBlank()) {
                    Text(
                        "F: ${s.fatherName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // Right side: status pill + chevron + (optional) call.
            Box(
                modifier = Modifier
                    .background(statusBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                              else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp).padding(start = 4.dp)
            )
        }

        // Expanded section.
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Restriction badges.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        s.examBlocked -> SmallBadge("Exam Blocked", ErrorRed, ErrorRedSurface)
                        else -> SmallBadge("Exam Eligible", SuccessGreen, SuccessGreenSurface)
                    }
                    if (s.resultWithheld) {
                        SmallBadge("Result Held", WarningAmber, WarningAmberSurface)
                    }
                }

                // Phone — tappable.
                if (s.phone.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}"))
                                )
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Call, null, tint = Teal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            s.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Unpaid months — only for actual defaulters with non-empty
                // unpaid-month list. "Yearly Fees" is relabelled to "Annual"
                // so the chip row matches what's shown on the parent app.
                val unpaid = defaulter?.unpaidMonths.orEmpty()
                if (s.isDefaulter && unpaid.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Unpaid:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        unpaid.forEach { m ->
                            val display = if (m == "Yearly Fees") "Annual" else m
                            val isAnnual = m == "Yearly Fees"
                            val chipColor = if (isAnnual) WarningAmber else WarningAmber
                            val chipBg    = if (isAnnual) WarningAmberSurface else WarningAmberSurface
                            Box(
                                modifier = Modifier
                                    .background(chipBg, RoundedCornerShape(6.dp))
                                    .border(1.dp, chipColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    display,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chipColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Last payment.
                if (s.lastPaymentDate.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Last payment:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.lastPaymentDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────

@Composable
private fun RollBadge(roll: String) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Glass, RoundedCornerShape(8.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            roll.ifBlank { "-" },
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SmallBadge(text: String, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp
        )
    }
}

// ── Utility ─────────────────────────────────────────────────────────────

/**
 * Shared red/amber/green tinting for any "% complete" surface — class
 * snapshot, monthly bars, annual bar. Keeps the visual language
 * consistent so a teacher can scan the screen at a glance.
 *
 * Marked @ReadOnlyComposable because the underlying theme tokens
 * (SuccessGreen / WarningAmber / ErrorRed) are themselves @Composable
 * getters that read LocalAppColors. Call sites are already inside
 * composables so this carries no extra cost.
 */
@Composable
@ReadOnlyComposable
private fun collectionTint(percent: Int): Color = when {
    percent >= 80 -> SuccessGreen
    percent >= 50 -> WarningAmber
    else          -> ErrorRed
}

private fun formatRs(amount: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    fmt.maximumFractionDigits = 0
    return fmt.format(amount)
}

/**
 * Phase 8B: convert a feeReminderLog.sent_date into a short "Xh ago"
 * label. Returns null for entries older than 7 days so the row doesn't
 * render stale history badges. Accepts either "YYYY-MM-DD HH:mm:ss"
 * (Fee_management::send_reminder's $now) or ISO-8601 (deliveredAt).
 */
private fun formatReminderAge(sentIso: String?): String? {
    if (sentIso.isNullOrBlank()) return null
    val ts = try {
        when {
            sentIso.contains('T') ->
                java.time.OffsetDateTime.parse(sentIso).toInstant().toEpochMilli()
            else ->
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .apply { timeZone = java.util.TimeZone.getDefault() }
                    .parse(sentIso)?.time ?: return null
        }
    } catch (e: Exception) { return null }

    val ageMin = (System.currentTimeMillis() - ts) / 60000L
    if (ageMin < 0) return null
    if (ageMin > 60 * 24 * 7) return null
    return when {
        ageMin < 1     -> "Just reminded"
        ageMin < 60    -> "Reminded ${ageMin}m"
        ageMin < 60*24 -> "Reminded ${ageMin / 60}h"
        else           -> "Reminded ${ageMin / (60 * 24)}d"
    }
}

private fun filterStudents(
    list: List<StudentFeeStatus>,
    query: String,
    filter: StudentFilter
): List<StudentFeeStatus> {
    var r = list
    if (query.isNotBlank()) {
        val lq = query.lowercase()
        r = r.filter {
            it.studentName.lowercase().contains(lq) ||
            it.rollNo.lowercase().contains(lq)
        }
    }
    return when (filter) {
        StudentFilter.ALL -> r
        StudentFilter.CLEAR -> r.filter { !it.isDefaulter && it.totalPending <= 0 }
        StudentFilter.DUES -> r.filter { it.isDefaulter || it.totalPending > 0 }
        StudentFilter.BLOCKED -> r.filter { it.examBlocked }
    }
}
