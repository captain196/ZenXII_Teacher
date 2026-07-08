package com.schoolsync.teacher.ui.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SosTriggerScreen — F9 (2026-07-07) driver SOS panic button with
 * severity picker.
 *
 * Severity levels (server-side Transport_notifier::SOS_SEVERITIES):
 *   Low      → Normal    (informational)
 *   Medium   → Important (dedup honored)
 *   High     → Urgent    (safety exemption — never deduped) — DEFAULT
 *   Critical → Urgent    (safety exemption — never deduped)
 *
 * Refinement 1 (2026-07-07): default = 'High' — driver may explicitly
 * lower if the situation is less critical, but the default biases toward
 * operational safety.
 *
 * The screen writes DIRECTLY to Firestore via TransportFirestoreRepository.
 * The Firestore rule `sosAlerts.create` validates the enum + demands
 * client diagnostic metadata; this composable relies on the repository
 * layer to attach ClientMetadata.asMap() to the write payload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosTriggerScreen(
    onBack: () -> Unit,
    viewModel: SosTriggerViewModel = hiltViewModel()
) {
    val state = viewModel.state.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raise SOS") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Emergency broadcast — Admin, Transport Manager, Principal, and parents of your vehicle's route will receive this notification.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SeverityPicker(
                current = state.severity,
                onPick = { viewModel.setSeverity(it) }
            )

            AlertTypePicker(
                current = state.alertType,
                onPick = { viewModel.setAlertType(it) }
            )

            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                label = { Text("Message *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { viewModel.trigger() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                enabled = !state.submitting && state.message.isNotBlank()
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Raise SOS (${state.severity})")
                }
            }

            if (state.error != null) {
                Text(state.error, color = Color.Red)
            }
        }
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelConfirm() },
            title = { Text("Confirm SOS — ${state.severity}") },
            text = {
                Text("This will notify Admin, Transport Manager, Principal, and all parents on the affected route. Continue?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAndSubmit() }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelConfirm() }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(state.sentSosId) {
        if (state.sentSosId != null) {
            onBack()
        }
    }
}

@Composable
private fun SeverityPicker(current: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Severity *", style = MaterialTheme.typography.labelSmall)
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = severityColor(current))
        ) {
            Text("$current (default High)", color = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Low", "Medium", "High", "Critical").forEach { level ->
                DropdownMenuItem(
                    text = { Text(level) },
                    onClick = {
                        onPick(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AlertTypePicker(current: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Alert Type *", style = MaterialTheme.typography.labelSmall)
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(current)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("emergency", "breakdown", "accident", "medical", "other").forEach { t ->
                DropdownMenuItem(
                    text = { Text(t) },
                    onClick = {
                        onPick(t)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun severityColor(severity: String): Color = when (severity) {
    "Low"      -> Color(0xFF3B82F6)   // blue
    "Medium"   -> Color(0xFFF59E0B)   // amber
    "High"     -> Color(0xFFDC2626)   // red
    "Critical" -> Color(0xFF7F1D1D)   // dark red
    else       -> Color(0xFF6B7280)   // grey
}

data class SosTriggerState(
    val severity: String = "High",     // Refinement 1: default High
    val alertType: String = "emergency",
    val message: String = "",
    val confirming: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val sentSosId: String? = null
)

@HiltViewModel
class SosTriggerViewModel @Inject constructor(
    private val repo: TransportFirestoreRepository
) : ViewModel() {

    private val _state = androidx.compose.runtime.mutableStateOf(SosTriggerState())
    val state: androidx.compose.runtime.State<SosTriggerState> = _state

    fun setSeverity(sev: String) { _state.value = _state.value.copy(severity = sev) }
    fun setAlertType(t: String)  { _state.value = _state.value.copy(alertType = t) }
    fun setMessage(m: String)    { _state.value = _state.value.copy(message = m, error = null) }

    fun trigger() {
        if (_state.value.message.isBlank()) return
        _state.value = _state.value.copy(confirming = true)
    }
    fun cancelConfirm() { _state.value = _state.value.copy(confirming = false) }

    fun confirmAndSubmit() {
        _state.value = _state.value.copy(confirming = false, submitting = true, error = null)
        viewModelScope.launch {
            // The repository looks up the driver's assigned vehicle so
            // vehicle/route context is auto-attached; falls back to
            // empty strings when unassigned (rules still accept because
            // sosAlerts rule doesn't require driver-vehicle FK — SOS is
            // always fireable).
            val vehicle = repo.getMyAssignedVehicle().getOrNull()
            val result = repo.triggerSos(
                vehicleId = vehicle?.id ?: "",
                routeId   = vehicle?.currentRouteId ?: "",
                alertType = _state.value.alertType,
                severity  = _state.value.severity,
                lat       = 0.0,   // lat/lng: F9 uses device location when permission granted (not yet plumbed); server-side priority routing unaffected
                lng       = 0.0,
                message   = _state.value.message
            )
            _state.value = if (result.isSuccess) {
                _state.value.copy(submitting = false, sentSosId = result.getOrNull())
            } else {
                _state.value.copy(submitting = false, error = result.exceptionOrNull()?.message ?: "SOS send failed")
            }
        }
    }
}
