package com.schoolsync.teacher.ui.myattendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.repository.firestore.RegularizationDoc
import com.schoolsync.teacher.data.repository.firestore.RegularizationEntry
import com.schoolsync.teacher.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.DisplayFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource

/**
 * RegularizeScreen — the "Attendance Request" surface, now a FULL SCREEN (not a
 * bottom sheet). Two panels:
 *   • LEFT  — timezone, stringResource(R.string.reg_new_date), and the selectable workday rows (with
 *             optional claimed Clock-In / Clock-Out times).
 *   • RIGHT — the required reason and the Submit action.
 *
 * Why a screen and not a dialog/sheet: on a bottom sheet, the system Back press
 * (which the user naturally taps to dismiss the keyboard while typing the
 * reason) collapsed the whole sheet and threw away the input. A full screen lets
 * Back close the keyboard first; only an intentional Back (or the ← button)
 * returns to the calendar. On narrow phones the two panels stack.
 *
 * Each submitted day becomes a pending regularization doc for the school admin to
 * approve. Submission is Firestore-direct (no backend needed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegularizeScreen(
    candidateDates: List<String>,     // yyyy-MM-dd, pre-fill (Absent + unmarked)
    onDismiss: () -> Unit,
    vm: RegularizationViewModel = hiltViewModel(),
) {
    val c = LocalAppColors.current
    val ui by vm.ui.collectAsStateWithLifecycle()

    // Editable rows — pre-filled from candidates, all enabled by default.
    val rows = remember { mutableStateListOf<RegRow>() }
    LaunchedEffect(candidateDates) {
        if (rows.isEmpty()) rows.addAll(candidateDates.distinct().sorted().map { RegRow(it, enabled = true) })
    }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    // 0 = file a new request, 1 = track my filed requests + admin decisions.
    var tab by remember { mutableStateOf(0) }

    // Load the teacher's own requests when the screen opens so the stringResource(R.string.att_my_requests)
    // tab reflects the latest admin decisions (approved / rejected / cancelled).
    // Also reloaded on a successful submit (VM.submit → loadMyRequests).
    LaunchedEffect(Unit) { vm.loadMyRequests() }

    // On success: show a confirmation overlay briefly, then close back to the
    // calendar (the screen used to just vanish with no feedback).
    LaunchedEffect(ui.submittedCount) {
        if (ui.submittedCount != null) {
            showSuccess = true
            kotlinx.coroutines.delay(1300)
            vm.consume()
            onDismiss()
        }
    }

    // Full screen: Back returns to the calendar (keyboard closes first).
    BackHandler { onDismiss() }

    val enabledRows = rows.filter { it.enabled }
    val canSubmit = enabledRows.isNotEmpty() && reason.isNotBlank() && !ui.submitting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // ── Top bar ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = c.textPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.myatt_regularize), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(stringResource(R.string.myatt_regularize_sub), fontSize = 12.sp, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        SegmentedTabs(
            selected = tab,
            requestCount = ui.myRequests.size,
            onSelect = {
                tab = it
                if (it == 1) vm.loadMyRequests()   // refresh decisions on entering the tab
            },
        )
        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 1) {
                MyRequestsPanel(ui = ui, onRetry = { vm.loadMyRequests() })
            } else BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val twoPane = maxWidth >= 560.dp
                if (twoPane) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PanelCard(modifier = Modifier.weight(1f).fillMaxHeight(), scroll = true) {
                            LeftContent(rows) { showDatePicker = true }
                        }
                        PanelCard(modifier = Modifier.weight(1f).fillMaxHeight(), scroll = true) {
                            RightContent(reason, { reason = it }, enabledRows.size, canSubmit, ui.submitting, ui.error) {
                                vm.submit(enabledRows.map { RegularizationEntry(it.date, it.checkInIso(), it.checkOutIso()) }, reason)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PanelCard(modifier = Modifier.fillMaxWidth(), scroll = false) {
                            LeftContent(rows) { showDatePicker = true }
                        }
                        Spacer(Modifier.height(16.dp))
                        PanelCard(modifier = Modifier.fillMaxWidth(), scroll = false) {
                            RightContent(reason, { reason = it }, enabledRows.size, canSubmit, ui.submitting, ui.error) {
                                vm.submit(enabledRows.map { RegularizationEntry(it.date, it.checkInIso(), it.checkOutIso()) }, reason)
                            }
                        }
                    }
                }
            }
        }
    }

    // Success confirmation overlay — shown briefly before returning to the calendar
    // so the teacher gets clear feedback the request was filed.
    if (showSuccess) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceCard, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 34.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.success, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.reg_submitted), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.reg_sent_to_admin), fontSize = 12.5.sp, color = c.textSecondary)
                }
            }
        }
    }

    // stringResource(R.string.reg_new_date) picker — past dates only (no future regularization).
    if (showDatePicker) {
        val todayEnd = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis }
        val dpState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayEnd
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val d = isoDate(ms)
                        if (rows.none { it.date == d }) rows.add(RegRow(d, enabled = true))
                        rows.sortBy { it.date }
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            val cfg = LocalConfiguration.current
            Column(
                modifier = Modifier
                    .heightIn(max = (cfg.screenHeightDp * 0.68f).dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DatePicker(state = dpState, title = null, headline = null, showModeToggle = false)
            }
        }
    }
}

/* ── panel + left/right content ──────────────────────────────────── */

@Composable
private fun PanelCard(modifier: Modifier, scroll: Boolean, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceCard)
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(16.dp))
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun LeftContent(rows: List<RegRow>, onNewDate: () -> Unit) {
    val c = LocalAppColors.current
    Text(stringResource(R.string.reg_workday_dates), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reg_timezone), fontSize = 12.5.sp, color = c.textSecondary, modifier = Modifier.width(84.dp))
        Text(timezoneLabel(), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
    }
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.reg_add_another), fontSize = 12.5.sp, color = c.textSecondary)
    Spacer(Modifier.height(8.dp))
    OutlineChip(label = stringResource(R.string.reg_new_date), onClick = onNewDate)
    Spacer(Modifier.height(14.dp)); DividerLine(); Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.reg_select_days), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
    Spacer(Modifier.height(8.dp))
    if (rows.isEmpty()) {
        Text(
            stringResource(R.string.reg_no_absent_hint),
            fontSize = 12.5.sp, color = c.textTertiary, modifier = Modifier.padding(vertical = 12.dp),
        )
    }
    rows.forEachIndexed { i, row ->
        DateRow(row = row)
        if (i < rows.lastIndex) { Spacer(Modifier.height(6.dp)); DividerLine(); Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun RightContent(
    reason: String,
    onReasonChange: (String) -> Unit,
    enabledCount: Int,
    canSubmit: Boolean,
    submitting: Boolean,
    error: String?,
    onSubmit: () -> Unit,
) {
    val c = LocalAppColors.current
    Text(stringResource(R.string.common_reason), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = reason,
        onValueChange = onReasonChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        label = { Text(stringResource(R.string.reg_reason_required)) },
        minLines = 5,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(14.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.warningSurface)
            .padding(12.dp),
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = c.warning, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.reg_policy_note),
            fontSize = 11.5.sp, color = c.textSecondary,
        )
    }
    error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, fontSize = 12.5.sp, color = c.error, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(16.dp))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (canSubmit) c.accent else c.accent.copy(alpha = 0.45f))
            .clickable(enabled = canSubmit, onClick = onSubmit),
    ) {
        if (submitting) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = c.textOnAccent)
                Text(stringResource(R.string.common_submitting), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textOnAccent)
            }
        } else {
            Text(
                pluralStringResource(R.plurals.myatt_submit_for_days, enabledCount, enabledCount),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textOnAccent,
            )
        }
    }
}

/* ── tabs + stringResource(R.string.att_my_requests) ────────────────────────────────────────── */

@Composable
private fun SegmentedTabs(selected: Int, requestCount: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceCard)
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegTab(stringResource(R.string.reg_file_request_tab), selected == 0, Modifier.weight(1f)) { onSelect(0) }
        SegTab(
            label = if (requestCount > 0) stringResource(R.string.reg_my_requests_n, requestCount) else stringResource(R.string.att_my_requests),
            active = selected == 1,
            modifier = Modifier.weight(1f),
        ) { onSelect(1) }
    }
}

@Composable
private fun SegTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) c.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) c.textOnAccent else c.textSecondary,
        )
    }
}

/**
 * MyRequestsPanel — the teacher's own filed regularizations with live status.
 * One row PER date (each Firestore doc is a single date), newest first, so a
 * multi-date batch where the admin approved some days and skipped a month-locked
 * one shows each day's real state independently. Handles loading / empty / error.
 */
@Composable
private fun MyRequestsPanel(
    ui: RegularizationUiState,
    onRetry: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reg_your_requests), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(10.dp))
                    .clickable(onClick = onRetry),
            ) {
                if (ui.loadingRequests) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            // First load, nothing yet — a quiet centered spinner.
            ui.loadingRequests && ui.myRequests.isEmpty() && ui.requestsError == null ->
                CenteredBox { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp, color = c.accent) }

            // Load failed and we have nothing to show — explicit error + retry.
            ui.requestsError != null && ui.myRequests.isEmpty() ->
                CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = c.error, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(ui.requestsError, fontSize = 13.sp, color = c.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(c.errorSurface)
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        ) { Text(stringResource(R.string.common_retry), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.error) }
                    }
                }

            ui.myRequests.isEmpty() ->
                CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.reg_no_requests), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.reg_no_requests_hint), fontSize = 12.sp, color = c.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }

            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // A stale error while older data is still shown — a thin banner, not a wipe.
                ui.requestsError?.let {
                    Text(stringResource(R.string.reg_couldnt_refresh, it), fontSize = 11.5.sp, color = c.error)
                }
                ui.myRequests.forEach { RequestCard(it) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RequestCard(doc: RegularizationDoc) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceCard)
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(prettyDate(doc.date), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f))
            StatusChip(doc.status)
        }
        if (doc.reason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(doc.reason, fontSize = 12.5.sp, color = c.textSecondary)
        }
        // Admin decision detail — applied mark + remarks, once decided.
        val appliedLabel = markLabel(LocalContext.current, doc.appliedMark.ifBlank { if (doc.status == "approved") doc.requestedStatus else "" })
        if (doc.status == "approved" && appliedLabel != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.success, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.reg_applied_label, appliedLabel), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.success)
            }
        }
        if (doc.remarks.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.divider.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.reg_admin_prefix), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.textTertiary)
                Text(doc.remarks, fontSize = 11.5.sp, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val c = LocalAppColors.current
    val (label, color) = when (status.lowercase()) {
        "pending" -> "Pending" to c.warning
        "approved" -> "Approved" to c.success
        "rejected" -> "Rejected" to c.error
        "auto_rejected" -> "Auto-rejected" to c.error
        "cancelled", "canceled" -> "Cancelled" to c.textTertiary
        else -> status.replaceFirstChar { it.uppercase() } to c.textTertiary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 11.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Map a 1-letter attendance mark to a readable label (null when blank/unknown). */
private fun markLabel(ctx: android.content.Context, mark: String): String? = when (mark.uppercase()) {
    "P" -> ctx.getString(R.string.attendance_status_present)
    "M" -> ctx.getString(R.string.myatt_status_halfday)
    "T" -> ctx.getString(R.string.attendance_status_late)
    "L" -> ctx.getString(R.string.attendance_status_leave)
    "H" -> ctx.getString(R.string.attendance_status_holiday)
    "O" -> ctx.getString(R.string.myatt_status_weeklyoff)
    "W" -> ctx.getString(R.string.myatt_extra_day)
    "A" -> ctx.getString(R.string.attendance_status_absent)
    else -> null
}

/* ── one workday row ─────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(row: RegRow) {
    val c = LocalAppColors.current
    var pickTarget by remember { mutableStateOf<Int?>(null) } // 0 = in, 1 = out

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { row.expanded = !row.expanded }
                .padding(vertical = 8.dp),
        ) {
            Checkbox(
                checked = row.enabled,
                onCheckedChange = { row.enabled = it },
                colors = CheckboxDefaults.colors(checkedColor = c.accent),
            )
            Text(prettyDate(row.date), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1f))
            Icon(
                if (row.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null, tint = c.textTertiary,
            )
        }
        if (row.expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 6.dp),
            ) {
                TimeField(label = stringResource(R.string.reg_clock_in), ms = row.checkInMs, onClick = { pickTarget = 0 }, modifier = Modifier.weight(1f))
                TimeField(label = stringResource(R.string.reg_clock_out), ms = row.checkOutMs, onClick = { pickTarget = 1 }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (pickTarget != null) {
        val existing = if (pickTarget == 0) row.checkInMs else row.checkOutMs
        val cal = remember { Calendar.getInstance().apply { existing?.let { timeInMillis = it } } }
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false,
        )
        TimePickerModal(
            title = if (pickTarget == 0) stringResource(R.string.reg_clock_in_time) else stringResource(R.string.reg_clock_out_time),
            state = tpState,
            onConfirm = {
                val ms = combine(row.date, tpState.hour, tpState.minute)
                if (pickTarget == 0) row.checkInMs = ms else row.checkOutMs = ms
                pickTarget = null
            },
            onDismiss = { pickTarget = null },
        )
    }
}

@Composable
private fun TimeField(label: String, ms: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 10.5.sp, color = c.textTertiary)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(ms?.let { fmtTime(it) } ?: stringResource(R.string.reg_set_time), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (ms != null) c.textPrimary else c.textTertiary)
        }
    }
}

/* ── small building blocks ───────────────────────────────────────── */

@Composable
private fun OutlineChip(label: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .border(BorderStroke(1.dp, c.accent.copy(alpha = 0.5f)), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
    }
}

@Composable
private fun DividerLine() {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
}

/**
 * Proper time-picker dialog. The old AlertDialog `text` slot clipped the Material
 * clock dial ("half clock"); a plain Dialog + Surface gives the dial full room.
 * A keyboard toggle switches to compact numeric entry (reliable on small screens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(
    title: String,
    state: TimePickerState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    var keyboard by remember { mutableStateOf(false) }
    val cfg = LocalConfiguration.current
    Dialog(
        onDismissRequest = onDismiss,
        // Release the platform default max-width — the horizontal dial is wider than
        // it, which clipped the right half of the clock. We size to content instead
        // (capped below) with a screen-edge margin.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = c.surfaceCard,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                // Landscape-locked (short viewport): cap height + scroll so the dial
                // can never clip vertically; cap width so it stays dialog-sized while
                // giving the HORIZONTAL dial (landscape layout) enough room.
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .heightIn(max = (cfg.screenHeightDp * 0.94f).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.padding(bottom = 12.dp))
                if (keyboard) {
                    TimeInput(state = state)
                } else {
                    TimePicker(state = state, layoutType = TimePickerLayoutType.Horizontal)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { keyboard = !keyboard }) {
                        Icon(
                            if (keyboard) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                            contentDescription = if (keyboard) stringResource(R.string.reg_switch_clock) else stringResource(R.string.reg_switch_keyboard),
                            tint = c.textSecondary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_set)) }
                }
            }
        }
    }
}

/* ── row state ───────────────────────────────────────────────────── */

private class RegRow(val date: String, enabled: Boolean) {
    var enabled by mutableStateOf(enabled)
    var checkInMs by mutableStateOf<Long?>(null)
    var checkOutMs by mutableStateOf<Long?>(null)
    var expanded by mutableStateOf(false)

    fun checkInIso(): String? = checkInMs?.let { ISO_FMT.format(Date(it)) }
    fun checkOutIso(): String? = checkOutMs?.let { ISO_FMT.format(Date(it)) }
}

/* ── date/time helpers (pure) ────────────────────────────────────── */

private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
private val KEY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
// NOT a cached SimpleDateFormat. A top-level val is initialised once per
// process, so it would capture whatever locale the app launched in and keep
// rendering that language after a switch + recreate() until the process dies.
// DisplayFormat builds a fresh formatter per call (and pins Latin digits).
private val TIME_FMT = SimpleDateFormat("hh:mm a", Locale.US)

/** Combine a yyyy-MM-dd date with an hour/minute into epoch millis (device tz). */
private fun combine(date: String, hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance()
    runCatching { cal.time = KEY_FMT.parse(date)!! }
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** DatePicker returns a UTC-midnight millis; render as the intended calendar day. */
private fun isoDate(utcMillis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = utcMillis
    val local = Calendar.getInstance().apply {
        clear()
        set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }
    return KEY_FMT.format(local.time)
}

private fun prettyDate(date: String): String =
    runCatching { DisplayFormat.weekdayDayMonth(KEY_FMT.parse(date)!!) }.getOrDefault(date)

private fun fmtTime(ms: Long): String = TIME_FMT.format(Date(ms))

private fun timezoneLabel(): String {
    val tz = TimeZone.getDefault()
    val offsetMin = tz.getOffset(System.currentTimeMillis()) / 60000
    val sign = if (offsetMin >= 0) "+" else "-"
    val h = kotlin.math.abs(offsetMin) / 60
    val m = kotlin.math.abs(offsetMin) % 60
    return String.format(Locale.US, "(GMT%s%02d:%02d) %s", sign, h, m, tz.id)
}
