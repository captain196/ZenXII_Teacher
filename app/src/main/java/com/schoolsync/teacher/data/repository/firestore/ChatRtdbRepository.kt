package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.rtdb.NotifBadgeRtdb
import com.schoolsync.teacher.data.model.rtdb.PresenceRtdb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for RTDB-backed communication features: notification badges and presence.
 *
 * RTDB paths used:
 * - NotifBadge/{userId}: per-user badge counts by category
 * - Presence/{userId}: online/offline status and last-seen timestamp
 */
@Singleton
class ChatRtdbRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    // ── Notification Badge ──────────────────────────────────────────────────

    /**
     * One-shot read of notification badge counts for the current user.
     */
    suspend fun getNotifBadge(): Result<NotifBadgeRtdb> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val snapshot = firebaseService.readSnapshot("NotifBadge/$userId")
            if (snapshot.exists()) {
                val badge = snapshot.getValue(NotifBadgeRtdb::class.java)
                    ?: NotifBadgeRtdb()
                Result.success(badge)
            } else {
                Result.success(NotifBadgeRtdb())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe notification badge counts in real time.
     * Reacts to user identity changes (userId) via [flatMapLatest].
     * Emits null when no badge data exists or user is not logged in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotifBadge(): Flow<NotifBadgeRtdb?> {
        return tokenManager.userId
            .map { it?.takeIf { id -> id.isNotBlank() } }
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    firebaseService.listen("NotifBadge/$userId")
                        .map { snapshot ->
                            if (snapshot.exists()) {
                                snapshot.getValue(NotifBadgeRtdb::class.java)
                            } else {
                                null
                            }
                        }
                }
            }
    }

    // ── Presence ────────────────────────────────────────────────────────────

    /**
     * Update the current user's online/offline presence.
     */
    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val data = mapOf<String, Any?>(
                "online" to online,
                "lastSeen" to System.currentTimeMillis()
            )
            firebaseService.setValue("Presence/$userId", data)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * One-shot read of a user's presence status.
     */
    suspend fun getPresence(userId: String): Result<PresenceRtdb?> {
        return try {
            val snapshot = firebaseService.readSnapshot("Presence/$userId")
            if (snapshot.exists()) {
                Result.success(snapshot.getValue(PresenceRtdb::class.java))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a user's presence in real time.
     */
    fun observePresence(userId: String): Flow<PresenceRtdb?> {
        return firebaseService.listen("Presence/$userId")
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.getValue(PresenceRtdb::class.java)
                } else {
                    null
                }
            }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getUserId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
