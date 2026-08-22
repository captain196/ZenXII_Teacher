package com.schoolsync.teacher.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.schoolsync.teacher.R
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.ClientMetadata
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.abs

/**
 * TripTrackingService — F13.1 M3 driver live-GPS stream.
 *
 * Runs as a foreground service for the duration of ONE trip and streams the
 * bus position into Firestore. Started by [TripTrackingController] after the
 * canonical server lifecycle has confirmed the trip is InProgress; stopped on
 * completion, cancellation, or process death.
 *
 * ─── WHY A SEPARATE COMPONENT FROM LocationProvider ──────────────────────
 * [LocationProvider] is the Staff Attendance (Phase 11) surface: a ONE-SHOT
 * foreground fix, explicitly documented as "no background location, so no
 * Android 14 foreground-service-location type". F13 needs the opposite — a
 * continuous stream for the length of a trip. That file is therefore left
 * untouched and this service sits alongside it, reusing its permission and
 * location-services reporters read-only.
 *
 * ─── WRITE MODEL (server contract, Transport_gps_service) ────────────────
 *   HOT  vehicleLivePositions/{schoolId}_{vehicleId}  — UPDATE only, never
 *        create. The document is created server-side at trip start (D-1);
 *        if it is absent the write fails and tracking is simply unavailable,
 *        which is the intended fail-closed behaviour.
 *   COLD vehicleBreadcrumbs/{schoolId}_{gpsSessionId}_{epochMs} — append-only.
 *
 * ─── WHAT IS DELIBERATELY NOT HERE ───────────────────────────────────────
 *  • No map, and no map SDK dependency. The mapping-provider decision keeps
 *    rendering out of the driver app entirely — a driver needs the road, not
 *    a screen. Only canonical telemetry is produced.
 *  • No manual coordinate entry anywhere. Every value comes from the OS
 *    location provider; there is no code path that accepts a typed position.
 */
@AndroidEntryPoint
class TripTrackingService : Service() {

    @Inject lateinit var firestoreService: FirestoreService
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var locationProvider: LocationProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient

    private var tripId: String = ""
    private var vehicleId: String = ""
    private var gpsSessionId: String = ""
    private var driverStaffId: String = ""
    private var schoolId: String = ""

    /** Last ACCEPTED fix — the basis for throttling and stationary suppression. */
    private var lastWriteMs: Long = 0L
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastMovedMs: Long = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fix = result.lastLocation ?: return
            scope.launch { handleFix(fix) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tripId        = intent?.getStringExtra(EXTRA_TRIP_ID).orEmpty()
        vehicleId     = intent?.getStringExtra(EXTRA_VEHICLE_ID).orEmpty()
        gpsSessionId  = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        driverStaffId = intent?.getStringExtra(EXTRA_DRIVER_ID).orEmpty()

        if (tripId.isBlank() || vehicleId.isBlank()) {
            stopSelf(); return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        beginUpdates()

        // NOT sticky: if the process dies the trip is no longer being driven by
        // this service instance, and silently resurrecting a location stream
        // the driver cannot see would break the "tracking is always visible"
        // invariant. The server-side sweeper closes any session left open.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        scope.cancel()
        super.onDestroy()
    }

    // ── location stream ──────────────────────────────────────────────────

    @SuppressLint("MissingPermission")   // guarded immediately below
    private fun beginUpdates() {
        fused = LocationServices.getFusedLocationProviderClient(this)
        if (!hasFineLocation()) { stopSelf(); return }

        // BALANCED, not HIGH_ACCURACY: a bus on a road does not need 3-metre
        // precision, and high accuracy roughly doubles battery draw over a
        // multi-hour route. The server rejects anything worse than 100 m.
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, SAMPLE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(SAMPLE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        runCatching { fused.requestLocationUpdates(request, callback, mainLooper) }
            .onFailure { stopSelf() }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ── per-fix handling ─────────────────────────────────────────────────

    private suspend fun handleFix(loc: Location) {
        val now = System.currentTimeMillis()

        // ── quality gates, mirroring Transport_gps_service::validatePosition ──
        // Applied client-side purely to avoid burning writes the server would
        // reject anyway. The SERVER remains the authority; this is not a
        // security control, since a modified client could skip it.
        if (loc.isMockCompat()) return                       // mock_location
        if (loc.accuracy <= 0f || loc.accuracy > MAX_ACCURACY_M) return   // poor_accuracy
        val speedKmph = (loc.speed * 3.6f).toDouble()
        if (speedKmph < 0 || speedKmph > MAX_PLAUSIBLE_SPEED_KMPH) return // implausible_speed

        // ── throttle + stationary suppression (the write budget) ──
        if (!shouldWrite(loc, now)) return

        if (schoolId.isBlank()) {
            schoolId = tokenManager.schoolCode.firstOrNull().orEmpty()
            if (schoolId.isBlank()) return
        }

        val recordedAt = isoUtc(now)
        val position = mapOf(
            "lat"            to loc.latitude,
            "lng"            to loc.longitude,
            "speed_kmph"     to speedKmph,
            "heading"        to loc.bearing.toDouble(),
            "accuracy_m"     to loc.accuracy.toDouble(),
            "altitude_m"     to if (loc.hasAltitude()) loc.altitude else null,
            "recorded_at"    to recordedAt,
            // Ordering key. The server rule requires this to strictly increase,
            // so a queued offline fix can never overwrite a newer position.
            "recorded_at_ms" to now,
            "received_at"    to recordedAt,
            "is_mock"        to false,
            "source"         to "driver_app",
        ) + ClientMetadata.required()   // required() — NOT asMap(); see that file

        val hotId = "${schoolId}_$vehicleId"
        try {
            // UPDATE, never set(): the hot doc is server-created (D-1). If it
            // does not exist this throws, tracking stops, and no half-formed
            // document is left behind for a parent to read.
            firestoreService.updateDocument(COL_LIVE_POSITIONS, hotId, position)
        } catch (_: Exception) {
            // Offline or session closed. Do NOT queue the hot doc — a stale
            // position must never be replayed as "current". Breadcrumbs below
            // are the durable record.
            return
        }

        lastWriteMs = now
        lastLat = loc.latitude
        lastLng = loc.longitude

        // Breadcrumb — append-only, deterministic id ⇒ replay-idempotent.
        if (gpsSessionId.isNotBlank()) {
            val crumb = mapOf(
                "schoolId"        to schoolId,
                "vehicleId"       to vehicleId,
                "tripId"          to tripId,
                "gps_session_id"  to gpsSessionId,
                "driver_staff_id" to driverStaffId,
                "lat"             to loc.latitude,
                "lng"             to loc.longitude,
                "speed_kmph"      to speedKmph,
                "recorded_at"     to recordedAt,
                "recorded_at_ms"  to now,
                // TTL. MUST be a Timestamp — a String is silently ignored by
                // the Firestore TTL policy and the document would live forever.
                "expires_at"      to Date(now + BREADCRUMB_TTL_MS),
            ) + ClientMetadata.required()
            runCatching {
                firestoreService.setDocument(
                    COL_BREADCRUMBS, "${schoolId}_${gpsSessionId}_$now", crumb, merge = false
                )
            }
        }
    }

    /**
     * The two write-budget controls, mirroring `Transport_gps_service`:
     * a 15-second floor, and suppression while stationary.
     *
     * Stationary suppression is the significant one — pickup dwell is a large
     * share of a trip, and a parked bus does not need four writes a minute.
     * A heartbeat still goes out every 60 s so the parent UI does not render
     * a genuinely-present bus as "stale".
     */
    private fun shouldWrite(loc: Location, now: Long): Boolean {
        if (lastWriteMs == 0L) return true
        if (now - lastWriteMs < MIN_WRITE_INTERVAL_MS) return false

        val pLat = lastLat; val pLng = lastLng
        if (pLat == null || pLng == null) return true

        val moved = FloatArray(1)
        Location.distanceBetween(pLat, pLng, loc.latitude, loc.longitude, moved)
        if (abs(moved[0]) > STATIONARY_RADIUS_M) { lastMovedMs = now; return true }

        val stationaryFor = now - (if (lastMovedMs == 0L) lastWriteMs else lastMovedMs)
        return stationaryFor >= STATIONARY_HEARTBEAT_MS
    }

    private fun Location.isMockCompat(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock
        else @Suppress("DEPRECATION") isFromMockProvider

    private fun isoUtc(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    // ── the always-visible tracking notification ─────────────────────────

    /**
     * Non-dismissible and unambiguous. A privacy invariant of the feature is
     * that the driver can always tell tracking is live — this notification is
     * that guarantee, and Android requires it for a foreground service anyway.
     */
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Trip tracking", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "Shown while your bus location is being shared with parents." }
                )
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trip in progress")
            .setContentText("Sharing bus location with parents")
            // The app's monochrome status-bar icon, matching FCMService.
            // ic_launcher_foreground is a mipmap launcher asset and would not
            // compile here, nor render correctly in the status bar.
            .setSmallIcon(R.drawable.ic_stat_zenxii)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val EXTRA_TRIP_ID    = "trip_id"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_SESSION_ID = "gps_session_id"
        const val EXTRA_DRIVER_ID  = "driver_staff_id"

        private const val CHANNEL_ID      = "trip_tracking"
        private const val NOTIFICATION_ID = 4131

        private const val COL_LIVE_POSITIONS = "vehicleLivePositions"
        private const val COL_BREADCRUMBS    = "vehicleBreadcrumbs"

        // Mirrors Transport_gps_service. Kept in sync by CH-TRN-F13-6.
        private const val SAMPLE_INTERVAL_MS      = 10_000L
        private const val MIN_WRITE_INTERVAL_MS   = 15_000L
        private const val STATIONARY_HEARTBEAT_MS = 60_000L
        private const val STATIONARY_RADIUS_M     = 25.0f
        private const val MAX_ACCURACY_M          = 100.0f
        private const val MAX_PLAUSIBLE_SPEED_KMPH = 150.0
        private const val BREADCRUMB_TTL_MS       = 30L * 24 * 60 * 60 * 1000
    }
}
