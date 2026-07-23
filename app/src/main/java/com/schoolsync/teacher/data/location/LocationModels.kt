package com.schoolsync.teacher.data.location

/**
 * Location-layer data models — Phase 11 (GPS staff attendance).
 *
 * Pure data only. NO Android/UI/network/attendance coupling. The location
 * layer hands these up to the repository → ViewModel → UI. The BACKEND is
 * always the final authority on attendance validation; anything here that
 * mentions the geofence is for on-screen GUIDANCE only.
 */

/**
 * A single campus geofence for on-screen GUIDANCE only. The backend supports
 * SEVERAL active campuses (attendancePolicy.gps.geofences[]); a punch is valid
 * inside ANY active one. The server re-validates — this is advisory.
 */
data class Campus(
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Int,
    val name: String?,
)

/** A clean, transport-ready location reading. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy radius in metres (smaller = better). */
    val accuracyMeters: Float,
    /** True when the OS flags this fix as coming from a mock/fake provider. */
    val isMock: Boolean,
    /** Device clock at capture (epoch millis) — informational; server stamps its own. */
    val capturedAtEpochMs: Long,
)

/** Why a location acquisition could not produce a fix. */
enum class LocationError {
    PERMISSION_DENIED,      // location permission not granted
    LOCATION_SERVICES_OFF,  // device location/GPS master switch is off
    TIMEOUT,                // no fix within the timeout (weak/indoor signal)
    UNAVAILABLE,            // provider error / unexpected failure
}

/** Result of a one-shot location acquisition. */
sealed interface LocationOutcome {
    data class Success(val fix: LocationFix) : LocationOutcome
    data class Failure(val error: LocationError) : LocationOutcome
}

/**
 * Pre-check GPS status shown BEFORE the user taps Check-In — guidance only.
 * `distanceMeters` / `insideGeofence` are null when the geofence is unknown
 * (e.g. policy not yet loaded). These never decide attendance; the server does.
 */
data class GpsStatus(
    val available: Boolean,
    val accuracyMeters: Float?,
    val distanceMeters: Double?,
    val insideGeofence: Boolean?,
    /** Name of the NEAREST campus (multi-campus). null when unknown/no fence. */
    val nearestCampusName: String? = null,
)
