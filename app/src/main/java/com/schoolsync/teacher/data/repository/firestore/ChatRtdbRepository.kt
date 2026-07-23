package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.rtdb.NotifBadgeRtdb
import com.schoolsync.teacher.data.model.rtdb.PresenceRtdb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification badges + user-presence repository (teacher app).
 *
 * Phase 5: migrated off Firebase Realtime Database onto Firestore. The
 * class name is kept stable so the existing DI module doesn't have to
 * move. Backing store:
 *
 *   RTDB  NotifBadge/{userId}   →  Firestore  notifBadges/{userId}
 *   RTDB  Presence/{userId}      →  Firestore  presence/{userId}
 *
 * The NotifBadgeRtdb / PresenceRtdb POJOs are reused verbatim — doc
 * shape is unchanged. A one-time server-side migration
 * (scripts/migrate_rtdb_to_firestore.js) seeds the Firestore
 * collections; from then on this repo is the sole data path.
 */
@Singleton
class ChatRtdbRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {

    companion object {
        private const val COL_NOTIF_BADGES = "notifBadges"
        private const val COL_PRESENCE     = "presence"
    }

    // ── Notification Badge ──────────────────────────────────────────────────

    suspend fun getNotifBadge(): Result<NotifBadgeRtdb> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        return try {
            val snap: DocumentSnapshot? = firestoreService.getDocument(COL_NOTIF_BADGES, userId)
            val badge = snap?.takeIf { it.exists() }
                ?.toObject(NotifBadgeRtdb::class.java)
                ?: NotifBadgeRtdb()
            Result.success(badge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotifBadge(): Flow<NotifBadgeRtdb?> {
        return tokenManager.userId
            .map { it?.takeIf { id -> id.isNotBlank() } }
            .flatMapLatest { userId ->
                if (userId == null) flowOf(null)
                else firestoreService.observeDocumentAs<NotifBadgeRtdb>(COL_NOTIF_BADGES, userId)
            }
            .catch { emit(null) }   // listener error → emit null, keep badge flow alive
    }

    // ── Presence ────────────────────────────────────────────────────────────

    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        return try {
            firestoreService.setDocument(
                COL_PRESENCE,
                userId,
                mapOf(
                    "userId"   to userId,
                    "online"   to online,
                    "lastSeen" to System.currentTimeMillis(),
                ),
                merge = true,
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPresence(userId: String): Result<PresenceRtdb?> {
        return try {
            val snap = firestoreService.getDocument(COL_PRESENCE, userId)
            val obj = snap?.takeIf { it.exists() }?.toObject(PresenceRtdb::class.java)
            Result.success(obj)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePresence(userId: String): Flow<PresenceRtdb?> =
        firestoreService.observeDocumentAs<PresenceRtdb>(COL_PRESENCE, userId)
            .catch { emit(null) }   // listener error → emit null, keep presence flow alive

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getUserId(): String? =
        tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
}
