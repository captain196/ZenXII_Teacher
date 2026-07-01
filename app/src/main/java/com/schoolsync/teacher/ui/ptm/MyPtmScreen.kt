package com.schoolsync.teacher.ui.ptm

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.schoolsync.teacher.ui.theme.SuccessGreen
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.firestore.PtmEventDoc
import com.schoolsync.teacher.data.model.firestore.activeSections
import com.schoolsync.teacher.data.model.firestore.assignmentsForTeacher
import com.schoolsync.teacher.data.model.firestore.windowEndTime
import com.schoolsync.teacher.data.model.firestore.windowStartTime
import com.schoolsync.teacher.data.repository.firestore.TeacherStudentView

@Composable
fun MyPtmScreen(
    viewModel: MyPtmViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Groups, null, tint = cs.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Parent-Teacher Meetings",
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Meet parents from your section",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.load() }, enabled = !state.isLoading) {
                Icon(Icons.Filled.Refresh, null, tint = cs.primary)
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = cs.primary)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: "Failed to load.",
                    color = cs.error,
                    modifier = Modifier.padding(24.dp)
                )
            }
            state.ptms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No upcoming PTMs",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "When the school schedules a PTM for a section you teach,\nit will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.ptms.size) { i ->
                    val ptm  = state.ptms[i]
                    val rows = state.rowsByPtm[ptm.ptmEventId.ifBlank { ptm.id }].orEmpty()
                    PtmCard(
                        ptm  = ptm,
                        rows = rows,
                        onMarkDelivered  = { studentId -> viewModel.markDelivered(ptm.ptmEventId.ifBlank { ptm.id }, studentId) },
                        onMarkNoShow     = { studentId -> viewModel.markNoShow(ptm.ptmEventId.ifBlank { ptm.id }, studentId) },
                        onMarkAllDelivered = { viewModel.markAllDelivered(ptm.ptmEventId.ifBlank { ptm.id }) }
                    )
                }
            }
        }

        // Toast slot — surfaces "Marked N delivered." / errors.
        state.toast?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2400)
                viewModel.consumeToast()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(msg, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
            }
        }
    }
}

@Composable
private fun PtmCard(
    ptm: PtmEventDoc,
    rows: List<TeacherStudentView>,
    onMarkDelivered: (String) -> Unit,
    onMarkNoShow: (String) -> Unit,
    onMarkAllDelivered: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(true) }

    val applied   = rows.count { it.status == "applied"   }
    val delivered = rows.count { it.status == "delivered" }
    val noShow    = rows.count { it.status == "no-show"   }
    val staffId = androidx.compose.runtime.remember { "" } // unused — we already pre-filtered

    // Window: prefer Phase-B/C root times; fall back to legacy slot range.
    val window = remember(ptm) {
        val s = ptm.windowStartTime()
        val e = ptm.windowEndTime()
        if (s.isNotBlank() && e.isNotBlank()) "$s – $e" else ""
    }

    // Sections this teacher serves in this PTM (usually one; possibly more
    // if the teacher is class teacher of multiple sections in an all-school
    // PTM). We'll group rows by sectionKey when there's more than one.
    val mySections = remember(ptm, rows) {
        // Use the rows themselves so we always show the sections that
        // actually surfaced — avoids confusion when sections[] list and
        // RSVP routing diverged after a mid-cycle admin edit.
        rows.map { it.sectionKey }.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .padding(14.dp)
    ) {
        // Header row — title, date, expand toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ptm.title.ifBlank { "Parent-Teacher Meeting" },
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ptm.date, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    if (window.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Schedule, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(window, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    if (ptm.location.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.LocationOn, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            ptm.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = cs.onSurfaceVariant
                )
            }
        }

        // Counts
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("Applied",   applied,   cs.primary)
            StatPill("Delivered", delivered, SuccessGreen)
            if (noShow > 0) StatPill("No-show", noShow, cs.error)
        }

        if (mySections.size > 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Hosting ${mySections.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Text(
                    "No applications yet for your section.",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            } else {
                // Bulk action — flips every applied row to delivered.
                if (applied > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.primaryContainer)
                            .clickable(onClick = onMarkAllDelivered)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.DoneAll, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Mark all $applied delivered",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Per-student rows, grouped by sectionKey when this teacher
                // serves multiple sections in this PTM.
                val grouped = rows.groupBy { it.sectionKey }
                val sectionOrder = grouped.keys.sorted()
                sectionOrder.forEachIndexed { idx, secKey ->
                    if (mySections.size > 1) {
                        if (idx > 0) Spacer(Modifier.height(8.dp))
                        Text(
                            secKey,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    grouped[secKey].orEmpty().forEachIndexed { ri, row ->
                        if (ri > 0) Spacer(Modifier.height(6.dp))
                        StudentRow(
                            view = row,
                            onMarkDelivered = onMarkDelivered,
                            onMarkNoShow    = onMarkNoShow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, count: Int, tint: Color) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StudentRow(
    view: TeacherStudentView,
    onMarkDelivered: (String) -> Unit,
    onMarkNoShow: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val rsvp = view.rsvp
    val statusColor = when (view.status) {
        "delivered" -> SuccessGreen
        "applied"   -> cs.primary
        "no-show"   -> cs.error
        else        -> cs.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.3f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Queue badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = view.queueNumber?.let { "#$it" } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rsvp.studentName.ifBlank { "(unnamed)" },
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                val sub = listOfNotNull(
                    rsvp.rollNo.takeIf { it.isNotBlank() }?.let { "Roll #$it" },
                    rsvp.parentName.takeIf { it.isNotBlank() }?.let { "Parent: $it" }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    view.status.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (rsvp.parentPhone.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Phone, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    rsvp.parentPhone,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
        if (rsvp.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "\"${rsvp.note}\"",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action chips — only for applied rows. Delivered/no-show rows are
        // immutable from the per-row UI; bulk action covers applied → delivered.
        if (view.status == "applied") {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip(
                    label = "Mark delivered",
                    icon = Icons.Filled.CheckCircle,
                    tint = SuccessGreen,
                    onClick = { onMarkDelivered(rsvp.studentId) }
                )
                ActionChip(
                    label = "No-show",
                    icon = Icons.Filled.Schedule,
                    tint = cs.error,
                    onClick = { onMarkNoShow(rsvp.studentId) }
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}
