package com.schoolsync.teacher.ui.myattendance

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.location.LocationError
import com.schoolsync.teacher.data.location.LocationPermissions
import com.schoolsync.teacher.data.repository.HistoryDay
import com.schoolsync.teacher.data.repository.TodayAttendance
import com.schoolsync.teacher.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * MyAttendanceScreen — GPS staff self-attendance, minimal "clock" design.
 *
 * Two views (matching the approved reference):
 *   • Home     — quiet GPS chip, a live hero clock, one big Clock-In button that
 *                becomes a running timer + red Clock-Out, and a ghost link to the
 *                month calendar.
 *   • Calendar — a month grid coloured by daily status (from the 30-day history).
 *
 * Rules preserved from the earlier build:
 *   - NEVER auto-records on open. A fix is acquired only for GUIDANCE; a punch is
 *     sent ONLY when the user taps Clock In / Clock Out.
 *   - Local geofence math is guidance only; the BACKEND is the final authority.
 *   - Full runtime permission handling: request, denied, "Don't ask again"
 *     (→ Settings), GPS disabled (→ Location settings), weak signal (retry), and
 *     auto-retry on resume after the user fixes it.
 *
 * All colours come from [LocalAppColors] (Soft Blue theme) so it light/dark-switches.
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
            val rationale = act?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ?: false
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

    // Re-check on resume so returning from Settings auto-refreshes the guidance.
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

    val requestLocation: () -> Unit = {
        if (hasPerm()) vm.refreshGpsStatus() else permLauncher.launch(LocationPermissions.REQUIRED)
    }

    var showCalendar by remember { mutableStateOf(false) }

    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgStart)
    ) {
        if (showCalendar) {
            CalendarView(
                today = ui.me?.today,
                history = ui.me?.history.orEmpty(),
                onBack = { showCalendar = false },
            )
        } else {
            HomeView(
                ui = ui,
                vm = vm,
                permGranted = permGranted,
                permanentlyDenied = permanentlyDenied,
                onRequestLocation = requestLocation,
                onRequestPermission = { permLauncher.launch(LocationPermissions.REQUIRED) },
                onOpenAppSettings = { openAppSettings(context) },
                onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                onOpenCalendar = { showCalendar = true },
            )
        }
    }
}

/* ══════════════════════════ HOME ══════════════════════════ */

@Composable
private fun HomeView(
    ui: StaffAttendanceUiState,
    vm: StaffAttendanceViewModel,
    permGranted: Boolean,
    permanentlyDenied: Boolean,
    onRequestLocation: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val c = LocalAppColors.current

    // Live ticking clock (1 Hz). Drives the hero time and the running timer.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val today = ui.me?.today
    val checkInMs = parseIso(today?.checkInAt)
    val checkOutMs = parseIso(today?.checkOutAt)
    var showGpsDetail by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(24.dp)
    val done = checkInMs != null && checkOutMs != null
    val isRestDay = today?.isWeeklyOff == true || today?.isHoliday == true
    val restLabel = if (today?.isHoliday == true) "holiday" else "weekly-off"
    var showOffConfirm by remember { mutableStateOf(false) }
    // On a rest day before any punch, today.status is still "V"; surface the
    // rest-day nature in the pill so the employee sees Weekly-off / Holiday.
    val pillStatus = when {
        (today?.status ?: "V").uppercase() in setOf("P", "T", "M", "A", "L", "H", "O", "W") -> today!!.status
        today?.isHoliday == true -> "H"
        today?.isWeeklyOff == true -> "O"
        else -> "V"
    }

    if (showOffConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOffConfirm = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showOffConfirm = false; vm.checkIn() }) { Text("Clock in anyway") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOffConfirm = false }) { Text("Not now") }
            },
            title = { Text("Today is a $restLabel") },
            text = { Text("Clocking in will record today as extra work and add extra pay for the day, per your company policy.") },
        )
    }

    // Two balanced panels: LEFT = live-clock card, RIGHT = today's attendance
    // + action. Each is a filled card so the layout reads as a designed
    // dashboard rather than text floating in empty space.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ══ LEFT CARD — live clock ══
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cardShape)
                .background(c.surfaceCard)
                .border(BorderStroke(1.dp, c.divider), cardShape)
                .padding(28.dp),
        ) {
            GpsChip(
                available = ui.gps?.available == true,
                inside = ui.gps?.insideGeofence,
                distance = ui.gps?.distanceMeters,
                locating = ui.gpsRefreshing,
                onClick = {
                    if (ui.gps?.available == true) showGpsDetail = !showGpsDetail else onRequestLocation()
                },
            )
            if (showGpsDetail && ui.gps?.available == true) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Accuracy ± ${ui.gps?.accuracyMeters?.roundToInt() ?: "—"} m", fontSize = 12.5.sp, color = c.textSecondary)
                    Text("•", color = c.divider)
                    Text("${fmtDistance(ui.gps?.distanceMeters)} from school", fontSize = 12.5.sp, color = c.textSecondary)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dateLine(now), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            clock12(cal),
                            fontSize = 88.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                            letterSpacing = (-2).sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            amPm(cal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textTertiary,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }
            }
        }

        // ══ RIGHT CARD — today's attendance + action ══
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cardShape)
                .background(c.surfaceCard)
                .border(BorderStroke(1.dp, c.divider), cardShape)
                .padding(28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today's Attendance", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                TodayStatusPill(pillStatus)
            }
            Spacer(Modifier.height(20.dp))
            InfoRow("Check-in", fmtHM(checkInMs))
            Spacer(Modifier.height(12.dp))
            InfoRow("Check-out", fmtHM(checkOutMs))
            Spacer(Modifier.height(12.dp))
            InfoRow("Working duration", fmtDuration(checkInMs, checkOutMs, now))

            // Slack pushes the notices + action to the bottom of the card.
            Spacer(Modifier.weight(1f))

            ui.lastResult?.let { r ->
                if (r.message.isNotBlank()) {
                    InfoLine(text = r.message, color = c.success, surface = c.successSurface, icon = Icons.Filled.CheckCircle)
                    Spacer(Modifier.height(12.dp))
                }
            }
            val stateCard = resolveStateCard(permGranted, permanentlyDenied, ui.gpsError)
            stateCard?.let { sc ->
                StatePrompt(
                    title = sc.title,
                    body = sc.body,
                    actionLabel = sc.actionLabel,
                    onAction = when (sc.action) {
                        PromptAction.GRANT -> onRequestPermission
                        PromptAction.APP_SETTINGS -> onOpenAppSettings
                        PromptAction.LOCATION_SETTINGS -> onOpenLocationSettings
                        PromptAction.RETRY -> ({ vm.refreshGpsStatus() })
                    },
                    secondaryLabel = sc.secondaryLabel,
                    onSecondary = { vm.refreshGpsStatus() },
                )
                Spacer(Modifier.height(12.dp))
            }
            ui.error?.let { e ->
                InfoLine(text = e.message ?: "Something went wrong.", color = c.error, surface = c.errorSurface, icon = Icons.Filled.WarningAmber)
                Spacer(Modifier.height(12.dp))
            }

            when {
                done -> InfoLine(
                    text = "All done for today · ${fmtHM(checkInMs)} — ${fmtHM(checkOutMs)}",
                    color = c.success, surface = c.successSurface, icon = Icons.Filled.CheckCircle,
                )
                checkInMs == null -> ClockInButton(
                    busy = ui.punching,
                    onClick = {
                        when {
                            !permGranted -> onRequestPermission()
                            isRestDay && today?.allowWorkOnOff == true -> showOffConfirm = true
                            else -> vm.checkIn()
                        }
                    },
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RunningCard(sinceMs = checkInMs, now = now)
                    ClockOutButton(
                        busy = ui.punching,
                        onClick = { if (!permGranted) onRequestPermission() else vm.checkOut() },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenCalendar)
                    .padding(vertical = 13.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Attendance calendar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
        }
    }
}

/* ── Today panel pieces ─────────────────────────────────────────── */

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.5.sp, color = c.textSecondary)
        Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
    }
}

@Composable
private fun TodayStatusPill(status: String) {
    val c = LocalAppColors.current
    val (label, color) = when (status.uppercase()) {
        "P" -> "Present" to c.attPresent
        "T" -> "Late" to c.attTardy
        "M" -> "Half-day" to c.attLeave
        "A" -> "Absent" to c.attAbsent
        "L" -> "Leave" to c.info
        "H" -> "Holiday" to c.attHoliday
        "O" -> "Weekly-off" to c.textTertiary
        "W" -> "Extra day" to c.attVacation
        else -> "Not marked" to c.textTertiary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun GpsChip(
    available: Boolean,
    inside: Boolean?,
    distance: Double?,
    locating: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val (dot, text, tone) = when {
        available && inside == true -> Triple(c.success, "On campus", c.success)
        available && inside == false -> Triple(c.warning, "${fmtDistance(distance)} away", c.warning)
        available -> Triple(c.success, "Located", c.success)
        else -> Triple(c.textTertiary, if (locating) "Locating…" else "Location off", c.textSecondary)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(c.surfaceCard)
            .border(BorderStroke(1.dp, c.divider), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = tone)
        if (locating) {
            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.6.dp, color = c.textTertiary)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ClockInButton(busy: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    BigButton(
        bg = c.accent,
        content = c.textOnAccent,
        onClick = onClick,
        enabled = !busy,
    ) {
        BigIconBox(tint = c.textOnAccent) { Icon(Icons.Filled.HowToReg, contentDescription = null, tint = c.textOnAccent, modifier = Modifier.size(24.dp)) }
        Text(if (busy) "Clocking in…" else "Clock in", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textOnAccent)
    }
}

@Composable
private fun ClockOutButton(busy: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    BigButton(
        bg = c.error,
        content = Color.White,
        onClick = onClick,
        enabled = !busy,
    ) {
        BigIconBox(tint = Color.White) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Text(if (busy) "Clocking out…" else "Clock out", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun RunningCard(sinceMs: Long, now: Long) {
    val c = LocalAppColors.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.accent)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(c.textOnAccent.copy(alpha = alpha)))
        Column(Modifier.weight(1f)) {
            Text("Working since ${fmtHM(sinceMs)}", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = c.textOnAccent.copy(alpha = 0.85f))
            Spacer(Modifier.height(2.dp))
            Text(fmtElapsed(now - sinceMs), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = c.textOnAccent, letterSpacing = (-0.5).sp)
        }
    }
}

@Composable
private fun DoneCard(inMs: Long?, outMs: Long?) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceCard)
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.success, modifier = Modifier.size(26.dp))
        Column {
            Text("All done for today", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text("${fmtHM(inMs)} — ${fmtHM(outMs)}", fontSize = 14.sp, color = c.textSecondary)
        }
    }
}

@Composable
private fun BigButton(
    bg: Color,
    content: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    row: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        content = row,
    )
}

@Composable
private fun BigIconBox(tint: Color, icon: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.20f)),
    ) { icon() }
}

@Composable
private fun InfoLine(text: String, color: Color, surface: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun StatePrompt(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.warningSurface)
            .border(BorderStroke(1.dp, c.warning.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
        Text(body, fontSize = 12.5.sp, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(actionLabel, bg = c.warning, content = Color.White, onClick = onAction)
            if (secondaryLabel != null) {
                PillButton(secondaryLabel, bg = c.surfaceCard, content = c.textPrimary, border = c.divider, onClick = onSecondary)
            }
        }
    }
}

@Composable
private fun PillButton(label: String, bg: Color, content: Color, border: Color? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), RoundedCornerShape(11.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

/* ══════════════════════════ CALENDAR ══════════════════════════ */

@Composable
private fun CalendarView(
    today: TodayAttendance?,
    history: List<HistoryDay>,
    onBack: () -> Unit,
) {
    val c = LocalAppColors.current

    val nowCal = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(nowCal.get(Calendar.MONTH)) } // 0-based

    // date -> status map from the 30-day history + today's live status.
    val statusByDate = remember(history, today) {
        buildMap {
            history.forEach { put(it.date, it.status) }
            today?.let { if (it.date.isNotBlank()) put(it.date, it.status) }
        }
    }

    val isThisMonth = year == nowCal.get(Calendar.YEAR) && month == nowCal.get(Calendar.MONTH)
    val todayDay = nowCal.get(Calendar.DAY_OF_MONTH)

    var showRegularize by remember { mutableStateOf(false) }

    // Candidate dates for regularization: past workdays in the displayed month
    // that are Absent (A) or explicitly unmarked (V). Weekends + future skipped.
    val todayKey = String.format(
        Locale.US, "%04d-%02d-%02d",
        nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH) + 1, nowCal.get(Calendar.DAY_OF_MONTH),
    )
    val regularizeCandidates = remember(year, month, statusByDate, todayKey) {
        val out = mutableListOf<String>()
        val mCal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val dim = mCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in 1..dim) {
            val key = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, d)
            if (key > todayKey) continue
            val dow = Calendar.getInstance().apply { clear(); set(year, month, d) }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) continue
            when (statusByDate[key]?.uppercase()) {
                "A", "V" -> out.add(key)
            }
        }
        out
    }

    fun shift(delta: Int) {
        var m = month + delta
        var y = year
        if (m < 0) { m = 11; y-- }
        if (m > 11) { m = 0; y++ }
        month = m; year = y
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Responsive: cap width + center so cells stay calendar-sized on wide /
      // landscape screens instead of stretching into oversized rows.
      Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        // Top bar: back · month nav · Today
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SquareIconButton(Icons.Filled.ChevronLeft, "Previous month", { shift(-1) }, small = true)
                Text(
                    "${MONTHS[month]} $year",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 150.dp),
                )
                SquareIconButton(Icons.Filled.ChevronRight, "Next month", { shift(1) }, small = true)
            }
            if (!isThisMonth) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.accentSurface)
                        .clickable { year = nowCal.get(Calendar.YEAR); month = nowCal.get(Calendar.MONTH) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text("Today", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accent)
                }
            } else {
                Spacer(Modifier.width(56.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        CalendarLegend()

        // ── Regularize Attendance ──
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.accentSurface)
                .border(BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                .clickable { showRegularize = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.EditCalendar, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Regularize Attendance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent)
                Text("Request a correction for absent or missed days", fontSize = 11.5.sp, color = c.textSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Weekday header (Mon-first)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WEEKDAYS.forEach { w ->
                Text(
                    w,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textTertiary,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Grid — Monday-first leading blanks
        val firstCal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val leading = (firstCal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0 … Sun=6
        val dim = firstCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = buildList {
            repeat(leading) { add(null) }
            for (d in 1..dim) add(d)
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (day == null) {
                            Spacer(Modifier.fillMaxWidth().aspectRatio(0.86f))
                        } else {
                            val key = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                            val dowCal = Calendar.getInstance().apply { clear(); set(year, month, day) }
                            val weekend = dowCal.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
                            DayCell(
                                day = day,
                                status = statusByDate[key],
                                weekend = weekend,
                                isToday = isThisMonth && day == todayDay,
                                todayTimes = if (isThisMonth && day == todayDay) today else null,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
      }
    }

    if (showRegularize) {
        RegularizeSheet(
            candidateDates = regularizeCandidates,
            onDismiss = { showRegularize = false },
        )
    }
}

@Composable
private fun DayCell(
    day: Int,
    status: String?,
    weekend: Boolean,
    isToday: Boolean,
    todayTimes: TodayAttendance?,
) {
    val c = LocalAppColors.current
    val band = statusBand(status, weekend)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceCard)
            .border(
                BorderStroke(if (isToday) 2.dp else 1.dp, if (isToday) c.accent else c.divider),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$day", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
        if (band != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(band.color.copy(alpha = 0.16f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(band.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = band.color, maxLines = 1)
            }
        }
        // Only today carries check-in/out times in the current backend contract.
        if (isToday && todayTimes != null) {
            val tin = parseIso(todayTimes.checkInAt)
            val tout = parseIso(todayTimes.checkOutAt)
            if (tin != null) {
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(fmtClockOnly(tin), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.success)
                    Text("→", fontSize = 10.sp, color = c.textTertiary)
                    Text(if (tout != null) fmtClockOnly(tout) else "—", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    val c = LocalAppColors.current
    val items = listOf(
        "Present" to c.attPresent,
        "Late" to c.attTardy,
        "Half" to c.attLeave,
        "Absent" to c.attAbsent,
        "Leave" to c.info,
        "Holiday" to c.attHoliday,
        "Extra" to c.attVacation,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.35f)))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun SquareIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    small: Boolean = false,
) {
    val c = LocalAppColors.current
    val s = if (small) 36.dp else 42.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(s)
            .clip(RoundedCornerShape(if (small) 11.dp else 13.dp))
            .background(c.surfaceCard)
            .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(if (small) 11.dp else 13.dp))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = desc, tint = c.textPrimary, modifier = Modifier.size(18.dp))
    }
}

/* ══════════════════════════ helpers (pure) ══════════════════════════ */

private data class Band(val label: String, val color: Color)

@Composable
private fun statusBand(status: String?, weekend: Boolean): Band? {
    val c = LocalAppColors.current
    return when (status?.uppercase()) {
        "P" -> Band("Present", c.attPresent)
        "T" -> Band("Late", c.attTardy)
        "M" -> Band("Half", c.attLeave)
        "A" -> Band("Absent", c.attAbsent)
        "L" -> Band("Leave", c.info)
        "H" -> Band("Holiday", c.attHoliday)
        "O" -> Band("Off", c.textTertiary)
        "W" -> Band("Extra", c.attVacation)
        else -> if (weekend) Band("Off", c.textTertiary) else null
    }
}

private enum class PromptAction { GRANT, APP_SETTINGS, LOCATION_SETTINGS, RETRY }
private data class StateCardSpec(
    val title: String,
    val body: String,
    val actionLabel: String,
    val action: PromptAction,
    val secondaryLabel: String? = null,
)

private fun resolveStateCard(
    permGranted: Boolean,
    permanentlyDenied: Boolean,
    gpsError: LocationError?,
): StateCardSpec? {
    if (!permGranted) {
        return if (permanentlyDenied) StateCardSpec(
            title = "Location permission blocked",
            body = "Enable Location for this app in system Settings to mark attendance.",
            actionLabel = "Open Settings",
            action = PromptAction.APP_SETTINGS,
        ) else StateCardSpec(
            title = "Location permission needed",
            body = "Attendance needs your location to confirm you are on campus.",
            actionLabel = "Grant Permission",
            action = PromptAction.GRANT,
        )
    }
    return when (gpsError) {
        LocationError.LOCATION_SERVICES_OFF -> StateCardSpec(
            title = "Location is turned off",
            body = "Turn on device location (GPS), then retry.",
            actionLabel = "Open Location Settings",
            action = PromptAction.LOCATION_SETTINGS,
            secondaryLabel = "Retry",
        )
        LocationError.TIMEOUT, LocationError.UNAVAILABLE -> StateCardSpec(
            title = "Weak GPS signal",
            body = "Couldn't get an accurate fix. Move to an open area and retry. " +
                "If location is set to Approximate, enable Precise location for this app.",
            actionLabel = "Retry",
            action = PromptAction.RETRY,
        )
        LocationError.PERMISSION_DENIED -> StateCardSpec(
            title = "Permission required",
            body = "Grant location permission to continue.",
            actionLabel = "Grant Permission",
            action = PromptAction.GRANT,
        )
        null -> null
    }
}

private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
private val HM = SimpleDateFormat("hh:mm a", Locale.US)
private val HM_SHORT = SimpleDateFormat("HH:mm", Locale.US)

private fun parseIso(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { ISO.parse(iso)!!.time }.getOrNull()
}

private fun fmtHM(ms: Long?): String = ms?.let { HM.format(Date(it)) } ?: "—"
private fun fmtClockOnly(ms: Long): String = HM_SHORT.format(Date(ms))

/** Working duration in-→(out|now); "—" when not clocked in. */
private fun fmtDuration(inMs: Long?, outMs: Long?, now: Long): String {
    if (inMs == null) return "—"
    val end = outMs ?: now
    if (end < inMs) return "—"
    val mins = ((end - inMs) / 60000L).toInt()
    return "${mins / 60}h ${mins % 60}m"
}

private fun clock12(cal: Calendar): String {
    var h = cal.get(Calendar.HOUR)
    if (h == 0) h = 12
    return String.format(Locale.US, "%d:%02d", h, cal.get(Calendar.MINUTE))
}

private fun amPm(cal: Calendar): String = if (cal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"

private fun dateLine(ms: Long): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(ms))

private fun fmtElapsed(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

private fun fmtDistance(m: Double?): String = when {
    m == null -> "—"
    m >= 1000 -> String.format(Locale.US, "%.1f km", m / 1000)
    else -> "${m.roundToInt()} m"
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
