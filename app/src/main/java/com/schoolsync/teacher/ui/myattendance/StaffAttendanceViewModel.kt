package com.schoolsync.teacher.ui.myattendance

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.BuildConfig
import com.schoolsync.teacher.data.location.Campus
import com.schoolsync.teacher.data.location.GeofenceGuide
import com.schoolsync.teacher.data.location.GpsStatus
import com.schoolsync.teacher.data.location.LocationError
import com.schoolsync.teacher.data.location.LocationFix
import com.schoolsync.teacher.data.location.LocationOutcome
import com.schoolsync.teacher.data.location.LocationProvider
import com.schoolsync.teacher.data.location.PlayIntegrityTokenProvider
import com.schoolsync.teacher.data.repository.MyAttendance
import com.schoolsync.teacher.data.repository.PunchResult
import com.schoolsync.teacher.data.repository.StaffAttendanceError
import com.schoolsync.teacher.data.repository.StaffAttendanceRepository
import com.schoolsync.teacher.data.repository.firestore.LeaveFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.SchoolFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * StaffAttendanceViewModel — holds UI state and orchestrates the location
 * layer (GPS fix) and the networking layer (backend punch/me).
 *
 * Responsibilities (Phase 12):
 *   - Expose [StaffAttendanceUiState] for the screen to render.
 *   - On check-in/out: acquire a fix via [LocationProvider], then send it via
 *     [StaffAttendanceRepository]. It does NOT decide Present/Late/accept — the
 *     SERVER is the sole authority; the VM only displays the result.
 *   - Compute a pre-check [GpsStatus] for on-screen GUIDANCE (via the pure
 *     [GeofenceGuide]); never an attendance decision.
 *
 * No attendance business logic and no raw networking live here — those stay in
 * the backend and the repository respectively.
 */
@HiltViewModel
class StaffAttendanceViewModel @Inject constructor(
    private val repo: StaffAttendanceRepository,
    private val locationProvider: LocationProvider,
    private val schoolRepo: SchoolFirestoreRepository,
    private val integrityProvider: PlayIntegrityTokenProvider,
    private val leaveRepo: LeaveFirestoreRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(StaffAttendanceUiState())
    val ui: StateFlow<StaffAttendanceUiState> = _ui.asStateFlow()

    // W6 — end-to-end idempotency: ONE clientPunchId per logical attempt,
    // reused across retries of the SAME direction after a transient failure.
    // Cleared on success or a terminal (server-validation) rejection.
    private var pendingPunchId: String? = null
    private var pendingDirection: String? = null

    /** Load the staff member's own attendance (today + month + history). */
    fun loadMe() {
        // Own approved-leave dates load concurrently — an overlay that guarantees
        // approved (and future-dated) leaves colour the calendar even before/without
        // the server's per-day 'L' stamp. A leave failure must NOT break attendance.
        loadLeaves()
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, loadError = null) }
            repo.me()
                .onSuccess { me -> _ui.update { it.copy(loading = false, me = me, loadError = null) } }
                .onFailure { e ->
                    // Record the load failure so the screen can show an explicit
                    // "unavailable — tap to retry" state. Previously this was
                    // swallowed and the Today card rendered a healthy-looking
                    // "Not marked", hiding a 404 (backend not deployed), a 422
                    // (GPS disabled) or a 5xx until the user tapped Clock-In and
                    // hit an error. `error` (the red action banner) stays reserved
                    // for explicit punch attempts.
                    val se = e as? StaffAttendanceError
                        ?: StaffAttendanceError.Network(e.message ?: "Couldn't load attendance.")
                    _ui.update { it.copy(loading = false, loadError = se) }
                }
        }
    }

    /**
     * Load the teacher's OWN leave applications and expand every APPROVED one
     * into its per-day set (startDate..endDate inclusive) mapped to the leave
     * type label. The screen overlays these onto the calendar so approved and
     * future-dated leaves show as "Leave" independently of the server stamp.
     *
     * getLeaveHistory() already scopes to applicantType=="staff" for this user.
     * Failure is swallowed — the attendance load stays authoritative and the
     * overlay is simply skipped.
     */
    private fun loadLeaves() {
        viewModelScope.launch {
            leaveRepo.getLeaveHistory()
                .onSuccess { docs ->
                    val map = buildApprovedLeaveDates(docs)
                    _ui.update { it.copy(approvedLeaveDates = map) }
                }
                // onFailure: keep any prior overlay; never surface as an error.
        }
    }

    /** date(yyyy-MM-dd) -> leaveType label, for every day of every APPROVED leave. */
    private fun buildApprovedLeaveDates(docs: List<com.schoolsync.teacher.data.model.firestore.LeaveApplicationDoc>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        for (doc in docs) {
            if (!doc.status.equals("approved", ignoreCase = true)) continue
            val start = runCatching { fmt.parse(doc.startDate) }.getOrNull() ?: continue
            val end = runCatching { fmt.parse(doc.endDate.ifBlank { doc.startDate }) }.getOrNull() ?: start
            if (end.before(start)) continue
            val label = doc.leaveType.ifBlank { "Leave" }
            val cal = java.util.Calendar.getInstance().apply { time = start }
            var guard = 0
            while (!cal.time.after(end) && guard < 400) {   // guard against runaway ranges
                out[fmt.format(cal.time)] = label
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                guard++
            }
        }
        return out
    }

    /**
     * Load the campus geofence (centre + radius) for on-screen GUIDANCE only.
     * Read from schools/{id}.attendancePolicy.gps.geofence via the existing
     * SchoolFirestoreRepository raw-map reader — no backend change. The value
     * is NEVER sent to the server (the backend reads its own geofence and
     * remains the sole authority).
     */
    fun loadGeofence() {
        viewModelScope.launch {
            try {
                val cfg = schoolRepo.getSchoolConfig()
                val policy = cfg?.get("attendancePolicy") as? Map<*, *>
                val gps = policy?.get("gps") as? Map<*, *>
                val geo = gps?.get("geofence") as? Map<*, *>
                val active = (geo?.get("active") as? Boolean) ?: false
                val cLat = (geo?.get("centerLat") as? Number)?.toDouble()
                val cLng = (geo?.get("centerLng") as? Number)?.toDouble()
                val rad = (geo?.get("radius") as? Number)?.toInt()

                // Multi-campus: attendancePolicy.gps.geofences[] = [{id,name,centerLat,
                // centerLng,radius,active}]. A punch is valid inside ANY active campus.
                // Fall back to the singular gps.geofence (primary campus) when the array
                // is absent/empty, mirroring the backend's back-compat behaviour.
                val geofencesRaw = gps?.get("geofences") as? List<*>
                val campuses = buildList {
                    geofencesRaw?.forEach { item ->
                        val m = item as? Map<*, *> ?: return@forEach
                        if ((m["active"] as? Boolean) != true) return@forEach
                        val gLat = (m["centerLat"] as? Number)?.toDouble() ?: return@forEach
                        val gLng = (m["centerLng"] as? Number)?.toDouble() ?: return@forEach
                        val gRad = (m["radius"] as? Number)?.toInt() ?: return@forEach
                        if (gRad <= 0) return@forEach
                        add(Campus(gLat, gLng, gRad, m["name"] as? String))
                    }
                    if (isEmpty() && active && cLat != null && cLng != null && rad != null && rad > 0) {
                        add(Campus(cLat, cLng, rad, geo?.get("name") as? String))
                    }
                }

                // Work Schedule for the check-in-time GUIDANCE hint (on-time vs late
                // vs window-closed). Source of truth = shifts.default.schedule; fall
                // back to the legacy windows block, then to the server's own defaults
                // (09:00 start, 0 grace) so the hint matches what the server would do.
                val shifts = policy?.get("shifts") as? Map<*, *>
                val def = shifts?.get("default") as? Map<*, *>
                val sched = def?.get("schedule") as? Map<*, *>
                val win = def?.get("windows") as? Map<*, *>
                val shiftStartStr = (sched?.get("shiftStart") as? String)
                    ?: (win?.get("lateThreshold") as? String)
                val graceMinutes = ((sched?.get("graceMinutes") as? Number)?.toInt())
                    ?: ((win?.get("gracePeriodMin") as? Number)?.toInt()) ?: 0
                val latestStr = (sched?.get("latestCheckIn") as? String)
                    ?: (win?.get("latestCheckIn") as? String)
                val earliestStr = (sched?.get("earliestCheckIn") as? String)
                    ?: (win?.get("earliestCheckIn") as? String)
                val fullH = (sched?.get("fullDayHours") as? Number)?.toDouble()
                // '' or the 23:59 sentinel both mean "no hard cutoff".
                val latestMin = parseHhMmToMin(latestStr)?.takeIf { latestStr != "23:59" }
                // '' or the 00:00 sentinel both mean "no opening gate".
                val earliestMin = parseHhMmToMin(earliestStr)?.takeIf { earliestStr != "00:00" }

                _ui.update {
                    it.copy(
                        geoActive = campuses.isNotEmpty(),
                        campuses = campuses,
                        geoCenterLat = cLat,
                        geoCenterLng = cLng,
                        geoRadius = rad,
                        shiftStartMin = parseHhMmToMin(shiftStartStr) ?: (9 * 60),
                        graceMin = graceMinutes,
                        earliestCheckInMin = earliestMin,
                        latestCheckInMin = latestMin,
                        fullDayHours = fullH,
                        scheduleLoaded = true,
                    )
                }

                // If a fix was already acquired before campuses were known (the
                // screen fires refreshGpsStatus concurrently with loadGeofence),
                // recompute the guidance now so inside/nearest reflect the real
                // fence rather than an "unknown" from the empty-campus snapshot.
                _ui.value.lastFix?.let { fix ->
                    _ui.update { it.copy(gps = GeofenceGuide.evaluateMulti(fix, campuses)) }
                }
            } catch (_: Exception) {
                // Guidance is optional — failure just means no distance/inside hint.
            }
        }
    }

    /** Parse "HH:mm" → minutes-of-day, or null if blank/malformed. */
    private fun parseHhMmToMin(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val parts = s.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * Refresh the pre-check GPS guidance (available / accuracy / distance /
     * inside) using the loaded campus geofence. Distance/inside are omitted
     * when the geofence is unknown. GUIDANCE ONLY — never an attendance decision.
     */
    fun refreshGpsStatus() {
        viewModelScope.launch {
            _ui.update { it.copy(gpsRefreshing = true, gpsError = null) }
            val s = _ui.value
            when (val out = locationProvider.getCurrentFix()) {
                is LocationOutcome.Success ->
                    _ui.update {
                        it.copy(
                            gpsRefreshing = false,
                            lastFix = out.fix,
                            gps = GeofenceGuide.evaluateMulti(out.fix, s.campuses),
                        )
                    }
                is LocationOutcome.Failure ->
                    _ui.update {
                        it.copy(
                            gpsRefreshing = false,
                            gpsError = out.error,
                            gps = GpsStatus(available = false, accuracyMeters = null, distanceMeters = null, insideGeofence = null),
                        )
                    }
            }
        }
    }

    fun checkIn() = punch("in")

    fun checkOut() = punch("out")

    private fun punch(direction: String) {
        viewModelScope.launch {
            _ui.update { it.copy(punching = true, error = null, lastResult = null, gpsError = null) }
            val s = _ui.value
            when (val out = locationProvider.getCurrentFix()) {
                is LocationOutcome.Failure ->
                    _ui.update { it.copy(punching = false, gpsError = out.error) }
                is LocationOutcome.Success -> {
                    val fix = out.fix
                    _ui.update { it.copy(gps = GeofenceGuide.evaluateMulti(fix, s.campuses), lastFix = fix) }

                    // W6: reuse the pending id when retrying the SAME direction
                    // after a transient failure; otherwise mint a fresh one.
                    val clientPunchId = if (pendingDirection == direction && pendingPunchId != null) {
                        pendingPunchId!!
                    } else {
                        UUID.randomUUID().toString().also { pendingPunchId = it; pendingDirection = direction }
                    }

                    // Play Integrity attestation (inert until configured — see
                    // PlayIntegrityTokenProvider). Bound to this attempt via the
                    // clientPunchId nonce. Best-effort: null when disabled/failed.
                    val integrityToken = integrityProvider.tokenOrNull(clientPunchId)

                    repo.punch(
                        direction = direction,
                        lat = fix.latitude,
                        lng = fix.longitude,
                        accuracy = fix.accuracyMeters,
                        mock = fix.isMock,
                        clientPunchId = clientPunchId,
                        clientCapturedAt = isoTimestamp(fix.capturedAtEpochMs),
                        device = deviceInfo(),
                        integrityToken = integrityToken,
                    )
                        .onSuccess { r ->
                            pendingPunchId = null; pendingDirection = null   // logical attempt done
                            _ui.update { it.copy(punching = false, lastResult = r) }
                            loadMe()   // refresh today/month after a state-changing punch
                        }
                        .onFailure { e ->
                            val err = e.asStaffError()
                            // Transient (network/timeout): KEEP the id so a retry is
                            // idempotent server-side. Terminal: clear → next attempt is new.
                            val retryable = err is StaffAttendanceError.Network || err is StaffAttendanceError.Timeout
                            if (!retryable) { pendingPunchId = null; pendingDirection = null }
                            _ui.update { it.copy(punching = false, error = err) }
                        }
                }
            }
        }
    }

    /** Clear transient result/error after the UI has shown it. */
    fun consumeResult() { _ui.update { it.copy(lastResult = null, error = null, gpsError = null) } }

    private fun Throwable.asStaffError(): StaffAttendanceError =
        this as? StaffAttendanceError ?: StaffAttendanceError.Network(message ?: "Unexpected error")

    private fun deviceInfo(): Map<String, String> = mapOf(
        "model" to Build.MODEL,
        "manufacturer" to Build.MANUFACTURER,
        "os" to "Android",
        "osVersion" to Build.VERSION.RELEASE,
        "sdkInt" to Build.VERSION.SDK_INT.toString(),
        "appVersion" to BuildConfig.VERSION_NAME,
    )

    private fun isoTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(epochMs))
}

/** UI state for the My Attendance screen (Phase 13 renders this). */
data class StaffAttendanceUiState(
    val loading: Boolean = false,
    val punching: Boolean = false,
    val gpsRefreshing: Boolean = false,
    val gps: GpsStatus? = null,
    val lastFix: LocationFix? = null,
    val gpsError: LocationError? = null,
    val me: MyAttendance? = null,
    val lastResult: PunchResult? = null,
    val error: StaffAttendanceError? = null,
    /** A me() LOAD failure (backend not deployed / GPS disabled / server / offline),
     *  distinct from the punch `error`. When set (and `me` is null) the screen shows
     *  an "unavailable — tap to retry" state instead of a misleading "Not marked". */
    val loadError: StaffAttendanceError? = null,
    // Campus geofence (guidance only; never sent to the server)
    val geoActive: Boolean = false,
    /** All active campuses (multi-campus). A punch is valid inside ANY of them.
     *  Empty = no fence configured → the server decides; the client won't hard-block. */
    val campuses: List<Campus> = emptyList(),
    val geoCenterLat: Double? = null,
    val geoCenterLng: Double? = null,
    val geoRadius: Int? = null,
    // Work Schedule (guidance only for the check-in hint; server is authoritative)
    val shiftStartMin: Int? = null,       // minutes-of-day, e.g. 540 = 09:00
    val graceMin: Int = 0,
    val earliestCheckInMin: Int? = null,  // optional opening gate; null = none
    val latestCheckInMin: Int? = null,    // optional hard cutoff; null = none
    val fullDayHours: Double? = null,
    val scheduleLoaded: Boolean = false,
    /** date(yyyy-MM-dd) -> leaveType label for the teacher's own APPROVED leaves.
     *  Overlaid onto the calendar so approved/future leave shows as "Leave" even
     *  before the server stamps the per-day 'L'. Empty when unloaded/failed. */
    val approvedLeaveDates: Map<String, String> = emptyMap(),
)
