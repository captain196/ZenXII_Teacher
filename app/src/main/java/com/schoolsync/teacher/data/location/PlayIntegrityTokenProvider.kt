package com.schoolsync.teacher.data.location

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlayIntegrityTokenProvider — requests a Google Play Integrity token to attest,
 * server-side, that a GPS punch came from a genuine, unmodified app on a genuine
 * device. This closes the last spoofing gap: the geofence is server-authoritative
 * but judges CLIENT-supplied coordinates, and the only other signal is the
 * bypassable `mock` flag.
 *
 * INERT BY DEFAULT. [cloudProjectNumber] is 0 until you:
 *   1. Enable the Play Integrity API in Google Play Console (App integrity) and
 *      note the linked Google Cloud project number.
 *   2. Set that number below (or wire it to a BuildConfig field / remote config).
 *   3. Turn on server verification in config/play_integrity.php.
 * While it is 0, [tokenOrNull] returns null immediately (no library call), the
 * punch omits `integrityToken`, and the server — with attestation not required —
 * accepts the punch exactly as before.
 */
@Singleton
class PlayIntegrityTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PlayIntegrity"

        /** Google Cloud project number linked in Play Console. 0 = disabled. */
        private const val cloudProjectNumber: Long = 0L
    }

    /** True when attestation is configured on this build. */
    fun isEnabled(): Boolean = cloudProjectNumber > 0L

    /**
     * Best-effort integrity token for a punch, using [nonce] (the per-attempt
     * clientPunchId) to bind the token to this request. Returns null when
     * disabled or on ANY failure — the caller still punches; the server decides
     * whether a missing token is acceptable (config `require`).
     */
    suspend fun tokenOrNull(nonce: String): String? {
        if (!isEnabled()) return null
        return try {
            val manager = IntegrityManagerFactory.create(context)
            val response = manager.requestIntegrityToken(
                IntegrityTokenRequest.builder()
                    .setCloudProjectNumber(cloudProjectNumber)
                    .setNonce(nonce)
                    .build()
            ).await()
            response.token()
        } catch (e: Exception) {
            Log.w(TAG, "integrity token request failed", e)
            null
        }
    }
}
