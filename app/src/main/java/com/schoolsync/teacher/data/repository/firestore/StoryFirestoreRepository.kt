package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stories — single source of truth for the teacher app.
 *
 * Reads + writes the Firestore collection `stories`. NO RTDB FALLBACK.
 * Same collection + identical doc shape as parent app + admin panel
 * — see model/firestore/StoryDoc.kt for the canonical contract.
 *
 * All reads are real-time snapshot listeners so a write from any
 * client (teacher upload, admin moderation, parent view increment)
 * is reflected on every other client within ~100ms — no manual
 * refresh required.
 *
 * Validation lives in [uploadStory] only: caption ≤ 500 chars, type
 * ∈ {image, video}, mediaUrl present, identity fields present.
 */
@Singleton
class StoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    companion object {
        // Delegates to StorySharedConfig — single source of truth
        // (also mirrored in admin PHP Stories.php).
        const val COLLECTION = com.schoolsync.teacher.data.model.firestore.StorySharedConfig.COLLECTION
        const val VIEWERS_SUBCOLLECTION = com.schoolsync.teacher.data.model.firestore.StorySharedConfig.VIEWERS_SUBCOLLECTION
        const val MAX_CAPTION_LENGTH = com.schoolsync.teacher.data.model.firestore.StorySharedConfig.MAX_CAPTION_LENGTH
        const val EXPIRY_MILLIS = com.schoolsync.teacher.data.model.firestore.StorySharedConfig.EXPIRY_MILLIS
        val ALLOWED_TYPES = com.schoolsync.teacher.data.model.firestore.StorySharedConfig.ALLOWED_TYPES
        const val TEACHER_DAILY_LIMIT = com.schoolsync.teacher.data.model.firestore.StorySharedConfig.TEACHER_DAILY_LIMIT
    }

    // ─── REAL-TIME LISTENERS ───────────────────────────────────────

    /**
     * Real-time stream of NON-EXPIRED + ACTIVE stories for this
     * school. Emits a fresh list on every Firestore change (any
     * teacher uploads / admin moderates / story expires).
     *
     * Used by parent-style "all stories on Dashboard" callers; not
     * currently consumed by the teacher VM (which only wants its own).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveStories(): Flow<List<StoryDoc>> {
        return tokenManager.schoolCode
            .flatMapLatest { schoolCode ->
                if (schoolCode.isNullOrBlank()) flowOf(emptyList())
                else {
                    // Single-field equality query (schoolId only) — auto-
                    // indexed, so NO composite index is required and the
                    // listener never errors out. Expiry + status filtering
                    // and the newest-first sort are done CLIENT-SIDE (story
                    // volume per school is tiny). This is what keeps the
                    // ring carousel from flickering: the previous
                    // schoolId==/expiresAtTs>/orderBy query needed a
                    // composite index and, until it exists, failed with
                    // FAILED_PRECONDITION — Firestore would serve cached
                    // docs (ring appears) then reject on the server (ring
                    // vanishes). Same pattern as observeMyStories.
                    firestoreService.observeQuery(COLLECTION) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                    }.map { snap ->
                        val nowMs = System.currentTimeMillis()
                        snap.documents
                            .mapNotNull { it.toObject(StoryDoc::class.java) }
                            .filter { it.status == "active" && it.expiresAtMillis > nowMs }
                            .sortedByDescending { it.expiresAtMillis }
                    }.onStart { emit(emptyList()) }
                     .catch { emit(emptyList()) }
                }
            }
    }

    /**
     * Real-time stream of THIS teacher's stories (active + expired
     * + flagged + removed all included so the teacher can see the
     * full history of what they've posted today). Sort: newest first.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMyStories(): Flow<List<StoryDoc>> {
        return tokenManager.userId
            .flatMapLatest { teacherId ->
                if (teacherId.isNullOrBlank()) flowOf(emptyList())
                else tokenManager.schoolCode
                    .flatMapLatest { schoolCode ->
                        if (schoolCode.isNullOrBlank()) flowOf(emptyList())
                        else firestoreService.observeQuery(COLLECTION) { ref ->
                            ref.whereEqualTo("schoolId", schoolCode)
                                .whereEqualTo("teacherId", teacherId)
                            // Note: NO orderBy on createdAt — Firestore will
                            // drop docs whose createdAt is still pending the
                            // server timestamp roundtrip. Sort client-side
                            // by expiresAt (which we set at write time).
                        }.map { snap ->
                            snap.documents
                                .mapNotNull { it.toObject(StoryDoc::class.java) }
                                .sortedByDescending { it.expiresAtMillis }
                        }.onStart { emit(emptyList()) }
                         .catch { emit(emptyList()) }
                    }
            }
    }

    // ─── ONE-SHOT (legacy callers; prefer the listeners above) ─────

    suspend fun getMyStories(): Result<List<StoryDoc>> {
        val teacherId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        return try {
            // Query by teacherId (not authorId) for now — legacy docs
            // (pre-v1.9) only have teacherId. Dual-writes keep both
            // populated until v2.0 removal. Switch to authorId then.
            val stories = firestoreService.queryDocumentsAs<StoryDoc>(COLLECTION) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("teacherId", teacherId)
            }
            Result.success(stories.sortedByDescending { it.expiresAtMillis })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── WRITES ────────────────────────────────────────────────────

    /**
     * Upload a new story. Single-source validation applied here so
     * teacher VM + any future caller share the same rules.
     *
     * Phase C: dual-emits canonical author* fields (preferred by new
     * readers) AND legacy teacher* fields (kept so older parent app
     * builds still render). Reads are tolerant to either via
     * StoryDoc.effectiveAuthor* helpers.
     *
     * authorPic falls back to the cached profile pic from TokenManager
     * when the caller doesn't supply one — denormalised at upload
     * time so the parent viewer doesn't need a separate teacher-doc
     * lookup.
     *
     * Returns the new story id on success. createdAt is set via
     * serverTimestamp so it never drifts from the wall clock.
     */
    suspend fun uploadStory(
        mediaUrl: String,
        type: String = "image",
        caption: String = "",
        teacherName: String,
        teacherPic: String = "",
        /** Canonical class-section tokens (StorySharedConfig.audienceKey).
         *  EMPTY = school-wide. Teacher posts default to their own
         *  class-teacher section(s); "Whole school" clears it. */
        audienceClassKeys: List<String> = emptyList()
    ): Result<String> {
        // Validation
        val cleanUrl     = mediaUrl.trim()
        val cleanCaption = caption.trim()
        val cleanType    = type.trim().lowercase()
        if (cleanUrl.isBlank())                      return Result.failure(IllegalArgumentException("Media URL is required"))
        if (cleanType !in ALLOWED_TYPES)             return Result.failure(IllegalArgumentException("Type must be image or video"))
        if (cleanCaption.length > MAX_CAPTION_LENGTH) return Result.failure(IllegalArgumentException("Caption exceeds $MAX_CAPTION_LENGTH chars"))

        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))

        // Hardening #4 — rate limit (max TEACHER_DAILY_LIMIT stories
        // per rolling 24h window). Query counts only active, non-
        // expired docs for this teacher since the client whereGreaterThan
        // on expiresAtTs already bounds to "today's window" for us.
        val activeTodayCount = try {
            val nowTs = com.google.firebase.Timestamp.now()
            firestoreService.queryDocumentsAs<StoryDoc>(COLLECTION) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("teacherId", teacherId)
                    .whereGreaterThan("expiresAtTs", nowTs)
            }.count { it.status == "active" }
        } catch (e: Exception) {
            0   // fail-open: if count fails, allow the upload
        }
        if (activeTodayCount >= TEACHER_DAILY_LIMIT) {
            return Result.failure(IllegalStateException(
                "Daily limit reached ($TEACHER_DAILY_LIMIT/day). " +
                "Wait for your earliest story to expire before posting again."
            ))
        }

        // authorPic: prefer caller value, else pull from cached profile.
        val resolvedPic = teacherPic.ifBlank {
            tokenManager.profilePic.firstOrNull().orEmpty()
        }

        val storyId = "${schoolCode}_${teacherId}_${System.currentTimeMillis()}"
        val expiresAtMillis = System.currentTimeMillis() + EXPIRY_MILLIS
        // Firestore TTL requires Timestamp (not Long). Keep both: the
        // Timestamp is what TTL reads; the client query `whereGreaterThan`
        // still works against Timestamp values.
        val expiresAtTs = com.google.firebase.Timestamp(expiresAtMillis / 1000, 0)

        val data = hashMapOf<String, Any?>(
            "schoolId"        to schoolCode,
            // Phase C — canonical author fields (new readers use these)
            "authorId"        to teacherId,
            "authorName"      to teacherName,
            "authorPic"       to resolvedPic,
            "authorType"      to "teacher",
            // Legacy aliases (back-compat with older parent builds)
            "teacherId"       to teacherId,
            "teacherName"     to teacherName,
            "teacherPic"      to resolvedPic,
            // Content
            "mediaUrl"        to cleanUrl,
            "type"            to cleanType,
            "caption"         to cleanCaption,
            "priority"        to "normal",
            // Audience scoping (v1) — empty list = school-wide.
            "audienceClassKeys" to audienceClassKeys,
            // Reactions (v1) — starts empty; parent app increments.
            "reactionCounts"  to emptyMap<String, Int>(),
            // Lifecycle — expiresAtTs (Timestamp) is the canonical
            // expiry field used by both clients AND Firestore TTL.
            // expiresAt (Long) is written for one more release so
            // pre-v1.9 readers don't break. Drop at v2.0.
            "createdAt"       to firestoreService.serverTimestamp(),
            "expiresAtTs"     to expiresAtTs,
            "expiresAt"       to expiresAtMillis,   // LEGACY — remove in v2.0
            "viewCount"       to 0,
            // status stays 'active' until admin moderates. Initialising
            // it explicitly means the parent reader's status filter
            // matches even on the very first listener emission (before
            // the server has applied any default).
            "status"          to "active",
            "moderatedBy"     to "",
            "moderatedByName" to "",
            "moderatedAt"     to 0L,
            "moderationReason" to ""
        )

        return try {
            firestoreService.setDocument(COLLECTION, storyId, data)
            Result.success(storyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        return try {
            firestoreService.deleteDocument(COLLECTION, storyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
