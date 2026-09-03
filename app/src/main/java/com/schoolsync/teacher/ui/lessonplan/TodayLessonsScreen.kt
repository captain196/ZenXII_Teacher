package com.schoolsync.teacher.ui.lessonplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.repository.firestore.LessonPlanFirestoreRepository.SlotWithPlan
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.CanonicalLabels

/**
 * Phase 7 — Teacher's "Today" lesson planner.
 *
 * Shows the periods this teacher takes today, each with the existing plan
 * (if any) inline. Quick "Mark complete" / "Mark skipped" buttons skip
 * the edit sheet for the common case; an Edit button opens a bottom
 * sheet for picking a topic, writing notes, or rescheduling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayLessonsScreen(
    viewModel: LessonPlanViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Surface ViewModel events via Snackbar
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is LessonPlanEvent.Saved    -> snackbarHostState.showSnackbar(ctx.getString(ev.messageRes))
                is LessonPlanEvent.Error    -> snackbarHostState.showSnackbar(ev.message)
                LessonPlanEvent.Conflict    -> snackbarHostState.showSnackbar(
                    ctx.getString(R.string.lp_conflict)
                )
            }
        }
    }

    GradientBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EventNote, null, tint = Teal, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.lp_title), color = TextPrimary,
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(state.dayLabel, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary)
                    }
                }

                // ── Body ──
                when {
                    state.isLoading -> Centered { CircularProgressIndicator(color = Teal) }
                    state.error != null -> Centered {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    state.rows.isEmpty() -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.EventNote, null, tint = TextTertiary,
                                modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.lp_no_periods), color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.rows, key = { "${it.entry.day}_${it.entry.periodNumber}_${it.entry.className}_${it.entry.section}" }) { row ->
                            SlotCard(
                                slot = row,
                                onComplete = { viewModel.markCompleted(row) },
                                onSkip     = { viewModel.markSkipped(row) },
                                onEdit     = { viewModel.openEdit(row) },
                                saving     = state.isSaving
                            )
                        }
                    }
                }
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) {
                Snackbar(snackbarData = it)
            }
        }
    }

    // ── Edit bottom sheet ──
    state.editing?.let { editing ->
        // Non-dismissible by outside touch / swipe-down / back-press —
        // user must use the Cancel or Save buttons. Prevents accidental
        // edit loss when tapping outside the sheet.
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { newValue -> newValue != SheetValue.Hidden }
        )
        ModalBottomSheet(
            onDismissRequest = { /* intentional no-op — see comment above */ },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EditSheetContent(
                slot = editing,
                topics = state.editingTopics,
                topicsLoading = state.isEditingTopicsLoading,
                isSaving = state.isSaving,
                onSave = { topicId, topicTitle, notes, status, rsTo ->
                    viewModel.saveEdit(topicId, topicTitle, notes, status, rsTo)
                },
                onCancel = { viewModel.closeEdit() }
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
//  Slot card
// ────────────────────────────────────────────────────────────────────────

@Composable
private fun SlotCard(
    slot: SlotWithPlan,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    saving: Boolean
) {
    val plan = slot.plan
    val status = plan?.status ?: "planned"
    val statusColor = when (status) {
        "completed"   -> SuccessGreen
        "skipped"     -> ErrorRed
        "rescheduled" -> WarningAmber
        else          -> Teal
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { onEdit() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: time + period + status pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("P${slot.entry.periodNumber}", color = TextTertiary,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (slot.entry.startTime.isNotBlank() && slot.entry.endTime.isNotBlank())
                        "${slot.entry.startTime} – ${slot.entry.endTime}"
                    else "—",
                    color = TextSecondary, fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                StatusPill(status, statusColor)
            }
            Spacer(Modifier.height(6.dp))
            // Subject + class label
            Text(slot.entry.subject, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${slot.entry.className} · ${slot.entry.section}",
                color = TextSecondary, fontSize = 12.sp)
            // Topic snippet
            val topicLabel = plan?.topicTitle?.takeIf { it.isNotBlank() }
                ?: plan?.notes?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.lp_no_topic_set)
            Spacer(Modifier.height(6.dp))
            Text(topicLabel, color = TextSecondary, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(10.dp))
            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onComplete, enabled = !saving && status != "completed",
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.lp_done), fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onSkip, enabled = !saving && status != "skipped",
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.lp_skip), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onEdit, enabled = !saving,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp), tint = Teal)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_edit), fontSize = 11.sp, color = Teal)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(status, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

// ────────────────────────────────────────────────────────────────────────
//  Edit sheet content
// ────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheetContent(
    slot: SlotWithPlan,
    topics: List<com.schoolsync.teacher.data.model.firestore.CurriculumTopicDoc>,
    topicsLoading: Boolean,
    isSaving: Boolean,
    onSave: (topicId: String, topicTitle: String, notes: String, status: String, rsTo: String) -> Unit,
    onCancel: () -> Unit,
) {
    val plan = slot.plan
    var topicId by rememberSaveable { mutableStateOf(plan?.topicId.orEmpty()) }
    var topicTitle by rememberSaveable { mutableStateOf(plan?.topicTitle.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(plan?.notes.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(plan?.status ?: "planned") }
    var rsDate by rememberSaveable {
        mutableStateOf(plan?.rescheduledTo?.substringBefore("_P").orEmpty())
    }
    var rsP by rememberSaveable {
        mutableStateOf(plan?.rescheduledTo?.substringAfter("_P", "").orEmpty())
    }
    var statusOpen by remember { mutableStateOf(false) }
    var topicOpen by remember { mutableStateOf(false) }

    // Layout: sticky header + scrollable middle + pinned action row.
    // weight(1f, fill = false) makes the middle take available height up to
    // its content; in landscape this scrolls when content overflows, in
    // portrait it just sits at content height.
    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Sticky header ──
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("${slot.entry.subject} · ${slot.entry.className}/${slot.entry.section}",
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.lp_period_time, slot.entry.periodNumber.toString(), slot.entry.timeSlot),
                color = TextSecondary, fontSize = 12.sp)
        }

        // ── Scrollable middle (form fields) ──
        Column(
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ── Topic dropdown ──
            ExposedDropdownMenuBox(expanded = topicOpen, onExpandedChange = { topicOpen = !topicOpen }) {
                OutlinedTextField(
                    value = if (topicTitle.isNotBlank()) topicTitle else if (topicsLoading) "Loading topics…" else stringResource(R.string.lp_no_topic),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.lp_topic)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(
                    expanded = topicOpen, onDismissRequest = { topicOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.lp_no_topic)) },
                        onClick = { topicId = ""; topicTitle = ""; topicOpen = false }
                    )
                    topics.forEach { t ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(t.title, fontSize = 13.sp)
                                    if (t.chapter.isNotBlank())
                                        Text(t.chapter, fontSize = 10.sp, color = TextTertiary)
                                }
                            },
                            onClick = { topicId = t.topicId; topicTitle = t.title; topicOpen = false }
                        )
                    }
                    if (topics.isEmpty() && !topicsLoading) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lp_no_topics_curriculum), color = TextTertiary, fontSize = 11.sp) },
                            onClick = {}, enabled = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Notes (relaxed height — grows with content) ──
            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 2000) notes = it },
                label = { Text(stringResource(R.string.lp_notes)) },
                placeholder = { Text(stringResource(R.string.lp_notes_hint), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp),
                maxLines = 4
            )

            Spacer(Modifier.height(12.dp))

            // ── Status dropdown ──
            ExposedDropdownMenuBox(expanded = statusOpen, onExpandedChange = { statusOpen = !statusOpen }) {
                OutlinedTextField(
                    // `status` is the WIRE value (planned/completed/skipped/
                    // rescheduled) and is what gets written back. Only the
                    // rendering is localized; the variable is never reassigned
                    // from a translated label.
                    value = CanonicalLabels.lessonStatus(LocalContext.current, status),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.common_status)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(
                    expanded = statusOpen, onDismissRequest = { statusOpen = false }
                ) {
                    listOf("planned", "completed", "skipped", "rescheduled").forEach { s ->
                        DropdownMenuItem(
                            text = { Text(CanonicalLabels.lessonStatus(LocalContext.current, s)) },
                            onClick = { status = s; statusOpen = false }
                        )
                    }
                }
            }

            // ── Reschedule fields (when status=rescheduled) ──
            if (status == "rescheduled") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rsDate,
                        onValueChange = { rsDate = it },
                        label = { Text(stringResource(R.string.lp_reschedule_to)) },
                        placeholder = { Text("2026-05-15", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rsP,
                        onValueChange = { rsP = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.lp_period_no)) },
                        modifier = Modifier.width(110.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // ── Pinned action row (always visible, respects keyboard + nav bar) ──
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onCancel, enabled = !isSaving) { Text(stringResource(R.string.common_cancel)) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val rsTo = if (status == "rescheduled") "${rsDate}_P${rsP.ifBlank { "0" }}" else ""  // i18n-ignore: Firestore doc key
                    onSave(topicId, topicTitle, notes, status, rsTo)
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = Color.White)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
