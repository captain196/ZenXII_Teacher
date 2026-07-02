package com.schoolsync.teacher.data.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GeofenceGuide — PURE client-side geo math for on-screen GUIDANCE only.
 *
 * Mirrors the server's haversine so the app can show "you are ~42 m from
 * school, inside the zone" BEFORE a punch. It is intentionally advisory:
 * the PHP backend re-computes geofence membership server-side and is the
 * SOLE authority on whether attendance is accepted. Nothing here is trusted
 * by the server.
 *
 * No Android, network, or attendance dependencies — just math.
 */
object GeofenceGuide {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres between two WGS-84 points. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLam = Math.toRadians(lng2 - lng1)
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLam / 2) * sin(dLam / 2)
        val c = 2 * asin(min(1.0, sqrt(a)))
        return EARTH_RADIUS_M * c
    }

    /**
     * Build the pre-check [GpsStatus] from the latest fix and (optionally) the
     * campus geofence. When the geofence is unknown, distance/inside are null.
     * GUIDANCE ONLY — never an attendance decision.
     */
    fun evaluate(
        fix: LocationFix?,
        centerLat: Double?,
        centerLng: Double?,
        radiusMeters: Int?,
    ): GpsStatus {
        if (fix == null) {
            return GpsStatus(available = false, accuracyMeters = null, distanceMeters = null, insideGeofence = null)
        }
        val haveFence = centerLat != null && centerLng != null && radiusMeters != null && radiusMeters > 0
        if (!haveFence) {
            return GpsStatus(available = true, accuracyMeters = fix.accuracyMeters, distanceMeters = null, insideGeofence = null)
        }
        val dist = distanceMeters(fix.latitude, fix.longitude, centerLat!!, centerLng!!)
        return GpsStatus(
            available = true,
            accuracyMeters = fix.accuracyMeters,
            distanceMeters = dist,
            insideGeofence = dist <= radiusMeters!!.toDouble(),
        )
    }
}
