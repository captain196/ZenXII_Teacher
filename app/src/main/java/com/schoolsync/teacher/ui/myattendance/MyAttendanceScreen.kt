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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

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

    // Precise (FINE) is required for a geofence-accurate punch. On Android 12+ the
    // user can grant Approximate (COARSE) only — hasPerm() is then true, the app
    // punches, and only the SERVER rejects it (poor_accuracy). Detect coarse-only
    // up front and prompt to upgrade instead of letting them hit a reject loop.
    fun hasPrecise(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var permGranted by remember { mutableStateOf(hasPerm()) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var coarseOnly by remember { mutableStateOf(hasPerm() && !hasPrecise()) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        permGranted = granted
        coarseOnly = granted && !hasPrecise()
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

    // Auto-dismiss the punch success/error banner. Previously consumeResult()
    // was never called, so a "Clocked in" / error line lingered indefinitely
    // and stayed visible across home↔calendar navigation.
    LaunchedEffect(ui.lastResult, ui.error) {
        if (ui.lastResult != null || ui.error != null) {
            kotlinx.coroutines.delay(4000)
            vm.consumeResult()
        }
    }

    // Re-check on resume so returning from Settings auto-refreshes the guidance.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permGranted = hasPerm()
                coarseOnly = permGranted && !hasPrecise()
                if (permGranted) vm.refreshGpsStatus()
                // Re-read attendance on resume so an admin's regularization approval
                // (which flips e.g. Absent→Present in staffAttendance) shows on the
                // Today card + calendar without a full app restart.
                vm.loadMe()
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
                leaveDates = ui.approvedLeaveDates,
                refreshing = ui.loading,
                onRefresh = { vm.loadMe() },
                onBack = { showCalendar = false },
            )
        } else {
            HomeView(
                ui = ui,
                vm = vm,
                permGranted = permGranted,
                permanentlyDenied = permanentlyDenied,
                coarseOnly = coarseOnly,
                onEnablePrecise = { permLauncher.launch(LocationPermissions.REQUIRED) },
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
    coarseOnly: Boolean,
    onEnablePrecise: () -> Unit,
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
    // First cold load (no data yet) — drives the shimmer skeletons instead of
    // rendering a guessed "Clock in" / "V" state that then flips once me() lands.
    val firstLoading = ui.me == null && ui.loading
    val checkInMs = parseIso(today?.checkInAt)
    val checkOutMs = parseIso(today?.checkOutAt)
    var showGpsDetail by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(24.dp)
    val done = checkInMs != null && checkOutMs != null
    val isRestDay = today?.isWeeklyOff == true || today?.isHoliday == true
    val restLabel = if (today?.isHoliday == true) "holiday" else "weekly-off"

    // FIX 2 — location gating for Clock-In (UX only; the server stays authoritative).
    // Enabled only when a real fix is available AND (inside a campus OR no fence is
    // configured, in which case the server decides). Never hard-blocks on schedule.
    val campuses = ui.campuses
    val fixReady = ui.gps?.available == true && !ui.gpsRefreshing
    val insideOk = ui.gps?.insideGeofence == true || campuses.isEmpty()
    val locationEligible = fixReady && insideOk

    // FIX 3 — an admin panel mark sets today.status (P/T/M/A/L/H/O/W) with NO punch
    // timestamps. Treat that as already-recorded so we don't show a bare Clock-In.
    // A rest day the school allows working on is the exception: the extra-work
    // confirm flow may still offer the action.
    val restDayWorkAllowed = isRestDay && today?.allowWorkOnOff == true
    val statusU = (today?.status ?: "V").uppercase()
    val alreadyMarked = statusU in setOf("P", "T", "M", "A", "L", "H", "O", "W") &&
        checkInMs == null && !restDayWorkAllowed
    var showOffConfirm by remember { mutableStateOf(false) }
    // On a rest day before any punch, today.status is still "V"; surface the
    // rest-day nature in the pill so the employee sees Weekly-off / Holiday.
    val pillStatus = when {
        (today?.status ?: "V").uppercase() in setOf("P", "T", "M", "A", "L", "H", "O", "W") -> today!!.status
        today?.isHoliday == true -> "H"
        today?.isWeeklyOff == true -> "O"
        else -> "V"
    }

    // On-leave detection: TODAY is on leave when the server already marked it 'L'
    // OR today falls inside one of the teacher's own APPROVED leave ranges (which
    // also covers the not-yet-stamped case). todayKey prefers the server's date,
    // falling back to the device date so the banner still works if me() failed.
    val deviceTodayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayKey = today?.date?.takeIf { it.isNotBlank() } ?: deviceTodayKey
    val onLeaveType = ui.approvedLeaveDates[todayKey]
    val onLeaveToday = today?.status?.uppercase() == "L" || onLeaveType != null
    val leaveUntilLabel = remember(todayKey, ui.approvedLeaveDates) {
        leaveUntilLabel(todayKey, ui.approvedLeaveDates)
    }

    if (showOffConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOffConfirm = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showOffConfirm = false; vm.checkIn() }) { Text(stringResource(R.string.myatt_clock_in_anyway)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOffConfirm = false }) { Text(stringResource(R.string.common_not_now)) }
            },
            title = { Text(stringResource(R.string.myatt_today_is, restLabel)) },
            text = { Text(stringResource(R.string.myatt_extra_work_warn)) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Location chip — stays at the top of the card, horizontally centered.
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
                    Text(stringResource(R.string.myatt_accuracy, (ui.gps?.accuracyMeters?.roundToInt() ?: "—").toString()), fontSize = 12.5.sp, color = c.textSecondary)
                    Text("•", color = c.divider)
                    Text(stringResource(R.string.myatt_from_school, fmtDistance(ui.gps?.distanceMeters)), fontSize = 12.5.sp, color = c.textSecondary)
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
        // Body scrolls: when a punch message / GPS prompt / error appears the
        // content can exceed the card height, so it must scroll instead of
        // clipping the action + calendar link off the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cardShape)
                .background(c.surfaceCard)
                .border(BorderStroke(1.dp, c.divider), cardShape)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
        ) {
            // Prominent on-leave banner — shows the moment an approved leave covers
            // today, independent of whether the server has stamped today's 'L' yet.
            if (onLeaveToday) {
                OnLeaveBanner(leaveType = onLeaveType, untilLabel = leaveUntilLabel)
                Spacer(Modifier.height(16.dp))
            }
            // The title takes the remaining width and the status pill keeps its
            // intrinsic size. Under SpaceBetween with both unconstrained, a long
            // translated pill ("குறிக்கப்படவில்லை") overlapped the heading —
            // "Not marked" is short enough in English to hide it. Caught on device.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.myatt_today_title),
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary
                )
                // Don't show a guessed status until today's data has loaded —
                // otherwise the pill flashes "V" before settling on the real state.
                when {
                    ui.me != null -> TodayStatusPill(pillStatus)
                    firstLoading -> Box(Modifier.width(72.dp).height(28.dp).shimmer(RoundedCornerShape(14.dp)))
                }
            }
            Spacer(Modifier.height(20.dp))
            InfoRow(stringResource(R.string.myatt_check_in), fmtHM(checkInMs), loading = firstLoading)
            Spacer(Modifier.height(12.dp))
            InfoRow(stringResource(R.string.myatt_check_out), fmtHM(checkOutMs), loading = firstLoading)
            Spacer(Modifier.height(12.dp))
            InfoRow(stringResource(R.string.myatt_working_duration), fmtDuration(checkInMs, checkOutMs, now), loading = firstLoading)

            // Separator before the notices + action. (A weight(1f) spacer can't
            // be used here — the card body is now vertically scrollable, where a
            // weighted child collapses to zero height.)
            Spacer(Modifier.height(24.dp))

            ui.lastResult?.let { r ->
                if (r.message.isNotBlank()) {
                    InfoLine(text = r.message, color = c.success, surface = c.successSurface, icon = Icons.Filled.CheckCircle)
                    Spacer(Modifier.height(12.dp))
                }
            }
            val stateCard = resolveStateCard(permGranted, permanentlyDenied, ui.gpsError)
            stateCard?.let { sc ->
                StatePrompt(
                    title = stringResource(sc.title),
                    body = stringResource(sc.body),
                    actionLabel = stringResource(sc.actionLabel),
                    onAction = when (sc.action) {
                        PromptAction.GRANT -> onRequestPermission
                        PromptAction.APP_SETTINGS -> onOpenAppSettings
                        PromptAction.LOCATION_SETTINGS -> onOpenLocationSettings
                        PromptAction.RETRY -> ({ vm.refreshGpsStatus() })
                    },
                    secondaryLabel = sc.secondaryLabel?.let { stringResource(it) },
                    onSecondary = { vm.refreshGpsStatus() },
                )
                Spacer(Modifier.height(12.dp))
            }
            ui.error?.let { e ->
                InfoLine(text = e.message ?: stringResource(R.string.common_something_wrong), color = c.error, surface = c.errorSurface, icon = Icons.Filled.WarningAmber)
                Spacer(Modifier.height(12.dp))
            }
            // Coarse-only location: warn + offer to upgrade BEFORE a punch, instead
            // of letting the server reject it for poor accuracy.
            if (permGranted && coarseOnly) {
                InfoLine(
                    text = stringResource(R.string.myatt_approx_location),
                    color = c.warning, surface = c.warningSurface, icon = Icons.Filled.WarningAmber,
                )
                Spacer(Modifier.height(8.dp))
                PillButton(label = stringResource(R.string.myatt_enable_precise), bg = c.warningSurface, content = c.warning) {
                    onEnablePrecise()
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                // Load failed and we have no data: show an explicit unavailable
                // state + Retry rather than a misleading "Clock in" button. This
                // is what a 404 (backend not deployed), 422 (GPS disabled) or 5xx
                // now surfaces as, instead of a healthy-looking stringResource(R.string.myatt_not_marked).
                ui.me == null && ui.loadError != null -> {
                    InfoLine(
                        text = ui.loadError?.message?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.vm_myatt_load_failed),
                        color = c.error, surface = c.errorSurface, icon = Icons.Filled.WarningAmber,
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton(label = stringResource(R.string.common_retry), bg = c.errorSurface, content = c.error) { vm.loadMe() }
                }
                // First load not finished yet: show a shimmer skeleton instead of
                // guessing the Clock-in state — prevents the "Clock in" button
                // flashing for an already-clocked-in user before me() resolves.
                firstLoading -> AttendanceActionSkeleton()
                done -> InfoLine(
                    text = stringResource(R.string.myatt_done_times, fmtHM(checkInMs), fmtHM(checkOutMs)),
                    color = c.success, surface = c.successSurface, icon = Icons.Filled.CheckCircle,
                )
                // A real check-in punch exists (no checkout yet) → let them clock out.
                checkInMs != null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RunningCard(sinceMs = checkInMs, now = now)
                    ClockOutButton(
                        busy = ui.punching,
                        onClick = { if (!permGranted) onRequestPermission() else vm.checkOut() },
                    )
                }
                // FIX 3 — admin/status-only mark (status set, no punch) → already
                // recorded; no bare Clock-In. The on-leave banner above covers 'L'.
                alreadyMarked -> {
                    if (!onLeaveToday) InfoLine(
                        text = stringResource(R.string.myatt_recorded_status, humanStatus(LocalContext.current, statusU)),
                        color = c.info, surface = c.infoSurface, icon = Icons.Filled.CheckCircle,
                    )
                }
                else -> {
                    // Normal Clock-In flow (checkInMs == null, not admin-marked).
                    // Check-in-time GUIDANCE (mirror of the server window): show
                    // on-time / late / window-closed before the tap; the server is
                    // still the authority. Skipped on a rest day (handled by dialog).
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
                    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                    val hint = staffCheckInHint(ui.shiftStartMin, ui.graceMin, ui.earliestCheckInMin, ui.latestCheckInMin, nowMin)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isRestDay && ui.scheduleLoaded) {
                            val fullTxt = ui.fullDayHours?.let { h ->
                                val hs = if (h % 1.0 == 0.0) h.toInt().toString() else h.toString()
                                stringResource(R.string.myatt_full_day_suffix, hs)
                            } ?: ""
                            when (hint.window) {
                                CheckInWindow.BEFORE_OPEN -> InfoLine(
                                    text = stringResource(R.string.myatt_opens_at, hint.opensAt.orEmpty()),
                                    color = c.textSecondary, surface = c.surfaceCard, icon = Icons.Filled.WarningAmber,
                                )
                                CheckInWindow.ON_TIME -> InfoLine(
                                    text = stringResource(R.string.myatt_ontime_by, hint.onTimeBy.orEmpty(), fullTxt),
                                    color = c.success, surface = c.successSurface, icon = Icons.Filled.CheckCircle,
                                )
                                CheckInWindow.LATE -> InfoLine(
                                    text = stringResource(R.string.myatt_marked_late, hint.lateMinutes, fullTxt),
                                    color = c.warning, surface = c.warningSurface, icon = Icons.Filled.WarningAmber,
                                )
                                CheckInWindow.CLOSED -> InfoLine(
                                    text = stringResource(R.string.myatt_closed_after, hint.closedAt.orEmpty()),
                                    color = c.error, surface = c.errorSurface, icon = Icons.Filled.WarningAmber,
                                )
                                CheckInWindow.UNKNOWN -> Unit
                            }
                        }
                        // FIX 2 — location guidance shown before the tap. Advisory copy;
                        // the server still validates the coordinates it receives.
                        if (permGranted && !locationEligible) {
                            when {
                                ui.gpsRefreshing || ui.gps == null -> InfoLine(
                                    text = stringResource(R.string.myatt_getting_location),
                                    color = c.textSecondary, surface = c.surfaceCard, icon = Icons.Filled.Refresh,
                                )
                                ui.gps?.available == true && ui.gps?.insideGeofence == false && campuses.isNotEmpty() -> InfoLine(
                                    text = stringResource(R.string.myatt_outside_geofence,
                                            fmtDistance(ui.gps?.distanceMeters),
                                            ui.gps?.nearestCampusName ?: stringResource(R.string.myatt_campus_fallback)),
                                    color = c.warning, surface = c.warningSurface, icon = Icons.Filled.WarningAmber,
                                )
                                else -> Unit   // no fix / GPS error → StatePrompt handles it
                            }
                        }
                        ClockInButton(
                            busy = ui.punching,
                            // FIX 2: gate on a real location fix + geofence. Enabled when
                            // permission is missing (so a tap can request it) OR location
                            // is eligible (fix acquired AND inside a campus, or no fence is
                            // configured). The schedule hint stays ADVISORY — a wrong device
                            // clock or stale config must not hard-lock a legit punch; the
                            // server is the sole authority on acceptance.
                            enabled = !permGranted || locationEligible,
                            onClick = {
                                when {
                                    !permGranted -> onRequestPermission()
                                    isRestDay && today?.allowWorkOnOff == true -> showOffConfirm = true
                                    else -> vm.checkIn()
                                }
                            },
                        )
                    }
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
                Text(stringResource(R.string.myatt_calendar), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
        }
    }
}

/* ── Today panel pieces ─────────────────────────────────────────── */

@Composable
private fun InfoRow(label: String, value: String, loading: Boolean = false) {
    val c = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.5.sp, color = c.textSecondary)
        if (loading) Box(Modifier.width(70.dp).height(13.dp).shimmer())
        else Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
    }
}

/* ── Shimmer / skeleton loading ─────────────────────────────────── */

/**
 * Animated shimmer fill for skeleton placeholders — a highlight band that
 * sweeps horizontally across the shape. Reusable across the attendance module.
 */
@Composable
private fun Modifier.shimmer(shape: Shape = RoundedCornerShape(6.dp)): Modifier {
    val c = LocalAppColors.current
    val base = c.textTertiary.copy(alpha = 0.16f)
    val highlight = c.textTertiary.copy(alpha = 0.38f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    return this
        .clip(shape)
        .drawBehind {
            val w = size.width
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(translate * w, 0f),
                    end = Offset(translate * w + w, 0f),
                )
            )
        }
}

/** Skeleton for the whole action area shown during the very first data load. */
@Composable
private fun AttendanceActionSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(64.dp).shimmer(RoundedCornerShape(18.dp)))
    }
}

/**
 * stringResource(R.string.myatt_on_leave) banner shown at the top of the Today card when today is
 * covered by an approved leave (or the server stamped 'L'). Uses the same
 * info-blue token as the Leave calendar band/pill so the colour reads coherently.
 */
@Composable
private fun OnLeaveBanner(leaveType: String?, untilLabel: String?) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.infoSurface)
            .border(BorderStroke(1.dp, c.info.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.info.copy(alpha = 0.18f)),
        ) {
            Icon(Icons.Filled.EventBusy, contentDescription = null, tint = c.info, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (untilLabel != null) stringResource(R.string.myatt_on_leave) else stringResource(R.string.myatt_on_leave_today),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.info,
            )
            val sub = buildString {
                if (!leaveType.isNullOrBlank()) append(leaveType)
                if (untilLabel != null) {
                    if (isNotEmpty()) append(" · ")
                    append(stringResource(R.string.myatt_until, untilLabel))
                }
            }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 12.5.sp, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun TodayStatusPill(status: String) {
    val c = LocalAppColors.current
    // The code -> colour mapping stays here; the code -> LABEL mapping is
    // humanStatus(), which already had every localized key. Duplicating it as
    // English literals is what shipped "Present / Late / Absent" untranslated.
    val ctx = LocalContext.current
    val color = when (status.uppercase()) {
        "P" -> c.attPresent
        "T" -> c.attTardy
        "M" -> c.attLeave
        "A" -> c.attAbsent
        "L" -> c.info
        "H" -> c.attHoliday
        "O" -> c.textTertiary
        "W" -> c.attVacation
        else -> c.textTertiary
    }
    val label = when (status.uppercase()) {
        "W"  -> stringResource(R.string.myatt_extra_day)
        in setOf("P", "T", "M", "A", "L", "H", "O") -> humanStatus(ctx, status)  // i18n-ignore: wire codes
        else -> stringResource(R.string.myatt_not_marked)
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
        available && inside == true -> Triple(c.success, stringResource(R.string.myatt_on_campus), c.success)
        available && inside == false -> Triple(c.warning, "${fmtDistance(distance)} away", c.warning)
        available -> Triple(c.success, "Located", c.success)
        else -> Triple(c.textTertiary, if (locating) stringResource(R.string.myatt_locating) else stringResource(R.string.myatt_location_off), c.textSecondary)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            // TCH-M4: guarantee a 48dp touch target without changing the visual size.
            .minimumInteractiveComponentSize()
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
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ClockInButton(busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalAppColors.current
    BigButton(
        bg = c.accent,
        content = c.textOnAccent,
        onClick = onClick,
        enabled = enabled && !busy,
    ) {
        BigIconBox(tint = c.textOnAccent) { Icon(Icons.Filled.HowToReg, contentDescription = null, tint = c.textOnAccent, modifier = Modifier.size(24.dp)) }
        Text(if (busy) stringResource(R.string.myatt_clocking_in) else stringResource(R.string.myatt_clock_in), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textOnAccent)
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
        Text(if (busy) stringResource(R.string.myatt_clocking_out) else stringResource(R.string.myatt_clock_out), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            Text(stringResource(R.string.myatt_working_since_fmt, fmtHM(sinceMs)), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = c.textOnAccent.copy(alpha = 0.85f))
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
            Text(stringResource(R.string.myatt_all_done), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
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
    leaveDates: Map<String, String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalAppColors.current

    val nowCal = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(nowCal.get(Calendar.MONTH)) } // 0-based

    // date -> status map from the 30-day history + today's live status, then the
    // approved-leave overlay. The overlay only paints days whose real mark is
    // still vacant/absent-not-yet (null/blank/V/A) — it never overrides a stronger
    // mark (P/T/M/H/O/W). This guarantees approved and FUTURE-dated leaves render
    // as "Leave" on the calendar even before the server stamps the per-day 'L'.
    val statusByDate = remember(history, today, leaveDates) {
        buildMap<String, String> {
            history.forEach { put(it.date, it.status) }
            today?.let { if (it.date.isNotBlank()) put(it.date, it.status) }
            leaveDates.keys.forEach { date ->
                val cur = get(date)?.uppercase()
                if (cur.isNullOrBlank() || cur == "V" || cur == "A") put(date, "L")
            }
        }
    }
    // Per-day check-in/out times (from me().history + today) so any day can
    // show when the employee clocked in/out, not just today.
    val timesByDate = remember(history, today) {
        buildMap<String, Pair<String?, String?>> {
            history.forEach { put(it.date, it.checkInAt to it.checkOutAt) }
            today?.let { if (it.date.isNotBlank()) put(it.date, it.checkInAt to it.checkOutAt) }
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
            // Use the SERVER's rest-day markings (O = weekly-off, H = holiday) rather
            // than a hardcoded Sat/Sun weekend — the school's real weekly-off may be
            // Friday, or Saturday may be a working day. Only past Absent/unmarked
            // WORKING days are regularizable.
            when (statusByDate[key]?.uppercase()) {
                "O", "H" -> {}            // server-marked rest day — not regularizable
                "A", "V" -> out.add(key)
            }
        }
        out
    }

    fun shift(delta: Int) {
        // me().history is only ~30 days, so only the current month and the
        // immediately-previous month have any data. Bounding nav to that window
        // stops the user paging into fully-blank months that read as "no
        // attendance recorded". (No future months either.)
        val nowAbs = nowCal.get(Calendar.YEAR) * 12 + nowCal.get(Calendar.MONTH)
        val targetAbs = (year * 12 + month) + delta
        if (targetAbs > nowAbs || targetAbs < nowAbs - 1) return
        month = ((targetAbs % 12) + 12) % 12
        year = Math.floorDiv(targetAbs, 12)
    }

    // Regularize is a FULL SCREEN now (not a bottom sheet). When open, it REPLACES
    // the calendar so that Back / keyboard-dismiss can't nuke the typed reason.
    if (showRegularize) {
        RegularizeScreen(
            candidateDates = regularizeCandidates,
            onDismiss = { showRegularize = false },
        )
        return
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onBack)
                // Re-read attendance so a fresh admin approval (Absent→Present) shows
                // without leaving the screen. Spins while me() reloads.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()   // TCH-M4: 48dp touch target
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.surfaceCard)
                        .border(BorderStroke(1.dp, c.divider), RoundedCornerShape(13.dp))
                        .clickable(enabled = !refreshing, onClick = onRefresh),
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = c.textPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SquareIconButton(Icons.Filled.ChevronLeft, stringResource(R.string.att_prev_month), { shift(-1) }, small = true)
                Text(
                    "${monthLabels()[month]} $year",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 150.dp),
                )
                SquareIconButton(Icons.Filled.ChevronRight, stringResource(R.string.att_next_month), { shift(1) }, small = true)
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
                    Text(stringResource(R.string.common_today), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accent)
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
                Text(stringResource(R.string.myatt_regularize), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent)
                Text(stringResource(R.string.myatt_regularize_sub), fontSize = 11.5.sp, color = c.textSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Weekday header (Mon-first)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            weekdayLabels().forEach { w ->
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
                            Spacer(Modifier.fillMaxWidth().aspectRatio(0.78f))
                        } else {
                            val key = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                            val dowCal = Calendar.getInstance().apply { clear(); set(year, month, day) }
                            val weekend = dowCal.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
                            val dayTimes = timesByDate[key]
                            DayCell(
                                day = day,
                                status = statusByDate[key],
                                weekend = weekend,
                                isToday = isThisMonth && day == todayDay,
                                checkInAt = dayTimes?.first,
                                checkOutAt = dayTimes?.second,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
      }
    }

}

@Composable
private fun DayCell(
    day: Int,
    status: String?,
    weekend: Boolean,
    isToday: Boolean,
    checkInAt: String?,
    checkOutAt: String?,
) {
    val c = LocalAppColors.current
    val band = statusBand(status, weekend)
    // The whole cell is now tinted with the status colour (same hue as the legend
    // swatch / pill) instead of showing a P/A letter — colour alone carries the
    // status, which reads faster across a month grid and frees the cell for times.
    val fill = band?.color?.copy(alpha = 0.15f) ?: c.surfaceCard
    // TCH-M3: status is conveyed by colour only, which TalkBack can't announce.
    // Build a merged contentDescription so the day, its status and its in/out
    // times are read aloud as one node.
    val cellDesc = run {
        val statusLabel = band?.label ?: if (weekend) "Weekly-off" else stringResource(R.string.myatt_not_marked)
        val ci = parseIso(checkInAt)?.let { fmtClockOnly(it) }
        val co = parseIso(checkOutAt)?.let { fmtClockOnly(it) }
        buildString {
            append("$day, $statusLabel")
            if (ci != null) append(", in $ci")
            if (co != null) append(stringResource(R.string.myatt_out_at, co))
        }
    }
    val edge = when {
        isToday   -> c.accent
        band != null -> band.color.copy(alpha = 0.45f)
        else      -> c.divider
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .semantics(mergeDescendants = true) { contentDescription = cellDesc }
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(
                BorderStroke(if (isToday) 2.dp else 1.dp, edge),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Day number, tinted the status colour so it stays legible on the fill and
        // reinforces the code without a separate dot/letter.
        Text(
            "$day",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = band?.color ?: c.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        // Bottom: in-time (green) over out-time (grey) — position + colour carry the
        // meaning, so the short 24h times fit without arrows/labels and never clip.
        // Approved regularizations feed their claimed times here too (via me()).
        val tin = parseIso(checkInAt)
        val tout = parseIso(checkOutAt)
        if (tin != null) {
            Text(fmtClockOnly(tin), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.success, maxLines = 1)
            Text(if (tout != null) fmtClockOnly(tout) else "·  ·", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun CalendarLegend() {
    val c = LocalAppColors.current
    val items = listOf(
        stringResource(R.string.attendance_status_present) to c.attPresent,
        stringResource(R.string.attendance_status_late) to c.attTardy,
        stringResource(R.string.myatt_status_halfday) to c.attLeave,
        stringResource(R.string.attendance_status_absent) to c.attAbsent,
        stringResource(R.string.myatt_on_leave_short) to c.info,
        stringResource(R.string.attendance_status_holiday) to c.attHoliday,
        stringResource(R.string.myatt_extra_work) to c.attVacation,
    )
    // FlowRow, not Row: seven labels fit one line as English words ("Present",
    // "Late", "Half"), but each is 2-3x longer in every Indic script and the
    // last one wrapped INSIDE its own cell and clipped. Wrapping to a second
    // line is the correct behaviour; `maxLines = 1` on each label keeps a
    // single item from breaking mid-word.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.35f)))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textSecondary,
                     maxLines = 1)
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
            // TCH-M4: keep the visual chip small but expand the touch target to 48dp.
            .minimumInteractiveComponentSize()
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
// @StringRes throughout, not String: resolveStateCard() is a plain function,
// so a String here would have to be resolved by its caller anyway — and holding
// one would freeze the launch language. Resolved at the render site below.
private data class StateCardSpec(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val actionLabel: Int,
    val action: PromptAction,
    @StringRes val secondaryLabel: Int? = null,
)

private fun resolveStateCard(
    permGranted: Boolean,
    permanentlyDenied: Boolean,
    gpsError: LocationError?,
): StateCardSpec? {
    if (!permGranted) {
        return if (permanentlyDenied) StateCardSpec(
            title = R.string.myatt_perm_blocked,
            body = R.string.myatt_perm_blocked_body,
            actionLabel = R.string.myatt_open_settings,
            action = PromptAction.APP_SETTINGS,
        ) else StateCardSpec(
            title = R.string.myatt_perm_needed,
            body = R.string.myatt_perm_needed_body,
            actionLabel = R.string.myatt_grant_permission,
            action = PromptAction.GRANT,
        )
    }
    return when (gpsError) {
        LocationError.LOCATION_SERVICES_OFF -> StateCardSpec(
            title = R.string.myatt_location_off,
            body = R.string.myatt_location_off_body,
            actionLabel = R.string.myatt_open_location_settings,
            action = PromptAction.LOCATION_SETTINGS,
            secondaryLabel = R.string.common_retry,
        )
        LocationError.TIMEOUT, LocationError.UNAVAILABLE -> StateCardSpec(
            title = R.string.myatt_weak_gps,
            body = R.string.myatt_weak_gps_body,
            actionLabel = R.string.common_retry,
            action = PromptAction.RETRY,
        )
        LocationError.PERMISSION_DENIED -> StateCardSpec(
            title = R.string.myatt_perm_required,
            body = R.string.myatt_perm_required_body,
            actionLabel = R.string.myatt_grant_permission,
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

private val DATE_KEY = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/**
 * If today starts a multi-day approved-leave run, return the last consecutive
 * on-leave date formatted as "d MMM" (e.g. "12 Jul"); null for a single day.
 * Walks forward from [todayKey] while consecutive dates exist in [leaveDates].
 */
private fun leaveUntilLabel(todayKey: String, leaveDates: Map<String, String>): String? {
    if (!leaveDates.containsKey(todayKey)) return null
    val start = runCatching { DATE_KEY.parse(todayKey) }.getOrNull() ?: return null
    val cal = Calendar.getInstance().apply { time = start }
    var last = todayKey
    var guard = 0
    while (guard < 400) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val k = DATE_KEY.format(cal.time)
        if (leaveDates.containsKey(k)) { last = k; guard++ } else break
    }
    if (last == todayKey) return null
    return runCatching {
        SimpleDateFormat("d MMM", Locale.getDefault()).format(DATE_KEY.parse(last)!!)
    }.getOrNull()
}

/** Map a server status letter → human words for the "already recorded" line. */
private fun humanStatus(ctx: android.content.Context, status: String): String = when (status.uppercase()) {
    "P" -> ctx.localizedString(R.string.attendance_status_present)
    "T" -> ctx.localizedString(R.string.attendance_status_late)
    "M" -> ctx.localizedString(R.string.myatt_status_halfday)
    "A" -> ctx.localizedString(R.string.attendance_status_absent)
    "L" -> ctx.localizedString(R.string.myatt_on_leave_short)
    "H" -> ctx.localizedString(R.string.attendance_status_holiday)
    "O" -> ctx.localizedString(R.string.myatt_status_weeklyoff)
    "W" -> ctx.localizedString(R.string.myatt_extra_work)
    else -> ctx.localizedString(R.string.myatt_status_recorded)
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
    SimpleDateFormat("EEEE, d MMMM"  /* i18n-ignore: date pattern */, Locale.getDefault()).format(Date(ms))

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

// Weekday and month names come from the platform, not a hardcoded list.
// A top-level `val` is initialised once at class-load, so it would freeze in
// whatever language the process started in and survive the recreate() that a
// language change triggers — the same trap that bit the notification channels.
// DateFormatSymbols reads Locale.getDefault(), which LocaleManager.wrap() sets,
// so these follow the app language for free and need no strings.xml entries.
private fun weekdayLabels(): List<String> {
    // shortWeekdays is 1-indexed with Calendar.SUNDAY == 1; the grid is Mon-first.
    val s = java.text.DateFormatSymbols.getInstance().shortWeekdays
    return listOf(
        java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY,
        java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY,
        java.util.Calendar.SUNDAY,
    ).map { s[it] }
}

private fun monthLabels(): List<String> =
    java.text.DateFormatSymbols.getInstance().months.take(12)
