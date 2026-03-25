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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.schoolsync.teacher.data.model.FeeDefaulter
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
    var filterMode by remember { mutableStateOf("all") }
    var studentDetailDialog by remember { mutableStateOf<StudentFeeStatus?>(null) }
    var defaulterDetailDialog by remember { mutableStateOf<FeeDefaulter?>(null) }

    GradientBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                FeesTopBar(state, viewModel::selectClass, viewModel::switchView, viewModel::refresh)

                if (state.selectedClass != null && !state.isLoading) {
                    SearchFilterBar(searchQuery, { searchQuery = it }, filterMode, { filterMode = it },
                        state.studentStatuses.size, state.defaulters.size)
                }

                when {
                    state.isLoading -> LoadingState()
                    state.selectedClass == null -> EmptyState(state.availableClasses.isEmpty())
                    state.selectedView == "summary" -> SummaryView(
                        overview = state.classOverview,
                        students = filterStudents(state.studentStatuses, searchQuery, filterMode),
                        onStudentClick = { studentDetailDialog = it },
                        onCardClick = { when (it) {
                            "defaulters" -> viewModel.switchView("defaulters")
                            "paid" -> { filterMode = "paid"; searchQuery = "" }
                            "pending" -> { filterMode = "defaulters"; searchQuery = "" }
                        }}
                    )
                    state.selectedView == "defaulters" -> DefaultersView(
                        defaulters = filterDefaulters(state.defaulters, searchQuery),
                        onDefaulterClick = { defaulterDetailDialog = it }
                    )
                }
            }
        }

        state.errorMessage?.let { ErrorDialog(it, viewModel::clearError) }
        studentDetailDialog?.let { StudentDetailDialog(it) { studentDetailDialog = null } }
        defaulterDetailDialog?.let { DefaulterDetailDialog(it) { defaulterDetailDialog = null } }
    }
}

// ── States ──────────────────────────────────────────────────────────────

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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CurrencyRupee, null, tint = TextTertiary, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(if (noClasses) "No classes assigned" else "Select a class",
                style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Text(if (noClasses) "Contact admin to assign classes" else "Choose a class from the dropdown above",
                style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
    }
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

// ── Top Bar ─────────────────────────────────────────────────────────────

@Composable
private fun FeesTopBar(state: FeesUiState, onClass: (ClassSection) -> Unit, onView: (String) -> Unit, onRefresh: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(GlassBorder)),
                shape = RoundedCornerShape(10.dp)) {
                Text(state.selectedClass?.displayName ?: "Select Class",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(SurfaceDark)) {
                state.availableClasses.forEach { cs ->
                    DropdownMenuItem(
                        text = { Text(cs.displayName, color = if (cs == state.selectedClass) Teal else TextPrimary) },
                        onClick = { onClass(cs); expanded = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ToggleBtn("Overview", state.selectedView == "summary") { onView("summary") }
            ToggleBtn("Defaulters", state.selectedView == "defaulters") { onView("defaulters") }
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ToggleBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Teal.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (selected) Teal else TextSecondary),
        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(if (selected) Teal.copy(0.5f) else GlassBorder)),
        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Search + Filter ─────────────────────────────────────────────────────

@Composable
private fun SearchFilterBar(query: String, onQuery: (String) -> Unit, filter: String, onFilter: (String) -> Unit, total: Int, defaulters: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 10.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(value = query, onValueChange = onQuery, modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp), cursorBrush = SolidColor(Teal), singleLine = true,
                decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search student name or roll...", style = MaterialTheme.typography.bodySmall, color = TextTertiary); inner() } })
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, "Clear", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Chip("All ($total)", filter == "all") { onFilter("all") }
            Chip("Clear (${total - defaulters})", filter == "paid", color = SuccessGreen) { onFilter("paid") }
            Chip("Dues ($defaulters)", filter == "defaulters", color = ErrorRed) { onFilter("defaulters") }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, color: Color = Teal, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
        .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
        .border(1.dp, if (selected) color.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (selected) color else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Summary / Overview ──────────────────────────────────────────────────

@Composable
private fun SummaryView(overview: ClassFeeOverview?, students: List<StudentFeeStatus>,
                        onStudentClick: (StudentFeeStatus) -> Unit, onCardClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        if (overview != null) { item { ClassReadinessCard(overview, onCardClick) } }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Students", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${students.size} shown", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }

        if (students.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No students match the filter", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            }}
        }

        items(students, key = { it.studentId }) { s -> StudentRow(s) { onStudentClick(s) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Teacher-centric overview: "Is my class ready for exams?" */
@Composable
private fun ClassReadinessCard(o: ClassFeeOverview, onCardClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(14.dp)) {
        // Title
        Text("Class Readiness", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // Collection progress bar
        val pct = o.collectionPercent / 100f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${o.collectionPercent}%", style = MaterialTheme.typography.titleMedium, color = when {
                o.collectionPercent >= 80 -> SuccessGreen; o.collectionPercent >= 50 -> WarningAmber; else -> ErrorRed
            }, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when { o.collectionPercent >= 80 -> SuccessGreen; o.collectionPercent >= 50 -> WarningAmber; else -> ErrorRed },
                trackColor = GlassBorder, strokeCap = StrokeCap.Round)
            Spacer(Modifier.width(8.dp))
            Text("fees cleared", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }

        Spacer(Modifier.height(12.dp))

        // Stat cards row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Modifier.weight(1f), "Exam Ready", "${o.examReadyCount}/${o.totalStudents}",
                Icons.Filled.School, SuccessGreen, SuccessGreenSurface, null)
            StatCard(Modifier.weight(1f), "Exam Blocked", "${o.examBlockedCount}",
                Icons.Filled.EventBusy, if (o.examBlockedCount > 0) ErrorRed else SuccessGreen,
                if (o.examBlockedCount > 0) ErrorRedSurface else SuccessGreenSurface, null)
            StatCard(Modifier.weight(1f), "Fees Clear", "${o.clearCount}/${o.totalStudents}",
                Icons.Filled.CheckCircle, Teal, TealSurface) { onCardClick("paid") }
            StatCard(Modifier.weight(1f), "Dues Pending", "${o.defaulterCount}",
                Icons.Filled.Warning, if (o.defaulterCount > 0) WarningAmber else SuccessGreen,
                if (o.defaulterCount > 0) WarningAmberSurface else SuccessGreenSurface) { onCardClick("defaulters") }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, icon: ImageVector,
                     iconTint: Color, bgColor: Color, onClick: (() -> Unit)?) {
    Column(modifier = modifier.background(bgColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodySmall, color = TextTertiary, fontSize = 10.sp, maxLines = 1)
    }
}

// ── Student Row ─────────────────────────────────────────────────────────

@Composable
private fun StudentRow(s: StudentFeeStatus, onClick: () -> Unit) {
    val color = if (s.isDefaulter) ErrorRed else SuccessGreen
    val bg = if (s.isDefaulter) ErrorRedSurface else SuccessGreenSurface
    val label = if (s.totalPending > 0) formatRs(s.totalPending) else "Clear"

    Row(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 10.dp).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            RollBadge(s.rollNo)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(s.studentName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.fatherName.isNotBlank()) {
                    Text("F: ${s.fatherName}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, fontSize = 10.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.examBlocked) Badge("Exam Blocked", ErrorRed, ErrorRedSurface)
            if (s.resultWithheld) Badge("Result Held", WarningAmber, WarningAmberSurface)
            Box(modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Defaulters View ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultersView(defaulters: List<FeeDefaulter>, onDefaulterClick: (FeeDefaulter) -> Unit) {
    if (defaulters.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("All Clear!", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                Text("Every student has cleared their fees", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }
        return
    }

    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Fee Defaulters", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${defaulters.size} students", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
            }
        }

        items(defaulters, key = { it.studentId }) { d ->
            DefaulterCard(d, onCallParent = { phone ->
                if (phone.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    context.startActivity(intent)
                }
            }, onViewDetails = { onDefaulterClick(d) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaulterCard(d: FeeDefaulter, onCallParent: (String) -> Unit, onViewDetails: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 10.dp)
        .clickable { expanded = !expanded }.padding(12.dp)) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RollBadge(d.rollNo)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(d.studentName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.fatherName.isNotBlank()) {
                        Text("Parent: ${d.fatherName}", style = MaterialTheme.typography.bodySmall, color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Call parent button
                if (d.phone.isNotBlank()) {
                    IconButton(onClick = { onCallParent(d.phone) }, modifier = Modifier.size(30.dp)
                        .background(TealSurface, CircleShape)) {
                        Icon(Icons.Filled.Call, "Call parent", tint = Teal, modifier = Modifier.size(16.dp))
                    }
                }
                Box(modifier = Modifier.background(ErrorRedSurface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(formatRs(d.totalDues), style = MaterialTheme.typography.labelMedium,
                        color = ErrorRed, fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    "Expand", tint = TextTertiary, modifier = Modifier.size(20.dp))
            }
        }

        // Expanded details
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Flags
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (d.examBlocked) Badge("Exam Blocked", ErrorRed, ErrorRedSurface)
                    if (d.resultWithheld) Badge("Result Withheld", WarningAmber, WarningAmberSurface)
                    if (!d.examBlocked && !d.resultWithheld) Badge("No restrictions", SuccessGreen, SuccessGreenSurface)
                }

                // Parent contact info
                if (d.phone.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCallParent(d.phone) }) {
                        Icon(Icons.Filled.Call, null, tint = Teal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(d.phone, style = MaterialTheme.typography.bodySmall, color = Teal, fontWeight = FontWeight.Medium)
                    }
                }

                // Unpaid months
                if (d.unpaidMonths.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Unpaid:", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        d.unpaidMonths.forEach { m ->
                            Box(modifier = Modifier.background(WarningAmberSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, WarningAmber.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(m, style = MaterialTheme.typography.labelSmall, color = WarningAmber, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Detail Dialogs ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentDetailDialog(s: StudentFeeStatus, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollBadge(s.rollNo)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.studentName, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("ID: ${s.studentId}", color = TextTertiary, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Fee Status", if (s.isDefaulter) "DEFAULTER" else "CLEAR", if (s.isDefaulter) ErrorRed else SuccessGreen)
                if (s.totalPending > 0) DetailLine("Pending", formatRs(s.totalPending), ErrorRed)
                DetailLine("Exam Eligible", if (s.examBlocked) "NO — Blocked" else "Yes", if (s.examBlocked) ErrorRed else SuccessGreen)
                DetailLine("Result Access", if (s.resultWithheld) "WITHHELD" else "Allowed", if (s.resultWithheld) ErrorRed else SuccessGreen)
                if (s.fatherName.isNotBlank()) DetailLine("Father", s.fatherName, TextSecondary)
                if (s.phone.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}")))
                    }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Parent Phone", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Call, null, tint = Teal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s.phone, style = MaterialTheme.typography.bodySmall, color = Teal, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (s.lastPaymentDate.isNotBlank()) DetailLine("Last Payment", s.lastPaymentDate, TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Teal) } },
        containerColor = SurfaceDark, shape = RoundedCornerShape(16.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaulterDetailDialog(d: FeeDefaulter, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).background(ErrorRedSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(d.studentName, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Roll: ${d.rollNo.ifBlank { "-" }}", color = TextTertiary, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Total Dues", formatRs(d.totalDues), ErrorRed)
                DetailLine("Exam Eligible", if (d.examBlocked) "NO — Blocked" else "Yes", if (d.examBlocked) ErrorRed else SuccessGreen)
                DetailLine("Result Access", if (d.resultWithheld) "WITHHELD" else "Allowed", if (d.resultWithheld) ErrorRed else SuccessGreen)
                if (d.fatherName.isNotBlank()) DetailLine("Parent", d.fatherName, TextSecondary)
                if (d.phone.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.phone}")))
                    }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Call Parent", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Call, null, tint = Teal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(d.phone, style = MaterialTheme.typography.bodySmall, color = Teal, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (d.unpaidMonths.isNotEmpty()) {
                    Text("Unpaid Months:", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        d.unpaidMonths.forEach { m ->
                            Box(modifier = Modifier.background(WarningAmberSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, WarningAmber.copy(0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(m, style = MaterialTheme.typography.labelSmall, color = WarningAmber, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Teal) } },
        containerColor = SurfaceDark, shape = RoundedCornerShape(16.dp))
}

// ── Shared ──────────────────────────────────────────────────────────────

@Composable private fun RollBadge(roll: String) {
    Box(modifier = Modifier.size(32.dp).background(Glass, RoundedCornerShape(8.dp))
        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(roll.ifBlank { "-" }, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun Badge(text: String, color: Color, bg: Color) {
    Box(modifier = Modifier.background(bg, RoundedCornerShape(6.dp))
        .border(1.dp, color.copy(0.3f), RoundedCornerShape(6.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium, fontSize = 9.sp)
    }
}

@Composable private fun DetailLine(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

// ── Utility ─────────────────────────────────────────────────────────────

private fun formatRs(amount: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    fmt.maximumFractionDigits = 0
    return fmt.format(amount)
}

private fun filterStudents(list: List<StudentFeeStatus>, q: String, mode: String): List<StudentFeeStatus> {
    var r = list
    if (q.isNotBlank()) { val lq = q.lowercase(); r = r.filter { it.studentName.lowercase().contains(lq) || it.rollNo.lowercase().contains(lq) } }
    return when (mode) { "paid" -> r.filter { !it.isDefaulter && it.totalPending <= 0 }; "defaulters" -> r.filter { it.isDefaulter || it.totalPending > 0 }; else -> r }
}

private fun filterDefaulters(list: List<FeeDefaulter>, q: String): List<FeeDefaulter> {
    if (q.isBlank()) return list; val lq = q.lowercase()
    return list.filter { it.studentName.lowercase().contains(lq) || it.rollNo.lowercase().contains(lq) }
}
