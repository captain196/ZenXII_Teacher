package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
/** One person who saw a story, with their reaction (if any). */
data class StoryViewerEntry(
    val userId: String,
    val name: String,
    /** Their emoji reaction, or null if they viewed but didn't react. */
    val emoji: String?,
    val viewedAtMillis: Long
)

/** Everything the teacher's "insights" sheet needs for one story. */
data class StoryInsights(
    val viewCount: Int,
    /** emoji → count (denormalised on the story doc). */
    val reactionCounts: Map<String, Int>,
    /** Individual viewers, newest first. */
    val viewers: List<StoryViewerEntry>
)

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

    /**
     * The school identifier used for ALL story queries. Prefer
     * KEY_SCHOOL_ID (the JWT `school_id` claim value, always set at
     * login) and fall back to KEY_SCHOOL_CODE only if it's blank.
     *
     * Why: KEY_SCHOOL_CODE is written only CONDITIONALLY in saveProfile
     * and can be blank/stale on some accounts. When it was blank, the
     * story queries below returned an empty list — so a teacher whose
     * schoolCode hadn't been populated saw NO stories at all (e.g. a
     * whole-school post from another teacher was invisible). schoolId
     * holds the same SCH_… value and is reliably present, so it's the
     * correct key to filter the `schoolId` doc field on.
     */
    private val schoolKey: Flow<String?> =
        combine(tokenManager.schoolId, tokenManager.schoolCode) { id, code ->
            id?.takeIf { it.isNotBlank() } ?: code
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
        return schoolKey
            .distinctUntilChanged()
            .flatMapLatest { storedSchool ->
                flow {
                    // The Firestore rule authorises a story read only when
                    // story.schoolId == the caller's `school_id` claim, and a
                    // listener whose query filters a DIFFERENT value is denied
                    // wholesale (→ zero stories). Resolve the filter value from
                    // the LIVE ID token claim; fall back to the stored school
                    // only when the token can't be read (offline).
                    val claimSchool = runCatching {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            ?.getIdToken(false)?.await()?.claims?.get("school_id")?.toString()
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    val schoolForQuery = claimSchool ?: storedSchool
                    if (schoolForQuery.isNullOrBlank()) {
                        emit(emptyList())
                    } else {
                        emitAll(
                            firestoreService.observeQuery(COLLECTION) { ref ->
                                ref.whereEqualTo("schoolId", schoolForQuery)
                            }.map { snap ->
                                val nowMs = System.currentTimeMillis()
                                snap.documents
                                    .mapNotNull { it.toObject(StoryDoc::class.java) }
                                    .filter { it.status == "active" && it.expiresAtMillis > nowMs }
                                    .sortedByDescending { it.expiresAtMillis }
                            }.onStart { emit(emptyList()) }
                             .catch { emit(emptyList()) }
                        )
                    }
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
                else schoolKey
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
        val schoolCode = schoolKey.firstOrNull()?.takeIf { it.isNotBlank() }
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

    /**
     * Read who saw a story and what they reacted. Joins the
     * `viewers` + `reactions` subcollections by userId. Viewer/reactor
     * names are read from the denormalised `userName` field the parent
     * app writes; older docs without it fall back to "Parent".
     */
    suspend fun getStoryInsights(storyId: String): Result<StoryInsights> {
        return try {
            val fs = FirebaseFirestore.getInstance()
            val storyRef = fs.collection(COLLECTION).document(storyId)

            val storySnap = storyRef.get().await()
            val story = storySnap.toObject(StoryDoc::class.java)

            val viewersSnap = storyRef.collection(VIEWERS_SUBCOLLECTION).get().await()
            val reactionsSnap = storyRef.collection(
                com.schoolsync.teacher.data.model.firestore.StorySharedConfig.REACTIONS_SUBCOLLECTION
            ).get().await()

            // reactions keyed by userId → (emoji, name)
            val reactionByUser = reactionsSnap.documents.associate { d ->
                (d.getString("userId") ?: d.id) to
                    Pair(d.getString("emoji").orEmpty(), d.getString("userName").orEmpty())
            }

            // Merge viewers with their reaction (LinkedHashMap keeps order).
            val entries = LinkedHashMap<String, StoryViewerEntry>()
            viewersSnap.documents.forEach { d ->
                val uid = d.getString("userId") ?: d.id
                val nm = d.getString("userName").orEmpty()
                    .ifBlank { reactionByUser[uid]?.second.orEmpty() }
                val viewedAt = d.getTimestamp("viewedAt")?.toDate()?.time ?: 0L
                val emoji = reactionByUser[uid]?.first?.takeIf { it.isNotBlank() }
                entries[uid] = StoryViewerEntry(uid, nm.ifBlank { "Parent" }, emoji, viewedAt)
            }
            // Reactors who somehow aren't in viewers (defensive union).
            reactionByUser.forEach { (uid, pair) ->
                if (!entries.containsKey(uid)) {
                    entries[uid] = StoryViewerEntry(
                        uid, pair.second.ifBlank { "Parent" },
                        pair.first.takeIf { it.isNotBlank() }, 0L
                    )
                }
            }

            Result.success(
                StoryInsights(
                    viewCount = story?.viewCount ?: viewersSnap.size(),
                    reactionCounts = (story?.reactionCounts ?: emptyMap())
                        .filterValues { it > 0 },
                    viewers = entries.values.sortedByDescending { it.viewedAtMillis }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * LIVE set of story ids this staff member has viewed — real-time
     * `viewers` collection-group listener keyed by userId. Drives the ring's
     * grey/colored state AND keeps SEPARATE VM instances in sync: when the
     * full-screen viewer writes a viewer doc, the Dashboard ring's VM (also
     * observing this) greys the ring within ~100ms — no shared VM needed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSeenStoryIds(): Flow<Set<String>> =
        combine(tokenManager.userId, schoolKey) { uid, school -> uid.orEmpty() to school.orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { (userId, school) ->
                // SEC-3: the viewers collection-group READ rule is now tenant-
                // bound, so the query MUST carry the schoolId filter or it is
                // denied wholesale. Composite CG index schoolId+userId backs it.
                if (userId.isBlank() || school.isBlank()) flowOf(emptySet())
                else callbackFlow {
                    val reg = FirebaseFirestore.getInstance()
                        .collectionGroup(VIEWERS_SUBCOLLECTION)
                        .whereEqualTo("schoolId", school)
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener { snap, err ->
                            if (err != null || snap == null) { trySend(emptySet()); return@addSnapshotListener }
                            trySend(snap.documents.mapNotNull { it.reference.parent.parent?.id }.toSet())
                        }
                    awaitClose { reg.remove() }
                }
            }

    /**
     * Story ids this staff member has already viewed — read once from the
     * `viewers` collection-group (docs keyed by userId) so the ring's
     * seen/unseen state survives app restarts (Instagram-style), not just
     * the current session. Mirrors the parent app's hydration.
     */
    suspend fun getSeenStoryIds(): Set<String> {
        return try {
            val userId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return emptySet()
            val school = schoolKey.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return emptySet()
            val snap = FirebaseFirestore.getInstance()
                .collectionGroup(VIEWERS_SUBCOLLECTION)
                .whereEqualTo("schoolId", school)   // SEC-3 tenant scope
                .whereEqualTo("userId", userId)
                .get().await()
            snap.documents.mapNotNull { it.reference.parent.parent?.id }.toSet()
        } catch (e: Exception) {
            emptySet()   // non-fatal: fall back to session-only seen
        }
    }

    /**
     * schoolId to stamp on engagement writes — the LIVE `school_id` claim
     * (the exact value the tenant-bound engagement rule compares against),
     * falling back to the stored school if the token can't be read.
     */
    private suspend fun resolveWriteSchoolId(): String {
        val claim = runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.getIdToken(false)?.await()?.claims?.get("school_id")?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return claim
            ?: tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: tokenManager.schoolCode.firstOrNull().orEmpty()
    }

    /**
     * Record that THIS staff member (teacher/admin) viewed a story. Writes
     * the per-user viewer marker (keyed by staff userId, carrying userName);
     * the aggregate viewCount is bumped exactly once SERVER-SIDE by the CF
     * onStoryViewerCreated (SEC-4). The viewer doc also drives persistent
     * ring seen-state and the author's "who viewed" list. Caller must NOT
     * invoke this for the author viewing their OWN story (no self-counting).
     */
    suspend fun markAsViewed(storyId: String): Result<Unit> {
        val userId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        val userName = tokenManager.userName.firstOrNull().orEmpty()
        // C2: stamp the caller's own schoolId so the engagement rule can
        // tenant-bind the write (must equal the token's school_id claim).
        val schoolId = resolveWriteSchoolId()
        if (schoolId.isBlank()) return Result.failure(Exception("School not available"))
        return try {
            val fs = FirebaseFirestore.getInstance()
            val storyRef  = fs.collection(COLLECTION).document(storyId)
            val viewerRef = storyRef.collection(VIEWERS_SUBCOLLECTION).document(userId)
            fs.runTransaction { tx ->
                val existing = tx.get(viewerRef)
                if (existing.exists()) {
                    // Already counted — never inflate on re-view. Backfill a
                    // blank name if an earlier doc lacked it (also stamp
                    // schoolId so the update satisfies the tenant-bound rule).
                    if (existing.getString("userName").isNullOrBlank() && userName.isNotBlank()) {
                        tx.update(viewerRef, mapOf("userName" to userName, "schoolId" to schoolId))
                    }
                    return@runTransaction null
                }
                // SEC-4: write ONLY the viewer marker. viewCount is
                // incremented server-side by the CF onStoryViewerCreated
                // (once per unique viewer doc create) — the client never
                // writes the forgeable aggregate.
                tx.set(viewerRef, hashMapOf<String, Any?>(
                    "viewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "userId"   to userId,
                    "userName" to userName,
                    "schoolId" to schoolId
                ))
                null
            }.await()
            Result.success(Unit)
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

        val schoolCode = schoolKey.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))

        // Hardening #4 — rate limit (max TEACHER_DAILY_LIMIT stories
        // per rolling 24h window). Query counts only active, non-
        // expired docs for this teacher since the client whereGreaterThan
        // on expiresAtTs already bounds to "today's window" for us.
        // C1 fix: FAIL-CLOSED. The 3-field composite index
        // (schoolId+teacherId+expiresAtTs) now exists, so this query should
        // never FAILED_PRECONDITION; if the count errors anyway (transient),
        // we reject rather than silently allowing unlimited uploads — the
        // rate limit is a real control, not best-effort.
        val activeTodayCount: Int = try {
            val nowTs = com.google.firebase.Timestamp.now()
            firestoreService.queryDocumentsAs<StoryDoc>(COLLECTION) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("teacherId", teacherId)
                    .whereGreaterThan("expiresAtTs", nowTs)
            }.count { it.status == "active" }
        } catch (e: Exception) {
            return Result.failure(IllegalStateException(
                "Couldn't verify your daily story limit right now. " +
                "Please check your connection and try again."
            ))
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

        // SEC-1: a whole-school post carries the '*' sentinel INSIDE
        // audienceClassKeys (not an empty list) so the parent's server-side
        // array-contains-any query can match it. Class-targeted posts keep
        // their canonical keys unchanged.
        val audience = audienceClassKeys.ifEmpty {
            listOf(com.schoolsync.teacher.data.model.firestore.StorySharedConfig.AUDIENCE_ALL)
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
            // Audience scoping (v1) — ['*'] = school-wide, else class keys.
            "audienceClassKeys" to audience,
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
