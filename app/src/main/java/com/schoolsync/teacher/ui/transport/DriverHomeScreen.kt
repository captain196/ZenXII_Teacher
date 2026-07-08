package com.schoolsync.teacher.ui.transport

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.TripLogDoc
import com.schoolsync.teacher.data.model.firestore.VehicleDoc
import com.schoolsync.teacher.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DriverHomeScreen — F9 (LC-22) driver operational landing.
 *
 * Reuses existing TransportFirestoreRepository methods:
 *   • getMyAssignedVehicle() → the vehicle assigned to the signed-in
 *     driver (server-side vehicles.staff_id == user_id claim)
 *   • getMyActiveTrips() → today's Ready+InProgress trips assigned
 *     to me (composite index (schoolId, driver_staff_id, status,
 *     date DESC))
 *
 * Navigation:
 *   • Trip row tap → DriverTripDetailScreen (start/checkpoints/
 *     boarding/complete)
 *   • Fuel Log button → FuelLogScreen (Refinement 3: driver-scoped
 *     to assigned vehicle, rule-enforced)
 *   • SOS button → SosTriggerScreen (F9 severity picker)
 *
 * No new business logic — this is the presentation layer that
 * surfaces the F9-shipped repository writers to drivers through
 * the standard nav rail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    onOpenTrip: (tripId: String) -> Unit,
    onOpenFuelLog: () -> Unit,
    onOpenSos: () -> Unit,
    viewModel: DriverHomeViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Transport (Driver)") })
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.notADriver -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Transport module is available only to staff who have transport-driving enabled in HR.",
                    color = Color.Gray,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { VehicleCard(state.vehicle) }
                item { QuickActionsRow(onFuelLog = onOpenFuelLog, onSos = onOpenSos) }
                item {
                    Text(
                        "Today's trips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (state.trips.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("No active trips assigned to you today.", color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "The school office schedules trips in the admin panel. Once a trip is dispatched, it appears here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                } else {
                    items(state.trips, key = { it.id }) { trip ->
                        TripRow(trip = trip, onClick = { onOpenTrip(trip.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleDoc?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("My assigned vehicle", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (vehicle == null) {
                Text("No vehicle assigned to you today.", color = Color.Gray)
            } else {
                Text("Number: ${vehicle.registrationNo.ifBlank { vehicle.id }}")
                if (vehicle.make.isNotBlank() || vehicle.model.isNotBlank()) {
                    Text("Model: ${"${vehicle.make} ${vehicle.model}".trim()}")
                }
                if (vehicle.currentRouteId.isNotBlank()) {
                    Text("Route: ${vehicle.currentRouteId}")
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(onFuelLog: () -> Unit, onSos: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onFuelLog,
            modifier = Modifier.weight(1f)
        ) { Text("Fuel log") }
        Button(
            onClick = onSos,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) { Text("SOS", color = Color.White) }
    }
}

@Composable
private fun TripRow(trip: TripLogDoc, onClick: () -> Unit) {
    val (label, tint) = when (trip.status) {
        "Ready"      -> "Ready"       to Color(0xFF3B82F6)
        "InProgress" -> "In progress" to Color(0xFFF59E0B)
        "Completed"  -> "Completed"   to Color(0xFF16A34A)
        "Cancelled"  -> "Cancelled"   to Color(0xFFDC2626)
        else         -> trip.status   to Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(shape = RoundedCornerShape(50), color = tint) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    trip.id.ifBlank { "Trip" },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${trip.tripDirection.ifBlank { trip.tripType }} · Route ${trip.routeId.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    "Roster: ${trip.rosterStudentIds.size} students",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

data class DriverHomeState(
    val loading: Boolean = true,
    val notADriver: Boolean = false,
    val vehicle: VehicleDoc? = null,
    val trips: List<TripLogDoc> = emptyList()
)

@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(DriverHomeState())
    val state: State<DriverHomeState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = DriverHomeState(loading = true)
            val myStaff = transportRepo.getMyDriverInfo().getOrNull()
            if (myStaff == null) {
                _state.value = DriverHomeState(loading = false, notADriver = true)
                return@launch
            }
            val vehicle = transportRepo.getMyAssignedVehicle().getOrNull()
            val trips = transportRepo.getMyActiveTrips().getOrDefault(emptyList())
            _state.value = DriverHomeState(
                loading = false,
                notADriver = false,
                vehicle = vehicle,
                trips = trips
            )
        }
    }
}
