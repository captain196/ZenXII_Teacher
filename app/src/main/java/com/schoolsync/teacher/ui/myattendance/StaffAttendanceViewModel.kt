package com.schoolsync.teacher.ui.myattendance

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.BuildConfig
import com.schoolsync.teacher.data.location.GeofenceGuide
import com.schoolsync.teacher.data.location.GpsStatus
import com.schoolsync.teacher.data.location.LocationError
import com.schoolsync.teacher.data.location.LocationFix
import com.schoolsync.teacher.data.location.LocationOutcome
import com.schoolsync.teacher.data.location.LocationProvider
import com.schoolsync.teacher.data.repository.MyAttendance
import com.schoolsync.teacher.data.repository.PunchResult
import com.schoolsync.teacher.data.repository.StaffAttendanceError
import com.schoolsync.teacher.data.repository.StaffAttendanceRepository
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
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repo.me()
                .onSuccess { me -> _ui.update { it.copy(loading = false, me = me) } }
                .onFailure {
                    // A LOAD failure (e.g. backend not yet deployed) must NOT surface as
                    // the red action banner — that is reserved for an explicit Clock-In/Out
                    // attempt. Fail quietly; the Today card simply shows "Not marked" / "—".
                    _ui.update { it.copy(loading = false) }
                }
        }
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
                _ui.update {
                    it.copy(
                        geoActive = active,
                        geoCenterLat = cLat,
                        geoCenterLng = cLng,
                        geoRadius = rad,
                    )
                }
            } catch (_: Exception) {
                // Guidance is optional — failure just means no distance/inside hint.
            }
        }
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
                            gps = GeofenceGuide.evaluate(out.fix, s.geoCenterLat, s.geoCenterLng, s.geoRadius),
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
                    _ui.update { it.copy(gps = GeofenceGuide.evaluate(fix, s.geoCenterLat, s.geoCenterLng, s.geoRadius), lastFix = fix) }

                    // W6: reuse the pending id when retrying the SAME direction
                    // after a transient failure; otherwise mint a fresh one.
                    val clientPunchId = if (pendingDirection == direction && pendingPunchId != null) {
                        pendingPunchId!!
                    } else {
                        UUID.randomUUID().toString().also { pendingPunchId = it; pendingDirection = direction }
                    }

                    repo.punch(
                        direction = direction,
                        lat = fix.latitude,
                        lng = fix.longitude,
                        accuracy = fix.accuracyMeters,
                        mock = fix.isMock,
                        clientPunchId = clientPunchId,
                        clientCapturedAt = isoTimestamp(fix.capturedAtEpochMs),
                        device = deviceInfo(),
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
    // Campus geofence (guidance only; never sent to the server)
    val geoActive: Boolean = false,
    val geoCenterLat: Double? = null,
    val geoCenterLng: Double? = null,
    val geoRadius: Int? = null,
)
