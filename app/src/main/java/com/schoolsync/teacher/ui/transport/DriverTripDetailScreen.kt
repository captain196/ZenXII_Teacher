package com.schoolsync.teacher.ui.transport

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.location.TripTrackingController
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.schoolsync.teacher.data.model.RosterStudent
import com.schoolsync.teacher.data.model.TripRoster
import com.schoolsync.teacher.data.model.firestore.TripLogDoc
import com.schoolsync.teacher.data.model.firestore.TripProgress
import com.schoolsync.teacher.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DriverTripDetailScreen — F9 (LC-22) full driver operational workflow
 * for a single trip. Reuses ALL existing repository methods:
 *   • startMyTrip                — Ready → InProgress
 *   • markCheckpointReached      — checkpoint tap
 *   • markCheckpointSkipped      — with reason
 *   • markStudentBoarded         — Board button on Pickup trip
 *   • markStudentDropped         — Drop button on Drop trip
 *   • markStudentNoShow          — No-show button
 *   • completeMyTrip             — end odometer + distance + fuel + notes
 *
 * No new business logic; the field-level Firestore rule
 * (F9-1 gate — driver-only fields on tripLogs update) enforces the
 * write discipline. Boarding events are deterministic-doc-id
 * idempotent (F7-4 gate).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripDetailScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: DriverTripDetailViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(tripId) { viewModel.load(tripId) }

    // ── F14 scanner ──────────────────────────────────────────────────────
    // GMS Code Scanner: Play services owns the camera, the preview and the
    // permission, so this screen declares no CAMERA permission and manages no
    // camera lifecycle. QR only — a conductor pointing the phone at a barcode
    // on a lunchbox should get nothing rather than a confusing error.
    val context = LocalContext.current
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
        )
    }
    val onScanRequested: ((String) -> Unit) -> Unit = { onResult ->
        scanner.startScan()
            .addOnSuccessListener { bc -> bc.rawValue?.let(onResult) }
            // A cancel and a real failure both land here; the message is only
            // surfaced when there is one, so backing out of the scanner is silent.
            .addOnFailureListener { e -> viewModel.scanFailed(e.message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip ${tripId.take(12)}") },
                // Back affordance removed — the device provides system back/gesture.
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.trip == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Trip not found.", color = Color.Red)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { StatusCard(state.trip, isBusy = state.mutating) }
                    if (state.trip.status == "Ready") {
                        item {
                            Button(
                                onClick = { viewModel.startTrip() },
                                enabled = !state.mutating,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Start Trip") }
                        }
                    }
                    if (state.trip.status == "InProgress") {
                        // F13.2 — route progress from the driver's own confirmed
                        // arrivals. Same rules as the admin fleet map and the
                        // parent app, so all three agree. No map, no ETA.
                        item {
                            TripProgress.from(state.trip.checkpoints).summaryOrNull()?.let { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        item {
                            Text(
                                "Checkpoints",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(state.trip.checkpoints, key = { (it["stop_id"] as? String) ?: "" }) { cp ->
                            CheckpointRow(
                                cp = cp,
                                onReached = { stopId -> viewModel.markReached(stopId) },
                                onSkip = { stopId -> viewModel.openSkipDialog(stopId) }
                            )
                        }
                        item {
                            HeadCountPanel(
                                roster = state.roster,
                                fallbackExpected = state.trip.rosterStudentIds.size,
                            )
                        }
                        state.scanBanner?.let { b ->
                            item { ScanBannerRow(b, onDismiss = { viewModel.dismissBanner() }) }
                        }
                        item {
                            ScanActions(
                                enabled = !state.mutating && state.trip.status == "InProgress",
                                aboard = state.roster?.counts?.onBoard ?: 0,
                                onScan = { onScanRequested { qr -> viewModel.onQrScanned(qr) } },
                                onAllOff = { viewModel.markAllAlightedAtSchool() },
                            )
                        }
                        val rows = state.roster?.students ?: emptyList()
                        if (rows.isEmpty()) {
                            item {
                                Text(
                                    "No students on this trip's roster.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        items(rows, key = { it.studentId }) { s ->
                            RosterRow(
                                student = s,
                                enabled = !state.mutating,
                                onResolve = { viewModel.openException(s) }
                            )
                        }
                        state.roster?.blocker?.takeIf { it.blocked }?.let { blk ->
                            item { BlockerRow(blk.message.ifBlank { blk.reason }) }
                        }
                        item {
                            Button(
                                onClick = { viewModel.openCompleteDialog() },
                                // The server refuses a blocked completion regardless;
                                // disabling here just stops the conductor pressing a
                                // button that cannot work, and the reason is shown above.
                                enabled = !state.mutating && state.roster?.blocker?.blocked != true,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Complete Trip") }
                        }
                    }
                    state.error?.let { item { Text(it, color = Color.Red) } }
                }
            }
        }
    }

    state.exceptionStudent?.let { s ->
        ExceptionDialog(
            student = s,
            onConfirm = { resolution, reason -> viewModel.resolveException(resolution, reason) },
            onDismiss = { viewModel.dismissException() }
        )
    }

    if (state.confirmAlternateQr != null) {
        ConfirmAlternateDialog(
            message = state.confirmAlternateMessage,
            onConfirm = { viewModel.confirmAlternate() },
            onDismiss = { viewModel.dismissAlternate() }
        )
    }

    if (state.skipDialogStopId != null) {
        SkipDialog(
            onConfirm = { reason -> viewModel.confirmSkip(reason) },
            onDismiss = { viewModel.dismissSkip() }
        )
    }
    if (state.completeDialogOpen) {
        CompleteDialog(
            onConfirm = { end, dist, fuel, notes -> viewModel.confirmComplete(end, dist, fuel, notes) },
            onDismiss = { viewModel.dismissComplete() }
        )
    }
}

@Composable
private fun StatusCard(trip: TripLogDoc, isBusy: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Trip status: ${trip.status.ifBlank { "—" }}")
            Text("Direction: ${trip.tripDirection.ifBlank { trip.tripType }}")
            Text("Route: ${trip.routeId.ifBlank { "—" }}")
            if (trip.tripKind == "Event" && trip.eventId.isNotBlank()) {
                Text("Event: ${trip.eventId}")
            }
            if (isBusy) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun CheckpointRow(
    cp: Map<String, Any?>,
    onReached: (String) -> Unit,
    onSkip: (String) -> Unit
) {
    val stopId = (cp["stop_id"] as? String).orEmpty()
    val name = (cp["stop_name"] as? String).orEmpty()
    val state = (cp["checkpoint_state"] as? String) ?: "Pending"
    val (label, tint) = when (state) {
        "Reached" -> "Reached" to Color(0xFF16A34A)
        "Skipped" -> "Skipped" to Color(0xFFF59E0B)
        else      -> "Pending" to Color(0xFF3B82F6)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name.ifBlank { stopId }, fontWeight = FontWeight.SemiBold)
                Surface(shape = RoundedCornerShape(50), color = tint) {
                    Text(
                        label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (state == "Pending") {
                OutlinedButton(onClick = { onSkip(stopId) }) { Text("Skip") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = { onReached(stopId) }) { Text("Reached") }
            }
        }
    }
}

/**
 * One student on the bus list.
 *
 * There is no Board button. Boarding is a scan — the signed QR is the identity
 * proof, and a tap could not verify a transport entitlement. What a conductor
 * can do here is ACCOUNT for a child who has not been counted.
 */
@Composable
private fun RosterRow(
    student: RosterStudent,
    enabled: Boolean,
    onResolve: () -> Unit,
) {
    val counted = student.boarded || student.alighted
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(student.name.ifBlank { student.studentId })
                val sub = buildList {
                    if (student.className.isNotBlank()) add(student.className)
                    if (!student.onRoster) add("not on this bus's list")
                }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            StateChip(student)
            if (!counted && student.resolution.let { it.isBlank() || it == "none" || it == "unresolved" }) {
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = onResolve, enabled = enabled) { Text("Account") }
            }
        }
    }
}

/** The child's status as one short, unambiguous word. */
@Composable
private fun StateChip(student: RosterStudent) {
    val (label, color) = when {
        student.alighted -> "Off" to Color(0xFF0369A1)
        student.boarded  -> "On board" to Color(0xFF166534)
        student.resolution == "absent"         -> "Absent" to Color(0xFF6B7280)
        student.resolution == "alternate_bus"  -> "Other bus" to Color(0xFF92400E)
        student.resolution == "verified_other" -> "Verified" to Color(0xFF6B7280)
        else -> "Waiting" to Color(0xFFB45309)
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SkipDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip checkpoint") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text("Skip") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CompleteDialog(
    onConfirm: (endOdometer: Double, distance: Double, fuelUsed: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var end by remember { mutableStateOf("") }
    var dist by remember { mutableStateOf("") }
    var fuel by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(end, { end = it }, label = { Text("End odometer (km)") })
                OutlinedTextField(dist, { dist = it }, label = { Text("Distance (km)") })
                OutlinedTextField(fuel, { fuel = it }, label = { Text("Fuel used (L)") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    end.toDoubleOrNull() ?: 0.0,
                    dist.toDoubleOrNull() ?: 0.0,
                    fuel.toDoubleOrNull() ?: 0.0,
                    notes
                )
            }) { Text("Complete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ═════════════════════════════════════════════════════════════════════
//  ViewModel + state
// ═════════════════════════════════════════════════════════════════════

data class DriverTripDetailState(
    val loading: Boolean = true,
    val mutating: Boolean = false,
    val trip: TripLogDoc? = null,
    val skipDialogStopId: String? = null,
    val completeDialogOpen: Boolean = false,
    val error: String? = null,
    // ── F14 QR attendance ────────────────────────────────────────────
    // The head count, straight from the server. Never recomputed here: a
    // number the phone works out for itself is a number that can disagree
    // with the office about whether a child is on the bus.
    val roster: TripRoster? = null,
    // Last scan outcome, shown as a banner so the conductor is told what
    // happened rather than left guessing from a list that did not change.
    val scanBanner: ScanBanner? = null,
    // A scan the server will only accept once the conductor confirms the
    // child really is taking a different bus today.
    val confirmAlternateQr: String? = null,
    val confirmAlternateMessage: String = "",
    // Student whose non-boarding outcome is being recorded.
    val exceptionStudent: RosterStudent? = null,
)

/** A scan result the conductor must actually see. */
data class ScanBanner(val text: String, val kind: Kind) {
    enum class Kind { OK, DUPLICATE, WARN, ERROR }
}


// ─── F14 conductor UI ────────────────────────────────────────────────────

/**
 * The head count — the number a conductor is actually accountable for.
 *
 * ABOARD is deliberately the largest figure on the screen: at pickup it must
 * reach the expected count, and at school it must fall to zero. Every value
 * comes from the server; nothing here is derived on the device.
 */
@Composable
private fun HeadCountPanel(roster: TripRoster?, fallbackExpected: Int) {
    val c = roster?.counts
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Head count", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${c?.onBoard ?: 0}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("on board", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                CountCell("Expected", c?.expected ?: fallbackExpected)
                CountCell("Counted", c?.boarded ?: 0)
                CountCell("Off", c?.alighted ?: 0)
            }
            if (c != null && (c.unresolved > 0 || c.offRosterAboard > 0 ||
                    c.absent > 0 || c.alternate > 0 || c.resolvedOther > 0)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildList {
                        if (c.unresolved > 0) add("${c.unresolved} not accounted for")
                        if (c.offRosterAboard > 0) add("${c.offRosterAboard} not on this bus's list")
                        if (c.absent > 0) add("${c.absent} absent")
                        if (c.alternate > 0) add("${c.alternate} other bus")
                        if (c.resolvedOther > 0) add("${c.resolvedOther} verified")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (c.unresolved > 0 || c.offRosterAboard > 0) Color(0xFFB45309) else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun CountCell(label: String, value: Int) {
    Column(
        Modifier.padding(start = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

/** The scan result, stated in words. A silent list refresh is not feedback. */
@Composable
private fun ScanBannerRow(banner: ScanBanner, onDismiss: () -> Unit) {
    val bg = when (banner.kind) {
        ScanBanner.Kind.OK        -> Color(0xFFDCFCE7)
        ScanBanner.Kind.DUPLICATE -> Color(0xFFE0F2FE)
        ScanBanner.Kind.WARN      -> Color(0xFFFEF3C7)
        ScanBanner.Kind.ERROR     -> Color(0xFFFEE2E2)
    }
    val fg = when (banner.kind) {
        ScanBanner.Kind.OK        -> Color(0xFF166534)
        ScanBanner.Kind.DUPLICATE -> Color(0xFF075985)
        ScanBanner.Kind.WARN      -> Color(0xFF92400E)
        ScanBanner.Kind.ERROR     -> Color(0xFF991B1B)
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(banner.text, color = fg, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss", color = fg) }
        }
    }
}

/**
 * The two actions a conductor performs.
 *
 * Both are disabled unless the trip is InProgress — the server refuses either
 * way, and a live button that always fails teaches a driver to ignore errors.
 */
@Composable
private fun ScanActions(
    enabled: Boolean,
    aboard: Int,
    onScan: () -> Unit,
    onAllOff: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = onScan,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Scan student ID card") }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onAllOff,
            enabled = enabled && aboard > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (aboard > 0) "All $aboard off at school" else "All off at school") }
    }
}

/** Why the trip cannot be closed yet — in the conductor's words, not a code. */
@Composable
private fun BlockerRow(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
    ) {
        Text(
            message.ifBlank { "The trip cannot be completed yet." },
            color = Color(0xFF92400E),
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * Record why a rostered child did not board.
 *
 * The three options are the service's own resolutions — nothing here invents a
 * boarding, which is the whole reason hand-boarding was removed.
 */
@Composable
private fun ExceptionDialog(
    student: RosterStudent,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(student.name.ifBlank { student.studentId }) },
        text = {
            Column {
                Text("This student has not been counted. What happened?")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(reason, { reason = it }, label = { Text("Note (optional)") })
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onConfirm("absent", reason) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Absent today") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onConfirm("alternate_bus", reason) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Took another bus") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onConfirm("verified_other", reason) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Verified another way") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The wrong-bus prompt. The server refuses until the conductor confirms. */
@Composable
private fun ConfirmAlternateDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not on this bus's list") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Count anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
@HiltViewModel
class DriverTripDetailViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository,
    // F13.1 M3 — owns the live-GPS stream and the location precondition.
    private val tripTracking: TripTrackingController,
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(DriverTripDetailState())
    val state: State<DriverTripDetailState> = _state

    private var tripId: String = ""

    fun load(tid: String) {
        tripId = tid
        viewModelScope.launch { refresh() }
        loadRoster()
    }

    private suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        val trip = transportRepo.getMyActiveTrips().getOrDefault(emptyList()).firstOrNull { it.id == tripId }
            ?: transportRepo.getMyActiveTrips().getOrDefault(emptyList()).firstOrNull()
        _state.value = _state.value.copy(loading = false, trip = trip, error = null)
    }

    /**
     * Start Trip — location precondition first, then the canonical server
     * lifecycle, then the stream.
     *
     * If Location Services is off the trip does NOT start and nothing is sent
     * to the server; the driver sees the copy below. Fixing the setting and
     * pressing Start Trip again re-runs this check, so tracking begins on the
     * second press with no extra step — the check is per-press, not cached.
     *
     * Ordering matters: the hot document is created server-side when the trip
     * goes InProgress, so the stream can only start AFTER the lifecycle call
     * succeeds. Starting it earlier would have it writing to a document that
     * does not exist yet.
     */
    fun startTrip() {
        when (tripTracking.preflight()) {
            TripTrackingController.Blocker.LOCATION_SERVICES_OFF -> {
                _state.value = _state.value.copy(
                    error = "Please enable Location Services to start the trip."
                )
                return
            }
            TripTrackingController.Blocker.PERMISSION_MISSING -> {
                _state.value = _state.value.copy(
                    error = "Location permission is required to start the trip. " +
                        "Allow location access for SchoolSync and try again."
                )
                return
            }
            null -> Unit
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(mutating = true, error = null)
            val r = transportRepo.startMyTrip(tripId)
            if (r.isFailure) {
                // Carries the server's own reason — an unfit driver, a
                // suspended route, a trip assigned to someone else. Shown
                // verbatim; that message is the whole point of routing the
                // lifecycle through the canonical service.
                _state.value = _state.value.copy(
                    mutating = false,
                    error = r.exceptionOrNull()?.message ?: "Could not start the trip."
                )
                return@launch
            }
            _state.value = _state.value.copy(mutating = false)
            refresh()
            _state.value.trip
                ?.takeIf { it.status == "InProgress" }
                ?.let { tripTracking.start(it) }
        }
    }
    fun markReached(stopId: String) = mutate { transportRepo.markCheckpointReached(tripId, stopId) }
    // ─── F14 QR attendance — the conductor's head count ─────────────────
    //
    // Boarding is a SCAN, not a tap. The signed QR on the student's ID card is
    // the identity proof; the server verifies the signature and the transport
    // entitlement before recording anything, which a tap-to-board button could
    // never do. A child without a scannable card is accounted for through
    // [openException] — recording the real outcome instead of a fake boarding.
    //
    // NOTE ON SCOPE: this is BUS boarding only. It writes transportAttendance
    // and has no effect whatsoever on classroom attendance — a child who
    // boarded the bus is not thereby marked present in class, and the two are
    // deliberately separate collections with separate authorities.

    /** Refresh the roster + counts + completion blocker from the server. */
    fun loadRoster() {
        viewModelScope.launch {
            val r = transportRepo.tripRoster(tripId)
            _state.value = _state.value.copy(
                roster = r.getOrNull() ?: _state.value.roster,
                error = r.exceptionOrNull()?.message ?: _state.value.error,
            )
        }
    }

    /**
     * A card was scanned. The raw token goes to the server untouched.
     *
     * Three outcomes are distinguished because they mean different things to a
     * conductor standing at the door: recorded, already recorded (a re-scan, not
     * an error), and "this child is not on this bus — confirm".
     */
    fun onQrScanned(rawQr: String, confirmAlternate: Boolean = false) {
        if (rawQr.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(mutating = true, scanBanner = null)
            val r = transportRepo.scanStudentQr(tripId, rawQr, confirmAlternate)
            val out = r.getOrNull()
            if (r.isFailure || out == null) {
                _state.value = _state.value.copy(
                    mutating = false,
                    scanBanner = ScanBanner(
                        r.exceptionOrNull()?.message ?: "Could not record the scan.",
                        ScanBanner.Kind.ERROR
                    ),
                )
                return@launch
            }
            when {
                out.needsConfirmation -> {
                    _state.value = _state.value.copy(
                        mutating = false,
                        confirmAlternateQr = rawQr,
                        confirmAlternateMessage = out.message.ifBlank {
                            "This student is not on this trip's roster. Board anyway?"
                        },
                    )
                    return@launch
                }
                out.isDuplicate -> _state.value = _state.value.copy(
                    mutating = false,
                    scanBanner = ScanBanner(
                        "${out.studentName.ifBlank { out.studentId }} — already counted",
                        ScanBanner.Kind.DUPLICATE
                    ),
                )
                else -> _state.value = _state.value.copy(
                    mutating = false,
                    scanBanner = ScanBanner(
                        "${out.studentName.ifBlank { out.studentId }} counted",
                        ScanBanner.Kind.OK
                    ),
                )
            }
            loadRoster()
        }
    }

    fun confirmAlternate() {
        val qr = _state.value.confirmAlternateQr ?: return
        _state.value = _state.value.copy(confirmAlternateQr = null)
        onQrScanned(qr, confirmAlternate = true)
    }
    fun dismissAlternate() {
        _state.value = _state.value.copy(confirmAlternateQr = null, confirmAlternateMessage = "")
    }

    /** Everyone still aboard has reached school. Geofence-verified server-side. */
    fun markAllAlightedAtSchool() {
        viewModelScope.launch {
            _state.value = _state.value.copy(mutating = true, scanBanner = null)
            val r = transportRepo.markAllAlightedAtSchool(tripId)
            _state.value = _state.value.copy(
                mutating = false,
                scanBanner = if (r.isSuccess)
                    ScanBanner("All students marked off at school", ScanBanner.Kind.OK)
                else ScanBanner(
                    r.exceptionOrNull()?.message ?: "Could not record arrival at school.",
                    ScanBanner.Kind.ERROR
                ),
            )
            loadRoster()
        }
    }

    fun openException(s: RosterStudent) { _state.value = _state.value.copy(exceptionStudent = s) }
    fun dismissException() { _state.value = _state.value.copy(exceptionStudent = null) }

    /**
     * Record why a rostered child did not board.
     *
     * `resolution` is the service's own vocabulary (absent / alternate_bus /
     * verified_other) — these are what clear the completion blocker. The
     * conductor is accounting for a child, never inventing a boarding.
     */
    fun resolveException(resolution: String, reason: String) {
        val s = _state.value.exceptionStudent ?: return
        _state.value = _state.value.copy(exceptionStudent = null)
        viewModelScope.launch {
            _state.value = _state.value.copy(mutating = true, scanBanner = null)
            val r = transportRepo.resolveStudentException(tripId, s.studentId, resolution, reason)
            _state.value = _state.value.copy(
                mutating = false,
                scanBanner = if (r.isSuccess)
                    ScanBanner("${s.name.ifBlank { s.studentId }} — recorded", ScanBanner.Kind.OK)
                else ScanBanner(
                    r.exceptionOrNull()?.message ?: "Could not record the resolution.",
                    ScanBanner.Kind.ERROR
                ),
            )
            loadRoster()
        }
    }

    /**
     * The scanner itself failed or was cancelled.
     *
     * A cancel arrives here too and carries no message, so backing out of the
     * scanner is silent rather than showing a spurious error.
     */
    fun scanFailed(message: String?) {
        if (message.isNullOrBlank()) return
        _state.value = _state.value.copy(
            scanBanner = ScanBanner("Scanner: $message", ScanBanner.Kind.ERROR)
        )
    }

    fun dismissBanner() { _state.value = _state.value.copy(scanBanner = null) }

    fun openSkipDialog(stopId: String) { _state.value = _state.value.copy(skipDialogStopId = stopId) }
    fun dismissSkip() { _state.value = _state.value.copy(skipDialogStopId = null) }
    fun confirmSkip(reason: String) {
        val stopId = _state.value.skipDialogStopId ?: return
        _state.value = _state.value.copy(skipDialogStopId = null)
        mutate { transportRepo.markCheckpointSkipped(tripId, stopId, reason) }
    }

    fun openCompleteDialog() { _state.value = _state.value.copy(completeDialogOpen = true) }
    fun dismissComplete() { _state.value = _state.value.copy(completeDialogOpen = false) }
    fun confirmComplete(end: Double, dist: Double, fuel: Double, notes: String) {
        _state.value = _state.value.copy(completeDialogOpen = false)
        viewModelScope.launch {
            _state.value = _state.value.copy(mutating = true, error = null)
            val r = transportRepo.completeMyTrip(tripId, end, dist, fuel, notes)
            if (r.isFailure) {
                // Completion was REFUSED (e.g. a checkpoint still Pending), so
                // the trip is still running — tracking must keep going.
                _state.value = _state.value.copy(
                    mutating = false,
                    error = r.exceptionOrNull()?.message ?: "Could not complete the trip."
                )
                return@launch
            }
            // Stop streaming only after the server confirmed completion. This
            // is the courteous stop; the authoritative one already happened
            // server-side, which disarmed the kill switch and deleted the hot
            // document as part of the same transition.
            tripTracking.stop()
            _state.value = _state.value.copy(mutating = false)
            refresh()
        }
    }

    private fun mutate(op: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(mutating = true, error = null)
            val r = op()
            if (r.isFailure) {
                _state.value = _state.value.copy(mutating = false, error = r.exceptionOrNull()?.message)
            } else {
                _state.value = _state.value.copy(mutating = false)
                refresh()
            }
        }
    }
}
