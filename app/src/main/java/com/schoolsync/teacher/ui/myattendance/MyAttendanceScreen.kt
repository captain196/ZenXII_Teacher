package com.schoolsync.teacher.ui.myattendance

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.location.LocationError
import com.schoolsync.teacher.data.location.LocationPermissions
import com.schoolsync.teacher.data.repository.TodayAttendance
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * MyAttendanceScreen — GPS staff self-attendance (Phase 13).
 *
 * Commercial-HR style: guides the user BEFORE they punch (today's status,
 * check-in/out times, working duration, GPS availability/accuracy, distance
 * from campus, inside/outside hint, and the current primary action).
 *
 * Rules honoured:
 *   - NEVER auto-records on open. A location fix is acquired only for GUIDANCE;
 *     attendance is recorded ONLY when the user taps Check In / Check Out.
 *   - Local geofence math is guidance only; the BACKEND is the final authority.
 *   - Full runtime permission handling: first request, denied, "Don't ask
 *     again" (→ Settings), GPS disabled (→ Location settings), high-accuracy
 *     unavailable (retry), and auto-retry on resume after the user fixes it.
 */
@Composable
fun MyAttendanceScreen(vm: StaffAttendanceViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun hasPerm(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        permGranted = granted
        if (granted) {
            permanentlyDenied = false
            vm.refreshGpsStatus()
        } else {
            val act = context as? Activity
            val rationale = act?.let {
                it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            } ?: false
            // Denied with no rationale ⇒ "Don't ask again" / permanently denied.
            permanentlyDenied = LocationPermissions.interpret(false, rationale) == LocationPermissions.Status.PERMANENTLY_DENIED
        }
    }

    // First load — does NOT punch. Loads data + geofence; refreshes GPS guidance
    // only when permission is already granted.
    LaunchedEffect(Unit) {
        vm.loadMe()
        vm.loadGeofence()
        if (hasPerm()) vm.refreshGpsStatus()
    }

    // Re-check on resume so returning from Settings (granted permission / enabled
    // GPS) auto-refreshes the guidance — the "retry after enabling" flow.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permGranted = hasPerm()
                if (permGranted) vm.refreshGpsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val today = ui.me?.today
    val primary = primaryAction(today)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My Attendance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // ── Today status ──
        TodayCard(today = today, loading = ui.loading)

        // ── GPS guidance (advisory only) ──
        GpsStatusCard(
            available = ui.gps?.available == true,
            accuracy = ui.gps?.accuracyMeters,
            distance = ui.gps?.distanceMeters,
            inside = ui.gps?.insideGeofence,
            refreshing = ui.gpsRefreshing,
            onRefresh = { if (hasPerm()) vm.refreshGpsStatus() else permLauncher.launch(LocationPermissions.REQUIRED) }
        )

        // ── Permission / GPS state prompts ──
        if (!permGranted) {
            StateCard(
                title = if (permanentlyDenied) "Location permission blocked" else "Location permission needed",
                body = if (permanentlyDenied)
                    "Enable Location for this app in system Settings to mark attendance."
                else
                    "Attendance needs your location to confirm you are on campus.",
                actionLabel = if (permanentlyDenied) "Open Settings" else "Grant Permission",
                onAction = {
                    if (permanentlyDenied) openAppSettings(context)
                    else permLauncher.launch(LocationPermissions.REQUIRED)
                }
            )
        } else when (ui.gpsError) {
            LocationError.LOCATION_SERVICES_OFF -> StateCard(
                title = "Location is turned off",
                body = "Turn on device location (GPS), then retry.",
                actionLabel = "Open Location Settings",
                onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                secondaryLabel = "Retry",
                onSecondary = { vm.refreshGpsStatus() }
            )
            LocationError.TIMEOUT, LocationError.UNAVAILABLE -> StateCard(
                title = "Weak GPS signal",
                body = "Couldn't get an accurate fix. Move to an open area and retry. " +
                    "If location is set to Approximate, enable Precise location for this app.",
                actionLabel = "Retry",
                onAction = { vm.refreshGpsStatus() }
            )
            LocationError.PERMISSION_DENIED -> StateCard(
                title = "Permission required",
                body = "Grant location permission to continue.",
                actionLabel = "Grant Permission",
                onAction = { permLauncher.launch(LocationPermissions.REQUIRED) }
            )
            null -> Unit
        }

        // ── Primary action (explicit tap only) ──
        PrimaryActionButton(
            primary = primary,
            busy = ui.punching,
            onClick = {
                when {
                    !hasPerm() -> permLauncher.launch(LocationPermissions.REQUIRED)
                    primary == "in" -> vm.checkIn()
                    primary == "out" -> vm.checkOut()
                }
            }
        )

        // ── Result / error banner ──
        ui.lastResult?.let { r ->
            ResultBanner(text = r.message.ifBlank { "Done." }, color = Color(0xFF16A34A))
        }
        ui.error?.let { e ->
            ResultBanner(text = e.message ?: "Something went wrong.", color = Color(0xFFDC2626))
        }

        // ── 30-day history ──
        ui.me?.history?.takeIf { it.isNotEmpty() }?.let { history ->
            Text("Last 30 days", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    history.asReversed().forEach { day ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(day.date, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            StatusChip(day.status)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

/* ── pieces ─────────────────────────────────────────────────────── */

@Composable
private fun TodayCard(today: TodayAttendance?, loading: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Today", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else StatusChip(today?.status ?: "V")
            }
            InfoRow("Check-in", formatTime(today?.checkInAt) ?: "—")
            InfoRow("Check-out", formatTime(today?.checkOutAt) ?: "—")
            val dur = workingDuration(today?.checkInAt, today?.checkOutAt)
            if (dur != null) InfoRow("Working duration", dur)
        }
    }
}

@Composable
private fun GpsStatusCard(
    available: Boolean, accuracy: Float?, distance: Double?, inside: Boolean?,
    refreshing: Boolean, onRefresh: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = if (available) Color(0xFF16A34A) else Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("GPS status", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                if (refreshing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).clickable(onClick = onRefresh))
            }
            InfoRow("GPS", if (available) "Available" else "Unavailable")
            InfoRow("Accuracy", accuracy?.let { "±${it.roundToInt()} m" } ?: "—")
            InfoRow("Distance from school", distance?.let { "${it.roundToInt()} m" } ?: "—")
            if (inside != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Geofence", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Pill(text = if (inside) "Inside campus" else "Outside campus", color = if (inside) Color(0xFF16A34A) else Color(0xFFD97706))
                }
            }
            Text(
                "Guidance only — the server makes the final attendance decision.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(primary: String, busy: Boolean, onClick: () -> Unit) {
    val (label, color, enabled) = when (primary) {
        "in" -> Triple("Check In", Color(0xFF16A34A), true)
        "out" -> Triple("Check Out", Color(0xFF2563EB), true)
        else -> Triple("Attendance Completed", Color(0xFF6B7280), false)
    }
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StateCard(
    title: String, body: String, actionLabel: String, onAction: () -> Unit,
    secondaryLabel: String? = null, onSecondary: (() -> Unit)? = null
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412))
            Text(body, fontSize = 12.5.sp, color = Color(0xFF7C2D12))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))) {
                    Text(actionLabel)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(text: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Text(text, modifier = Modifier.padding(14.dp), fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status.uppercase()) {
        "P" -> "Present" to Color(0xFF16A34A)
        "T" -> "Late" to Color(0xFFD97706)
        "A" -> "Absent" to Color(0xFFDC2626)
        "L" -> "Leave" to Color(0xFF2563EB)
        "H" -> "Holiday" to Color(0xFF6B7280)
        else -> "Not marked" to Color(0xFF6B7280)
    }
    Pill(label, color)
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/* ── helpers (pure) ─────────────────────────────────────────────── */

/** Primary action: Check In until checked in, then Check Out, then done. */
private fun primaryAction(today: TodayAttendance?): String = when {
    today?.checkInAt.isNullOrBlank() -> "in"
    today?.checkOutAt.isNullOrBlank() -> "out"
    else -> "done"
}

private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
private val HM = SimpleDateFormat("hh:mm a", Locale.US)

private fun formatTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching { HM.format(ISO.parse(iso)!!) }.getOrNull()
}

private fun workingDuration(checkIn: String?, checkOut: String?): String? {
    if (checkIn.isNullOrBlank()) return null
    val start = runCatching { ISO.parse(checkIn)!!.time }.getOrNull() ?: return null
    val end = if (!checkOut.isNullOrBlank()) runCatching { ISO.parse(checkOut)!!.time }.getOrNull() else System.currentTimeMillis()
    if (end == null || end < start) return null
    val mins = ((end - start) / 60000L).toInt()
    return "${mins / 60}h ${mins % 60}m"
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
