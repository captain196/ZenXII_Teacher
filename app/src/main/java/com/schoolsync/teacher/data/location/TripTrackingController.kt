package com.schoolsync.teacher.data.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.schoolsync.teacher.data.model.firestore.TripLogDoc
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TripTrackingController — the only way the UI layer starts or stops the
 * live-GPS stream.
 *
 * Exists so a ViewModel never touches Android `Service` plumbing directly, and
 * so "is tracking allowed to start?" is answered in exactly one place.
 *
 * Note what this does NOT decide: whether the TRIP may start. That is the
 * server's call, made by the canonical `Transport_service` lifecycle. This
 * class only governs the location stream that follows a successful start.
 */
@Singleton
class TripTrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationProvider: LocationProvider,
) {

    /** Why tracking could not begin — mapped to driver-facing copy by the UI. */
    enum class Blocker { LOCATION_SERVICES_OFF, PERMISSION_MISSING }

    /**
     * The precondition checked BEFORE a trip is started.
     *
     * Both reporters come from the existing [LocationProvider] (Staff
     * Attendance, Phase 11), used read-only — that file is not modified.
     *
     * This is a CLIENT-SIDE guard by necessity: no server can observe a
     * device's location-services switch, and a client-asserted "GPS is on"
     * flag would be trivially forged, so treating one as authoritative would
     * be security theatre. The server's real protections are separate and
     * unconditional — it rejects mock and low-quality fixes, refuses
     * out-of-order writes, and sweeps any session that never streams.
     *
     * @return null when tracking may begin, otherwise the reason it may not.
     */
    fun preflight(): Blocker? = when {
        !locationProvider.isLocationEnabled()    -> Blocker.LOCATION_SERVICES_OFF
        !locationProvider.hasLocationPermission() -> Blocker.PERMISSION_MISSING
        else -> null
    }

    /**
     * Start streaming for a trip that the SERVER has already moved to
     * InProgress. Call only after the lifecycle endpoint returned success —
     * the hot document is created server-side, and the service writes by
     * update only.
     */
    fun start(trip: TripLogDoc) {
        val intent = Intent(context, TripTrackingService::class.java).apply {
            putExtra(TripTrackingService.EXTRA_TRIP_ID, trip.id)
            putExtra(TripTrackingService.EXTRA_VEHICLE_ID, trip.vehicleId)
            putExtra(TripTrackingService.EXTRA_SESSION_ID, trip.gpsSessionId)
            putExtra(TripTrackingService.EXTRA_DRIVER_ID, trip.driverStaffId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stop streaming. Safe to call when nothing is running.
     *
     * This is the courteous stop, not the authoritative one: the server
     * disarms `live_tracking_enabled` and deletes the hot document as part of
     * completing or cancelling the trip, so even a driver app that is killed
     * mid-trip cannot keep a position live.
     */
    fun stop() {
        context.stopService(Intent(context, TripTrackingService::class.java))
    }
}
