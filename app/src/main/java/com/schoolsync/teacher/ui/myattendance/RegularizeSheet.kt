package com.schoolsync.teacher.ui.myattendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.repository.firestore.RegularizationEntry
import com.schoolsync.teacher.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * RegularizeSheet — the "Attendance Request" bottom sheet (Asanify-style,
 * redesigned to the Soft Blue theme).
 *
 * Pre-fills the selected month's Absent / unmarked workdays as candidate rows;
 * the teacher can toggle each, add any past date (+ New Date), optionally set
 * claimed Clock-In / Clock-Out times, write a required reason, and submit. Each
 * submitted day becomes a pending regularization doc for the school admin to
 * approve. Submission is Firestore-direct (no backend needed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegularizeSheet(
    candidateDates: List<String>,     // yyyy-MM-dd, pre-fill (Absent + unmarked)
    onDismiss: () -> Unit,
    vm: RegularizationViewModel = hiltViewModel(),
) {
    val c = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ui by vm.ui.collectAsStateWithLifecycle()

    // Editable rows — pre-filled from candidates, all enabled by default.
    val rows = remember { mutableStateListOf<RegRow>() }
    LaunchedEffect(candidateDates) {
        if (rows.isEmpty()) rows.addAll(candidateDates.distinct().sorted().map { RegRow(it, enabled = true) })
    }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Close the sheet once a submit succeeds.
    LaunchedEffect(ui.submittedCount) {
        if (ui.submittedCount != null) {
            vm.consume()
            sheetState.hide()
            onDismiss()
        }
    }

    val enabledRows = rows.filter { it.enabled }
    val canSubmit = enabledRows.isNotEmpty() && reason.isNotBlank() && !ui.submitting

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceCard,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            // Header
            Text("Regularize Attendance", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.padding(top = 18.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Timezone", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.width(96.dp))
                Text(timezoneLabel(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            }
            Spacer(Modifier.height(14.dp))
            Text("Want to regularize for a different date?", fontSize = 13.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            OutlineChip(label = "New Date", onClick = { showDatePicker = true })

            Spacer(Modifier.height(16.dp))
            DividerLine()
            Spacer(Modifier.height(12.dp))
            Text("Workday Date", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))

            // Scrollable body (rows + reason)
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (rows.isEmpty()) {
                    Text(
                        "No absent or unmarked days this month. Tap “New Date” to request a correction for any past date.",
                        fontSize = 12.5.sp, color = c.textTertiary, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                rows.forEachIndexed { i, row ->
                    DateRow(row = row)
                    if (i < rows.lastIndex) { Spacer(Modifier.height(6.dp)); DividerLine(); Spacer(Modifier.height(6.dp)) }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reason / note (required)") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Policy notice
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
                    "Each request goes to your school admin for approval. Requests may be auto-rejected after the policy deadline.",
                    fontSize = 11.5.sp, color = c.textSecondary,
                )
            }

            ui.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.5.sp, color = c.error, fontWeight = FontWeight.Medium)
            }

            // Submit
            Spacer(Modifier.height(14.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSubmit) c.accent else c.accent.copy(alpha = 0.45f))
                    .clickable(enabled = canSubmit) {
                        vm.submit(
                            enabledRows.map { RegularizationEntry(it.date, it.checkInIso(), it.checkOutIso()) },
                            reason,
                        )
                    },
            ) {
                if (ui.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = c.textOnAccent)
                } else {
                    Text(
                        "Submit for ${enabledRows.size} day${if (enabledRows.size == 1) "" else "s"}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textOnAccent,
                    )
                }
            }
        }
    }

    // "New Date" picker — past dates only (no future regularization).
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
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            // Compact + scrollable so the calendar never clips (and keeps its
            // OK/Cancel visible) on short screens or in landscape. Drop the
            // oversized title/headline/mode-toggle and cap height with a scroll
            // fallback.
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
                TimeField(label = "Clock In", ms = row.checkInMs, onClick = { pickTarget = 0 }, modifier = Modifier.weight(1f))
                TimeField(label = "Clock Out", ms = row.checkOutMs, onClick = { pickTarget = 1 }, modifier = Modifier.weight(1f))
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
        DatePickerDialogShell(
            onDismiss = { pickTarget = null },
            onConfirm = {
                val ms = combine(row.date, tpState.hour, tpState.minute)
                if (pickTarget == 0) row.checkInMs = ms else row.checkOutMs = ms
                pickTarget = null
            },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (pickTarget == 0) "Clock In time" else "Clock Out time", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.padding(bottom = 12.dp))
                TimePicker(state = tpState)
            }
        }
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
            Text(ms?.let { fmtTime(it) } ?: "Set time", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (ms != null) c.textPrimary else c.textTertiary)
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

/** A tiny AlertDialog-like shell for hosting the time picker (Confirm/Cancel). */
@Composable
private fun DatePickerDialogShell(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { content() },
    )
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
private val PRETTY_FMT = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
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
    runCatching { PRETTY_FMT.format(KEY_FMT.parse(date)!!) }.getOrDefault(date)

private fun fmtTime(ms: Long): String = TIME_FMT.format(Date(ms))

private fun timezoneLabel(): String {
    val tz = TimeZone.getDefault()
    val offsetMin = tz.getOffset(System.currentTimeMillis()) / 60000
    val sign = if (offsetMin >= 0) "+" else "-"
    val h = kotlin.math.abs(offsetMin) / 60
    val m = kotlin.math.abs(offsetMin) % 60
    return String.format(Locale.US, "(GMT%s%02d:%02d) %s", sign, h, m, tz.id)
}
