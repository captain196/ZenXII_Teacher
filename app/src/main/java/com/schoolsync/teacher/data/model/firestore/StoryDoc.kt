package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Canonical Story document — IDENTICAL across all 3 systems
 * (Teacher app, Parent app, Admin panel). Any field added or renamed
 * here MUST be mirrored in:
 *   - parent  : data/model/firestore/StoryDoc.kt
 *   - admin   : application/controllers/Stories.php (read/write)
 *   - admin   : application/views/stories/index.php (rendering)
 *
 * SOURCE OF TRUTH: Firestore collection `stories`
 * NO RTDB. NO LEGACY PATHS. Single store for all clients.
 *
 * Doc ID pattern: `{schoolId}_{authorId}_{epochMillis}`  e.g.
 *   "SCH_D94FE8F7AD_T0001_1713620938421"
 *
 * Phase C extension — author-agnostic + admin priority:
 *   - authorId / authorName / authorPic  ← canonical (replaces teacher*)
 *   - authorType  : "teacher" | "admin"  (back-compat default "teacher")
 *   - priority    : "high" | "normal"    (admin posts only; default "normal")
 *
 * Legacy teacher* fields are STILL written by uploadStory for back-
 * compat with parent app builds that haven't picked up the new
 * fields yet. Readers prefer authorX, fall back to teacherX.
 *
 * Lifecycle:
 *   1. Teacher uploads → authorType='teacher', status='active',
 *      priority='normal', createdAt=serverTimestamp,
 *      expiresAt=client-millis + 24h
 *   2. Admin uploads via PHP backend → authorType='admin', priority
 *      can be 'high' to pin
 *   3. Parent reads via observeActiveStories (snapshot listener,
 *      filtered by expiresAt > now AND status='active'); sorts admin
 *      stories first by priority, then teacher stories
 *   4. Admin moderates → status='flagged'|'removed' + moderatedAt+By
 *   5. After expiresAt: filtered out client-side; cleanup via
 *      Firestore TTL (Phase E)
 */
data class StoryDoc(
    @DocumentId
    val id: String = "",

    // ── Identity (always populated) ────────────────────────────────
    val schoolId: String = "",

    /** Phase C canonical fields (preferred). */
    val authorId: String = "",
    val authorName: String = "",
    val authorPic: String = "",
    /** "teacher" | "admin" — drives sort + ring color in parent app. */
    val authorType: String = "teacher",

    /** LEGACY aliases — scheduled for removal in **v2.0** (one release
     *  after the canonical `authorX` fields are deployed everywhere).
     *  New writers stopped setting these; keep the fields to parse
     *  docs written before v1.9. Remove the fields, the writes, and
     *  the `effectiveAuthor*` helpers together. */
    @Deprecated("Use authorId. Remove in v2.0.", ReplaceWith("authorId"))
    val teacherId: String = "",
    @Deprecated("Use authorName. Remove in v2.0.", ReplaceWith("authorName"))
    val teacherName: String = "",
    @Deprecated("Use authorPic. Remove in v2.0.", ReplaceWith("authorPic"))
    val teacherPic: String = "",

    // ── Content ────────────────────────────────────────────────────
    val mediaUrl: String = "",
    /** "image" | "video". */
    val type: String = "image",
    /** ≤ 500 chars. */
    val caption: String = "",
    /** "high" | "normal". Admin posts only; teacher posts default to "normal". */
    val priority: String = "normal",

    // ── Lifecycle ──────────────────────────────────────────────────
    val createdAt: Any? = null,
    /**
     * Firestore `Timestamp` of when this story expires. Single
     * canonical expiry field — readers + TTL policy both use this.
     * Typed Any? because Firestore delivers a `com.google.firebase.Timestamp`
     * but legacy docs (pre-v1.9) may carry an epoch-millis `Long`. Use
     * [expiresAtMillis] to coerce to Long. */
    val expiresAtTs: Any? = null,
    /** Legacy Long-millis field from pre-v1.9 docs. REMOVE IN v2.0.
     *  New writers no longer populate this. */
    @Deprecated("Use expiresAtTs. Remove in v2.0.", ReplaceWith("expiresAtTs"))
    val expiresAt: Long = 0,
    val viewCount: Int = 0,

    // ── Audience scoping (v1) ──────────────────────────────────────
    /** Canonical class-section tokens this story targets (see
     *  StorySharedConfig.audienceKey), e.g. ["8-a","8-b"]. EMPTY =
     *  school-wide (every pre-v1 doc → empty → visible to all). Admin
     *  posts are school-wide. Teacher posts default to their own
     *  class-teacher section(s). Parent app filters client-side. */
    val audienceClassKeys: List<String> = emptyList(),

    // ── Reactions (v1) ─────────────────────────────────────────────
    /** Denormalised emoji → count, kept in sync transactionally with
     *  the reactions/{userId} subcollection (mirrors viewCount). Read
     *  by teacher/admin for analytics; written by the parent app. */
    val reactionCounts: Map<String, Int> = emptyMap(),

    // ── Moderation (admin-only writes) ─────────────────────────────
    val status: String = "active",
    val moderatedBy: String = "",
    val moderatedByName: String = "",
    val moderatedAt: Long = 0,
    val moderationReason: String = ""
) {
    /** Resolve effective author fields (canonical with legacy fallback). */
    @Suppress("DEPRECATION")
    val effectiveAuthorId: String get() = authorId.ifBlank { teacherId }
    @Suppress("DEPRECATION")
    val effectiveAuthorName: String get() = authorName.ifBlank { teacherName }
    @Suppress("DEPRECATION")
    val effectiveAuthorPic: String get() = authorPic.ifBlank { teacherPic }

    /**
     * Coerce expiresAtTs (Any?) to epoch millis. Handles:
     *   - com.google.firebase.Timestamp  (current writers)
     *   - Number (legacy Long-millis docs; removed in v2.0)
     *   - null / unknown → 0
     *
     * Fallback: if [expiresAtTs] is absent, use the legacy [expiresAt] Long.
     */
    @Suppress("DEPRECATION")
    val expiresAtMillis: Long get() = when (val ts = expiresAtTs) {
        is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
        is Number -> ts.toLong()
        null -> expiresAt       // fallback to legacy Long field
        else -> expiresAt
    }
}
