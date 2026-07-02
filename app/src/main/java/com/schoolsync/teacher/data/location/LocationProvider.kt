package com.schoolsync.teacher.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationProvider — the ONLY Android location-acquisition surface for the
 * GPS staff-attendance feature (Phase 11).
 *
 * Strict boundaries:
 *   - Acquires a single high-accuracy fix + reports mock/permission/services
 *     state. That's all.
 *   - NO attendance logic, NO networking, NO UI. It returns a clean
 *     [LocationOutcome]; the repository (Phase 12) decides what to do with it.
 *
 * Layering: LocationProvider → Repository → ViewModel → UI.
 *
 * Android 12–15:
 *   - Mock flag: API 31+ uses Location.isMock; below 31 falls back to the
 *     deprecated isFromMockProvider.
 *   - Android 12 "approximate location": if the user grants COARSE only, the
 *     fix accuracy will be poor and the SERVER rejects it (poor_accuracy);
 *     the UI nudges the user to enable Precise location.
 *   - One-shot foreground fix only — no background location, so no Android
 *     14 foreground-service-location type and no Android 15 changes apply.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /** True when FINE or COARSE location permission is currently granted. */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** True only when FINE (precise) location is granted. */
    fun hasPreciseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** True when the device's location master switch (GPS/network) is on. */
    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Acquire ONE fresh high-accuracy fix. Returns a typed [LocationOutcome] —
     * never throws to the caller.
     *
     * @param timeoutMs how long to wait for a fix before reporting TIMEOUT.
     */
    @SuppressLint("MissingPermission") // permission is checked first; SecurityException also handled
    suspend fun getCurrentFix(timeoutMs: Long = 15_000L): LocationOutcome {
        if (!hasLocationPermission()) {
            return LocationOutcome.Failure(LocationError.PERMISSION_DENIED)
        }
        if (!isLocationEnabled()) {
            return LocationOutcome.Failure(LocationError.LOCATION_SERVICES_OFF)
        }
        return try {
            val cts = CancellationTokenSource()
            val location: Location? = withTimeoutOrNull(timeoutMs) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
            cts.cancel()
            if (location == null) {
                LocationOutcome.Failure(LocationError.TIMEOUT)
            } else {
                LocationOutcome.Success(location.toFix())
            }
        } catch (se: SecurityException) {
            LocationOutcome.Failure(LocationError.PERMISSION_DENIED)
        } catch (e: Exception) {
            LocationOutcome.Failure(LocationError.UNAVAILABLE)
        }
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        isMock = isMockCompat(),
        capturedAtEpochMs = time,
    )

    private fun Location.isMockCompat(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
}
