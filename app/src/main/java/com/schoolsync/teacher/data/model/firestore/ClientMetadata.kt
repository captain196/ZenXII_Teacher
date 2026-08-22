package com.schoolsync.teacher.data.model.firestore

import android.os.Build
import com.schoolsync.teacher.BuildConfig

/**
 * Client diagnostic metadata attached to every Teacher-originated
 * operational write. F9 (Refinement additional, 2026-07-07).
 *
 * These values are NOT used for authorisation — they are retained
 * strictly for operational diagnostics, troubleshooting and audit
 * analysis:
 *   • app_version       — release identifier (BuildConfig.VERSION_NAME)
 *   • client_platform   — 'android' (matches Firestore rule enum)
 *   • client_timestamp  — device wall-clock at write time (millis).
 *                         The server-side createdAt / updatedAt fields
 *                         remain the authoritative time source; this
 *                         field lets us detect clock skew or replay.
 *
 * Firestore rule `hasClientMetadata()` enforces presence + shape on
 * driver-fired writes to tripLogs (update), transportAttendance
 * (create), fuelLogs (create), and sosAlerts (create).
 */
object ClientMetadata {
    private const val PLATFORM = "android"

    /** Returns the metadata map to spread into any write payload. */
    fun asMap(): Map<String, Any> = mapOf(
        "app_version"      to BuildConfig.VERSION_NAME,
        "client_platform"  to PLATFORM,
        "client_timestamp" to System.currentTimeMillis(),
        // Optional troubleshooting extras — not required by the rule,
        // just useful when reading audit rows post-incident.
        "device_sdk"       to Build.VERSION.SDK_INT,
        "device_model"     to (Build.MODEL ?: "")
    )

    /**
     * The three fields `hasClientMetadata()` actually requires, and nothing else.
     *
     * F13.1 live-position writes go to `vehicleLivePositions`, whose rule pins
     * the writable fields with `affectedKeys().hasOnly(...)`. The diagnostic
     * extras in [asMap] are NOT in that allowlist, so using it there would make
     * Firestore reject every position update with permission-denied.
     *
     * Keeping the payload minimal is also the right call on its own terms: this
     * map is written up to four times a minute per vehicle for the length of
     * every trip, and `device_model` never changes mid-trip.
     */
    fun required(): Map<String, Any> = mapOf(
        "app_version"      to BuildConfig.VERSION_NAME,
        "client_platform"  to PLATFORM,
        "client_timestamp" to System.currentTimeMillis()
    )
}
