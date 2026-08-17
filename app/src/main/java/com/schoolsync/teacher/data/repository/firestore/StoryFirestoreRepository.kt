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
    val viewedAtMillis: Long,
    /** Denormalised profile photo URL; blank → the UI draws initials. */
    val pic: String = "",
    /**
     * True when this person watched the story to the end, rather than merely
     * opening it past the 500ms dwell that records a view.
     */
    val completed: Boolean = false,
    /**
     * True when this person reacted but has no viewer doc — i.e. the
     * reaction landed and the view marker didn't. Surfaced so the count
     * and the list can't silently disagree.
     */
    val reactedOnly: Boolean = false
)

/** Everything the teacher's "insights" sheet needs for one story. */
data class StoryInsights(
    val viewCount: Int,
    /** emoji → count (denormalised on the story doc). */
    val reactionCounts: Map<String, Int>,
    /** Individual viewers, newest first. */
    val viewers: List<StoryViewerEntry>
) {
    /**
     * The number to actually SHOW.
     *
     * `viewCount` is the Cloud-Function aggregate (monotonic, one bump per
     * unique viewer doc) while `viewers` is the tenant-filtered list. They can
     * legitimately diverge — a legacy viewer doc predating the `schoolId` field
     * is counted but unreadable, and the CF trigger lands a beat after the
     * viewer doc. Taking the max means the pill never reads LOWER than the
     * number of rows sitting right underneath it.
     */
    val displayViewCount: Int
        get() = maxOf(viewCount, viewers.count { !it.reactedOnly })

    /**
     * How many people watched to the end. Always derived from the rows (there
     * is no server-side aggregate for it), so unlike [displayViewCount] it can
     * only ever be as complete as the list itself.
     */
    val completedCount: Int
        get() = viewers.count { it.completed }
}

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
     * LIVE "who saw this story" — Instagram/WhatsApp-style.
     *
     * Three snapshot listeners (the story doc for the aggregate counters, plus
     * the `viewers` and `reactions` subcollections) combined into one stream,
     * so the author's sheet fills in as people watch instead of freezing on
     * whatever was true the instant it opened. This replaces a one-shot `get()`
     * that could never update — someone viewing three seconds later changed
     * nothing on screen.
     *
     * Emits `Result` rather than a bare value on purpose: a permission error, a
     * dropped connection and a genuinely-unseen story are three different
     * things, and the UI has to be able to tell them apart. Collapsing a
     * failure to an empty list is what made "no views yet" indistinguishable
     * from "couldn't load".
     *
     * Viewer/reactor names come from the denormalised `userName` the writer
     * stamps; docs without one fall back to a neutral "Viewer" (NOT "Parent" —
     * staff view stories too, and mislabelling a staff viewer as a parent is
     * wrong).
     */
    fun observeStoryInsights(storyId: String): Flow<Result<StoryInsights>> {
        if (storyId.isBlank()) return flowOf(Result.failure(Exception("Missing story id")))
        val fs = FirebaseFirestore.getInstance()
        val storyRef = fs.collection(COLLECTION).document(storyId)

        val storyFlow: Flow<Result<StoryDoc?>> = callbackFlow {
            val reg = storyRef.addSnapshotListener { snap, err ->
                when {
                    err != null -> trySend(Result.failure(err))
                    else -> trySend(Result.success(snap?.toObject(StoryDoc::class.java)))
                }
            }
            awaitClose { reg.remove() }
        }

        // The viewers/reactions read rule requires resource.data.schoolId ==
        // the caller's `school_id` claim, so an UNFILTERED subcollection query
        // is denied wholesale → empty list ("can't see who viewed"). Filter by
        // the caller's own school so the query aligns with the rule. Resolved
        // per-subscription from the live claim (falling back to the stored
        // school offline) rather than read off the story doc, so a listener
        // never has to wait on another read to start.
        fun engagementFlow(sub: String): Flow<Result<List<com.google.firebase.firestore.DocumentSnapshot>>> =
            flow {
                val school = resolveWriteSchoolId()
                if (school.isBlank()) {
                    emit(Result.failure(Exception("School not available")))
                    return@flow
                }
                emitAll(
                    callbackFlow {
                        val reg = storyRef.collection(sub)
                            .whereEqualTo("schoolId", school)
                            .addSnapshotListener { snap, err ->
                                when {
                                    err != null -> trySend(Result.failure(err))
                                    else -> trySend(Result.success(snap?.documents.orEmpty()))
                                }
                            }
                        awaitClose { reg.remove() }
                    }
                )
            }

        val viewersFlow = engagementFlow(VIEWERS_SUBCOLLECTION)
        val reactionsFlow = engagementFlow(
            com.schoolsync.teacher.data.model.firestore.StorySharedConfig.REACTIONS_SUBCOLLECTION
        )

        return combine(storyFlow, viewersFlow, reactionsFlow) { storyR, viewersR, reactionsR ->
            // Any leg failing fails the whole read — better a visible "Retry"
            // than a half-populated list the author would read as complete.
            val story = storyR.getOrElse { return@combine Result.failure(it) }
            val viewerDocs = viewersR.getOrElse { return@combine Result.failure(it) }
            val reactionDocs = reactionsR.getOrElse { return@combine Result.failure(it) }
            Result.success(mergeInsights(story, viewerDocs, reactionDocs))
        }.catch { emit(Result.failure(it)) }
    }

    /** Join viewers × reactions by userId into the sheet's view model. */
    private fun mergeInsights(
        story: StoryDoc?,
        viewerDocs: List<com.google.firebase.firestore.DocumentSnapshot>,
        reactionDocs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): StoryInsights {
        // reactions keyed by userId → (emoji, name, pic)
        val reactionByUser = reactionDocs.associate { d ->
            (d.getString("userId") ?: d.id) to Triple(
                d.getString("emoji").orEmpty(),
                d.getString("userName").orEmpty(),
                d.getString("userPic").orEmpty()
            )
        }

        val entries = LinkedHashMap<String, StoryViewerEntry>()
        viewerDocs.forEach { d ->
            val uid = d.getString("userId") ?: d.id
            val reaction = reactionByUser[uid]
            val nm = d.getString("userName").orEmpty().ifBlank { reaction?.second.orEmpty() }
            val pic = d.getString("userPic").orEmpty().ifBlank { reaction?.third.orEmpty() }
            entries[uid] = StoryViewerEntry(
                userId = uid,
                name = nm.ifBlank { "Viewer" },
                emoji = reaction?.first?.takeIf { it.isNotBlank() },
                // A pending server timestamp reads as null on the writer's own
                // device for one snapshot; 0 sorts it to the top as "just now"
                // rather than dropping it.
                viewedAtMillis = d.getTimestamp("viewedAt")?.toDate()?.time ?: 0L,
                pic = pic,
                completed = d.getBoolean("completed") == true
            )
        }
        // Reactors with no viewer doc — the reaction landed and the view marker
        // didn't. Flagged rather than hidden so a stuck write is visible.
        reactionByUser.forEach { (uid, r) ->
            if (!entries.containsKey(uid)) {
                entries[uid] = StoryViewerEntry(
                    userId = uid,
                    name = r.second.ifBlank { "Viewer" },
                    emoji = r.first.takeIf { it.isNotBlank() },
                    viewedAtMillis = 0L,
                    pic = r.third,
                    reactedOnly = true
                )
            }
        }

        return StoryInsights(
            viewCount = story?.viewCount ?: viewerDocs.size,
            reactionCounts = (story?.reactionCounts ?: emptyMap()).filterValues { it > 0 },
            viewers = entries.values.sortedByDescending { it.viewedAtMillis }
        )
    }

    /**
     * Backfill a video story's missing poster — the self-healing path.
     *
     * A video whose `thumbnailUrl` is empty shows a blank tile forever: legacy
     * posts predating poster generation, admin-panel posts (PHP has no frame
     * extractor), and posts where on-device generation failed all land here.
     * The author's own client decodes a frame straight from the uploaded video,
     * uploads it, and patches the doc — so the tile repairs itself the first
     * time the author looks at the story, with no server-side ffmpeg.
     *
     * Only the story's author should call this: the Firestore rule allows staff
     * to update a story in their own school (counters excluded), so anyone
     * COULD, but a single writer avoids N clients racing to upload N posters.
     * Fails silently — a blank tile is a cosmetic problem, not one worth
     * interrupting the author with.
     */
    suspend fun backfillThumbnail(storyId: String): Result<String> {
        return try {
            val fs = FirebaseFirestore.getInstance()
            val storyRef = fs.collection(COLLECTION).document(storyId)
            val snap = storyRef.get().await()
            val type = snap.getString("type").orEmpty()
            if (!type.equals("video", ignoreCase = true)) {
                return Result.failure(IllegalStateException("Not a video story"))
            }
            if (!snap.getString("thumbnailUrl").isNullOrBlank()) {
                return Result.failure(IllegalStateException("Poster already present"))
            }
            val mediaUrl = snap.getString("mediaUrl")?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("No media to derive a poster from"))
            val schoolId = snap.getString("schoolId").orEmpty()
                .ifBlank { return Result.failure(IllegalStateException("Story has no school")) }
            val authorId = snap.getString("authorId").orEmpty()
                .ifBlank { snap.getString("teacherId").orEmpty() }
                .ifBlank { return Result.failure(IllegalStateException("Story has no author")) }

            val posterUrl = com.schoolsync.teacher.util.StoryMediaUploader
                .posterFromRemoteVideo(mediaUrl, schoolId, authorId)
                ?: return Result.failure(IllegalStateException("Couldn't extract a frame"))

            // Field-scoped update: touching ONLY thumbnailUrl keeps this inside
            // the staff-update rule, which rejects any write that affects
            // viewCount / reactionCounts.
            storyRef.update("thumbnailUrl", posterUrl).await()
            Result.success(posterUrl)
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
        // KEY_SCHOOL_ID holds the SAME SCH_… value as the claim, so it is a
        // safe offline fallback.
        //
        // schoolCode is NOT, and used to be the last resort here: it is the
        // login code (e.g. "DPS123"), which can never equal the `school_id`
        // claim the engagement rule compares against. Writing it produced a
        // guaranteed PERMISSION_DENIED that the caller swallowed into a retry
        // queue — a view silently lost forever, with the retry re-sending the
        // same doomed value. Returning blank instead makes the caller fail
        // loudly, which is the honest outcome: we cannot form a write that
        // could possibly be accepted.
        return claim
            ?: tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: ""
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
            ?: run {
                // A blank userId means the session is broken, not that the view
                // is unimportant — say so instead of failing anonymously.
                com.schoolsync.teacher.util.debugLog(
                    "Story.markAsViewed ABORT story=$storyId — blank userId (broken session)"
                )
                return Result.failure(Exception("User ID not available — please re-login"))
            }
        val userName = tokenManager.userName.firstOrNull().orEmpty()
        // Denormalised avatar, same rationale as userName: the author's
        // "who viewed" list can then render a real face without an N-per-row
        // profile lookup. Blank is fine — the row falls back to initials.
        val userPic = tokenManager.profilePic.firstOrNull().orEmpty()
        // C2: stamp the caller's own schoolId so the engagement rule can
        // tenant-bind the write (must equal the token's school_id claim).
        val schoolId = resolveWriteSchoolId()
        // Blank means we could resolve NOTHING the engagement rule would
        // accept. Fail loudly rather than write a value guaranteed to be
        // denied — see resolveWriteSchoolId for why schoolCode is not a
        // legitimate fallback here.
        if (schoolId.isBlank()) {
            com.schoolsync.teacher.util.debugLog(
                "Story.markAsViewed ABORT story=$storyId — no usable schoolId (claim + stored both blank)"
            )
            return Result.failure(Exception("School not available — please re-login"))
        }
        // The exact payload the rule will be evaluated against. If a write is
        // being denied, this line and the rule side-by-side show why.
        com.schoolsync.teacher.util.debugLog(
            "Story.markAsViewed story=$storyId docId=$userId userId=$userId schoolId=$schoolId"
        )
        return try {
            val fs = FirebaseFirestore.getInstance()
            val storyRef  = fs.collection(COLLECTION).document(storyId)
            val viewerRef = storyRef.collection(VIEWERS_SUBCOLLECTION).document(userId)
            fs.runTransaction { tx ->
                val existing = tx.get(viewerRef)
                if (existing.exists()) {
                    // Already counted — never inflate on re-view. Backfill a
                    // blank name/pic if an earlier doc lacked them (also stamp
                    // schoolId so the update satisfies the tenant-bound rule).
                    val patch = mutableMapOf<String, Any?>()
                    if (existing.getString("userName").isNullOrBlank() && userName.isNotBlank()) {
                        patch["userName"] = userName
                    }
                    if (existing.getString("userPic").isNullOrBlank() && userPic.isNotBlank()) {
                        patch["userPic"] = userPic
                    }
                    if (patch.isNotEmpty()) {
                        patch["schoolId"] = schoolId
                        tx.update(viewerRef, patch)
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
                    "userPic"  to userPic,
                    "schoolId" to schoolId
                ))
                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Record that this staff member watched a story ALL THE WAY THROUGH.
     *
     * A "view" is recorded after a 500 ms dwell, which means opened — not
     * watched. Without this, "50 views" can't be told apart from "50 people
     * swiped past it", and the author has no way to know whether anything
     * actually landed. Completion is written when an image's timer runs out or
     * a video reaches STATE_ENDED; tapping to the next story does NOT count.
     *
     * Idempotent: once `completed` is set the transaction is a no-op, so a
     * replayed story never rewrites it.
     *
     * Writes the FULL marker when no viewer doc exists yet (a completion
     * implies a view, and the tenant-bound engagement rule requires userId +
     * schoolId on the resulting document), so this is safe even if the earlier
     * view write was lost.
     *
     * Failures are logged, not queued for retry like views are: a missing view
     * corrupts the author's "who viewed" list, whereas a missing completion
     * only softens an analytics number. Deliberate asymmetry.
     */
    suspend fun markAsCompleted(storyId: String): Result<Unit> {
        val userId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        val userName = tokenManager.userName.firstOrNull().orEmpty()
        val userPic = tokenManager.profilePic.firstOrNull().orEmpty()
        val schoolId = resolveWriteSchoolId()
        if (schoolId.isBlank()) return Result.failure(Exception("School not available"))
        return try {
            val fs = FirebaseFirestore.getInstance()
            val viewerRef = fs.collection(COLLECTION).document(storyId)
                .collection(VIEWERS_SUBCOLLECTION).document(userId)
            fs.runTransaction { tx ->
                val existing = tx.get(viewerRef)
                if (existing.exists()) {
                    if (existing.getBoolean("completed") == true) return@runTransaction null
                    tx.update(viewerRef, mapOf(
                        "completed"   to true,
                        "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "userId"      to userId,
                        "schoolId"    to schoolId
                    ))
                } else {
                    tx.set(viewerRef, hashMapOf<String, Any?>(
                        "viewedAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "completed"   to true,
                        "userId"      to userId,
                        "userName"    to userName,
                        "userPic"     to userPic,
                        "schoolId"    to schoolId
                    ))
                }
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
        /** Video poster URL (empty for images). */
        thumbnailUrl: String = "",
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
            "thumbnailUrl"    to (if (cleanType == "video") thumbnailUrl.trim() else ""),
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
            // Best-effort: purge the Storage media so a manual delete doesn't
            // orphan the image/video/poster in the bucket. The onStoryDeleted
            // Cloud Function is the server-side backstop, but deleting here
            // means cleanup is immediate and still happens even if that CF
            // isn't deployed. Each delete is swallowed on failure — the doc
            // delete below is what actually removes the story; the CF sweeps
            // anything we couldn't reach.
            runCatching {
                val fs = FirebaseFirestore.getInstance()
                val snap = fs.collection(COLLECTION).document(storyId).get().await()
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                listOfNotNull(
                    snap.getString("mediaUrl")?.takeIf { it.isNotBlank() },
                    snap.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }
                ).forEach { url ->
                    runCatching { storage.getReferenceFromUrl(url).delete().await() }
                }
            }
            firestoreService.deleteDocument(COLLECTION, storyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
