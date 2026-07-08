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
import com.schoolsync.teacher.data.model.firestore.TripLogDoc
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip ${tripId.take(12)}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
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
                            Text(
                                "Roster (${state.trip.rosterStudentIds.size} students)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(state.trip.rosterStudentIds, key = { it }) { sid ->
                            RosterRow(
                                studentId = sid,
                                direction = state.trip.tripDirection.ifBlank { state.trip.tripType },
                                onBoard = { viewModel.markBoarded(sid) },
                                onDrop = { viewModel.markDropped(sid) },
                                onNoShow = { viewModel.markNoShow(sid) }
                            )
                        }
                        item {
                            Button(
                                onClick = { viewModel.openCompleteDialog() },
                                enabled = !state.mutating,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Complete Trip") }
                        }
                    }
                    state.error?.let { item { Text(it, color = Color.Red) } }
                }
            }
        }
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

@Composable
private fun RosterRow(
    studentId: String,
    direction: String,
    onBoard: () -> Unit,
    onDrop: () -> Unit,
    onNoShow: () -> Unit
) {
    val isPickup = direction.equals("Pickup", ignoreCase = true) || direction.contains("morning", ignoreCase = true)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(studentId, modifier = Modifier.weight(1f))
            if (isPickup) {
                Button(onClick = onBoard) { Text("Board") }
            } else {
                Button(onClick = onDrop) { Text("Drop") }
            }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = onNoShow,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
            ) { Text("No-show") }
        }
    }
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
    val error: String? = null
)

@HiltViewModel
class DriverTripDetailViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(DriverTripDetailState())
    val state: State<DriverTripDetailState> = _state

    private var tripId: String = ""

    fun load(tid: String) {
        tripId = tid
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        val trip = transportRepo.getMyActiveTrips().getOrDefault(emptyList()).firstOrNull { it.id == tripId }
            ?: transportRepo.getMyActiveTrips().getOrDefault(emptyList()).firstOrNull()
        _state.value = _state.value.copy(loading = false, trip = trip, error = null)
    }

    fun startTrip() = mutate { transportRepo.startMyTrip(tripId) }
    fun markReached(stopId: String) = mutate { transportRepo.markCheckpointReached(tripId, stopId) }
    fun markBoarded(sid: String) = mutate { transportRepo.markStudentBoarded(tripId, sid).map { } }
    fun markDropped(sid: String) = mutate { transportRepo.markStudentDropped(tripId, sid).map { } }
    fun markNoShow(sid: String) = mutate { transportRepo.markStudentNoShow(tripId, sid).map { } }

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
        mutate { transportRepo.completeMyTrip(tripId, end, dist, fuel, notes) }
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
