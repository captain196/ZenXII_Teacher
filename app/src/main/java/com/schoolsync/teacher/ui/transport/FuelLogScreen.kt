package com.schoolsync.teacher.ui.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.VehicleDoc
import com.schoolsync.teacher.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FuelLogScreen — F9 (LC-22) driver fuel-log submission.
 *
 * Reuses TransportFirestoreRepository.submitFuelLog().
 * Refinement 3: server-side rule restricts driver writes to the
 * driver's currently assigned vehicle — the ViewModel auto-fetches
 * that vehicle so the driver can't accidentally submit against
 * someone else's bus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelLogScreen(
    onBack: () -> Unit,
    viewModel: FuelLogViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel log") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Vehicle")
                    Text(
                        state.vehicle?.registrationNo
                            ?: state.vehicle?.id
                            ?: "Loading…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
            OutlinedTextField(
                value = state.litres,
                onValueChange = viewModel::setLitres,
                label = { Text("Litres *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.pricePerLitre,
                onValueChange = viewModel::setPricePerLitre,
                label = { Text("Price per litre (INR)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.totalAmount,
                onValueChange = viewModel::setTotalAmount,
                label = { Text("Total amount (INR) *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.odometer,
                onValueChange = viewModel::setOdometer,
                label = { Text("Odometer reading (km)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.station,
                onValueChange = viewModel::setStation,
                label = { Text("Fuel station") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Text(it, color = Color.Red)
            }
            state.savedId?.let {
                Text("Saved: $it", color = Color(0xFF16A34A))
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.submit(onBack) },
                enabled = !state.submitting && state.vehicle != null && state.litres.isNotBlank() && state.totalAmount.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.submitting) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                else Text("Submit fuel log")
            }
        }
    }
}

data class FuelLogState(
    val vehicle: VehicleDoc? = null,
    val litres: String = "",
    val pricePerLitre: String = "",
    val totalAmount: String = "",
    val odometer: String = "",
    val station: String = "",
    val notes: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val savedId: String? = null
)

@HiltViewModel
class FuelLogViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(FuelLogState())
    val state: State<FuelLogState> = _state

    fun load() {
        viewModelScope.launch {
            val v = transportRepo.getMyAssignedVehicle().getOrNull()
            _state.value = _state.value.copy(vehicle = v)
        }
    }

    fun setLitres(v: String) { _state.value = _state.value.copy(litres = v, error = null, savedId = null) }
    fun setPricePerLitre(v: String) { _state.value = _state.value.copy(pricePerLitre = v) }
    fun setTotalAmount(v: String) { _state.value = _state.value.copy(totalAmount = v) }
    fun setOdometer(v: String) { _state.value = _state.value.copy(odometer = v) }
    fun setStation(v: String) { _state.value = _state.value.copy(station = v) }
    fun setNotes(v: String) { _state.value = _state.value.copy(notes = v) }

    fun submit(onSuccess: () -> Unit) {
        val v = _state.value.vehicle ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            val r = transportRepo.submitFuelLog(
                vehicleId = v.id,
                litres = _state.value.litres.toDoubleOrNull() ?: 0.0,
                pricePerLitre = _state.value.pricePerLitre.toDoubleOrNull() ?: 0.0,
                totalAmount = _state.value.totalAmount.toDoubleOrNull() ?: 0.0,
                odometerKm = _state.value.odometer.toDoubleOrNull() ?: 0.0,
                station = _state.value.station,
                notes = _state.value.notes
            )
            _state.value = if (r.isSuccess) {
                _state.value.copy(submitting = false, savedId = r.getOrNull())
            } else {
                _state.value.copy(submitting = false, error = r.exceptionOrNull()?.message)
            }
            if (r.isSuccess) onSuccess()
        }
    }
}
