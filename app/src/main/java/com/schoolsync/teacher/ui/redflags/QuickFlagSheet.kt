package com.schoolsync.teacher.ui.redflags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.data.model.StudentFlag
import com.schoolsync.teacher.data.model.StudentInfo
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import com.schoolsync.teacher.util.CanonicalLabels

/**
 * Phase 6A — Teacher 1-tap Red Flag quick flow.
 *
 * A reusable bottom sheet opened from the 🚩 trailing icon on student rows
 * across attendance/homework/marks/redflags/students. Reduces the
 * friction of raising a flag from ~6 taps + typing to 2-3 taps while
 * preserving the canonical `studentFlags` doc shape (so admin and
 * parent UIs need no schema changes).
 *
 * Locked design (phase_6a_design.md, 2026-05-05):
 *   • 3 templates: Late HW / Behaviour concern / Low performance
 *   • Severity defaults Medium across all templates
 *   • Subject auto-resolved when screen has context; forced pick on
 *     attendance / redflags / students screens
 *   • "More options" expander for severity / message / type overrides
 *   • Submit emits a populated [StudentFlag] for the host to persist
 */

/** Compact template descriptor — drives the chip row. */
private data class FlagTemplate(
    // @StringRes: TEMPLATES below is a top-level val, so a String label would be
    // resolved once per process and freeze the launch language.
    @StringRes val label: Int,
    val emoji: String,
    val type: String,        // canonical lowercase enum
    val severity: String,    // canonical lowercase enum
    val message: String
)

// NOTE on `message`: it is deliberately NOT translated. It is inserted into the
// flag document and read by the PARENT app, whose reader may have chosen a
// different language than the staff member filing the flag — so translating it
// here would show one family's flag in someone else's language. The teacher can
// always edit the text (More options -> Message) before filing.
private val TEMPLATES = listOf(
    FlagTemplate(
        label = R.string.rf_tpl_late_hw,
        emoji = "📝",
        type = "homework",
        severity = "medium",
        message = "Did not submit homework on time"  // i18n-ignore: read by the Parent app
    ),
    FlagTemplate(
        label = R.string.rf_tpl_behaviour,
        emoji = "😟",
        type = "behavior",
        severity = "medium",
        message = "Behaviour requires attention"  // i18n-ignore: read by the Parent app
    ),
    FlagTemplate(
        label = R.string.rf_tpl_low_perf,
        emoji = "📉",
        type = "performance",
        severity = "medium",
        message = "Recent performance below expectation"  // i18n-ignore: read by the Parent app
    )
)

private val SEVERITIES = listOf("low", "medium", "high")
private val TYPES      = listOf("homework", "behavior", "performance")

/**
 * Host-state for the quick flag sheet. Hold one per screen and call
 * [showFor] from each row's 🚩 onClick.
 *
 * Subject behavior:
 *   • [defaultSubject] non-blank → auto-fills (homework / marks columns)
 *   • [defaultSubject] blank AND [forceSubjectPick] → user must pick
 *     before Submit enables
 *   • [subjectsForClass] is the list shown in the picker (filter to the
 *     teacher's assignments for the row's class+section)
 */
class QuickFlagSheetState {
    var visible: Boolean by mutableStateOf(false)
        private set
    var student: StudentInfo? by mutableStateOf(null)
        private set
    var classKey: String by mutableStateOf("")
        private set
    var sectionKey: String by mutableStateOf("")
        private set
    var defaultSubject: String by mutableStateOf("")
        private set
    var forceSubjectPick: Boolean by mutableStateOf(false)
        private set
    var subjectsForClass: List<String> by mutableStateOf(emptyList())
        private set

    fun showFor(
        student: StudentInfo,
        classKey: String,
        sectionKey: String,
        defaultSubject: String = "",
        forceSubjectPick: Boolean = false,
        subjectsForClass: List<String> = emptyList()
    ) {
        this.student          = student
        this.classKey         = normalizeClassKey(classKey)
        this.sectionKey       = normalizeSectionKey(sectionKey)
        this.defaultSubject   = defaultSubject.trim()
        this.forceSubjectPick = forceSubjectPick
        this.subjectsForClass = subjectsForClass.distinct()
        this.visible          = true
    }

    fun dismiss() {
        visible = false
    }

    private fun normalizeClassKey(s: String): String {
        val v = s.trim()
        if (v.isEmpty()) return ""
        return if (v.startsWith("Class ", ignoreCase = true)) v else "Class $v"
    }
    private fun normalizeSectionKey(s: String): String {
        val v = s.trim()
        if (v.isEmpty()) return ""
        return if (v.startsWith("Section ", ignoreCase = true)) v else "Section $v"
    }
}

@Composable
fun rememberQuickFlagSheetState(): QuickFlagSheetState = remember { QuickFlagSheetState() }

/**
 * The sheet itself. Render once per host screen, controlled by [state].
 * [onSubmit] is invoked synchronously with a fully-populated
 * [StudentFlag]; the host is responsible for the actual repository
 * call + post-submit UX (snackbar with Undo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFlagSheet(
    state: QuickFlagSheetState,
    isSaving: Boolean = false,
    onSubmit: (StudentFlag) -> Unit
) {
    if (!state.visible) return
    val student = state.student ?: return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    // Local form state — reset every time the sheet is opened by
    // keying remember on the student id. Avoids stale form leaking
    // across consecutive opens for different students.
    var selectedTemplate by remember(student.studentId) { mutableStateOf<FlagTemplate?>(null) }
    var subject          by remember(student.studentId) { mutableStateOf(state.defaultSubject) }
    var severity         by remember(student.studentId) { mutableStateOf("medium") }
    var type             by remember(student.studentId) { mutableStateOf("") }
    var message          by remember(student.studentId) { mutableStateOf("") }
    var moreOptionsOpen  by remember(student.studentId) { mutableStateOf(false) }
    // Submit-in-flight state is HOISTED to the VM (isSaving) so the spinner
    // actually renders — the old local `submitting` var never showed because
    // the button dismissed the sheet on the same frame it was set.
    val submitting = isSaving

    // Picking a template seeds type / severity / message. User can
    // still flip those via the "More options" expander.
    LaunchedEffect(selectedTemplate) {
        selectedTemplate?.let { t ->
            type     = t.type
            severity = t.severity
            message  = t.message
        }
    }

    // Subject integrity: when subjects ARE available for this class+section the
    // teacher must pick one — no free-text fallback, so reporting stays clean.
    val noAssignedSubjects = state.forceSubjectPick && state.subjectsForClass.isEmpty()
    // …but when the teacher has NO subjectAssignments at all for this class, a
    // hard refusal locked them out of raising ANY flag (F-NEW-1). Since the
    // full CreateFlagDialog is unreachable (see RedFlagTeacherScreen), this
    // sheet is the only create path in the app — so the lockout meant a real
    // safeguarding concern could not be recorded at all until an admin fixed
    // the assignment data. Degrade instead: allow the flag through with a blank
    // subject (the panel buckets blank as "General") and warn inline.
    val needsSubject = state.forceSubjectPick && subject.isBlank() && !noAssignedSubjects
    val canSubmit = !submitting
        && type.isNotBlank()
        && severity.isNotBlank()
        && message.isNotBlank()
        && !needsSubject

    ModalBottomSheet(
        onDismissRequest = { state.dismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scroll the sheet body so "More options" (severity/type/message)
                // and the action buttons stay reachable when the expanded form
                // is taller than the sheet — especially in landscape or with the
                // keyboard open. imePadding keeps the message field + Submit
                // above the keyboard; navigationBarsPadding clears the gesture bar.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // ── Header: student + class ──────────────────────────
            Text(
                text = stringResource(R.string.rf_flag_student_cd, student.displayName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${state.classKey} · ${state.sectionKey}" +
                    (if (student.rollNo.isNotBlank()) " · Roll ${student.rollNo}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ── Template chips ──────────────────────────────────
            Text(
                text = stringResource(R.string.rf_quick_reason),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TEMPLATES, key = { it.label }) { template ->
                    TemplateChip(
                        template = template,
                        selected = selectedTemplate?.label == template.label,
                        onClick = { selectedTemplate = template }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Subject row (auto or required pick) ─────────────
            SubjectField(
                subject = subject,
                onSubjectChange = { subject = it },
                subjectsForClass = state.subjectsForClass,
                forced = state.forceSubjectPick,
                hasDefault = state.defaultSubject.isNotBlank()
            )
            Spacer(Modifier.height(12.dp))

            // ── More options (severity / type / message override) ──
            TextButton(
                onClick = { moreOptionsOpen = !moreOptionsOpen },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(if (moreOptionsOpen) stringResource(R.string.rf_hide_options) else stringResource(R.string.rf_more_options))
            }
            AnimatedVisibility(
                visible = moreOptionsOpen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    val ctx = LocalContext.current
                    EnumDropdown(
                        label = stringResource(R.string.common_severity),
                        options = SEVERITIES,
                        value = severity,
                        display = { CanonicalLabels.flagSeverity(ctx, it) },
                        onChange = { severity = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    EnumDropdown(
                        label = stringResource(R.string.common_type),
                        options = TYPES,
                        value = type,
                        display = { CanonicalLabels.flagType(ctx, it) },
                        onChange = { type = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(stringResource(R.string.common_message)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Action row ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = { state.dismiss() }) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (!canSubmit) return@Button
                        val flag = StudentFlag(
                            studentId   = student.studentId,
                            studentName = student.displayName,
                            rollNo      = student.rollNo,
                            fatherName  = student.fatherName,
                            className   = state.classKey,
                            section     = state.sectionKey,
                            type        = type.lowercase(),
                            severity    = severity.lowercase(),
                            message     = message.trim(),
                            subject     = subject.trim(),
                            // Repository fills in teacherId/teacherName,
                            // schoolId/schoolCode/session, createdAtMs,
                            // status, createdByRole. Sending only the
                            // denorm fields keeps the contract simple.
                            createdAtMs = System.currentTimeMillis(),
                            status      = "active"
                        )
                        // Do NOT dismiss here — the VM drives `isSaving` and the
                        // screen dismisses the sheet on success. This keeps the
                        // sheet open with a spinner during the write, and leaves
                        // it open on failure so the teacher can retry.
                        onSubmit(flag)
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (submitting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (submitting) stringResource(R.string.common_submitting_ellipsis) else stringResource(R.string.rf_submit_flag))
                }
            }

            if (noAssignedSubjects) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.rf_no_subject_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (needsSubject) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.rf_subject_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TemplateChip(
    template: FlagTemplate,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.outlineVariant
    val bgColor     = if (selected) MaterialTheme.colorScheme.errorContainer
                      else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(template.emoji, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(template.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectField(
    subject: String,
    onSubjectChange: (String) -> Unit,
    subjectsForClass: List<String>,
    forced: Boolean,
    hasDefault: Boolean
) {
    val helper = when {
        hasDefault && !forced       -> stringResource(R.string.rf_auto_resolved)
        subjectsForClass.size == 1  -> stringResource(R.string.rf_auto_filled)
        forced                      -> stringResource(R.string.rf_subject_required_pick)
        else                        -> stringResource(R.string.common_optional)
    }

    if (subjectsForClass.isEmpty()) {
        // Strict integrity: if there are no subjectAssignments for this
        // teacher+class+section, do NOT render an input. Submission is
        // gated upstream and the bottom-of-sheet error explains why.
        // Free-text was deliberately removed — admins must wire up an
        // assignment so the canonical subject column never gets dirty.
        if (!forced) {
            // Non-forced screens (homework / marks) always pass a default
            // subject from screen context; this branch only fires on the
            // unusual case of a non-forced caller with no list AND no
            // default. Render a read-only display of the current subject
            // so the user sees what's about to be submitted.
            OutlinedTextField(
                value = subject,
                onValueChange = { /* read-only */ },
                readOnly = true,
                label = { Text(stringResource(R.string.common_subject)) },
                supportingText = { Text(helper) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = subject,
            onValueChange = { /* read-only via dropdown */ },
            readOnly = true,
            label = { Text(stringResource(R.string.common_subject) + if (forced) " *" else "") },
            supportingText = { Text(helper) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            subjectsForClass.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = {
                        onSubjectChange(s)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 5-second Undo banner. Visible while a just-raised quick flag is
 * still in the soft-undo window. Designed as a Material3 inline
 * banner rather than a Snackbar so it cleanly co-exists with the
 * screen's existing event-snackbar host.
 */
@Composable
fun QuickFlagUndoBanner(
    visible: Boolean,
    onUndo: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    MaterialTheme.colorScheme.inverseSurface,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.flag_raised_toast),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onUndo,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.inversePrimary
                )
            ) {
                Text(stringResource(R.string.common_undo), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    options: List<String>,
    value: String,
    // Renders a canonical wire value for display. `value`/`options` stay
    // canonical — only what the user reads passes through this.
    display: (String) -> String,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = display(value),
            onValueChange = { /* read-only */ },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
