package com.schoolsync.teacher.data.model.firestore

/**
 * Single source of truth for Stories feature constants.
 *
 * KEEP IN SYNC with the admin PHP mirror at:
 *   application/controllers/Stories.php  (class-level constants)
 *
 * Any change here requires:
 *   1. Update the matching PHP constant in Stories.php
 *   2. Mirror in the parent app's StorySharedConfig.kt
 *   3. Storage rules (firebase_storage_rules_stories.md) if size caps move
 */
object StorySharedConfig {

    // ── Collection / paths ─────────────────────────────────────────
    const val COLLECTION = "stories"
    const val VIEWERS_SUBCOLLECTION = "viewers"
    /** Per-user reaction docs: stories/{id}/reactions/{userId} = {emoji, reactedAt}. */
    const val REACTIONS_SUBCOLLECTION = "reactions"

    // ── Reactions (v1) ─────────────────────────────────────────────
    /** Fixed emoji palette. Parents react; teacher/admin see aggregate counts. */
    val ALLOWED_REACTIONS = listOf("❤️", "👍", "😍", "👏", "🎉")

    // ── Audience scoping (v1) ──────────────────────────────────────
    // A story carries `audienceClassKeys: List<String>` of CANONICAL
    // class-section tokens. EMPTY = school-wide (back-compat: every
    // pre-v1 doc has no field → empty → visible to all, as before).
    //
    // [audienceKey] reduces ANY representation of a class+section to a
    // single canonical token so the teacher writer and the parent
    // reader match even though the two apps' Constants.classKey differ
    // (teacher adds an ordinal "8"→"Class 8th", parent does not). All
    // of "Class 8th"/"8th"/"8" → "8"; "Section A"/"A" → "a".
    //   ⚠ MUST stay byte-identical in: teacher StorySharedConfig,
    //     parent StorySharedConfig, admin Stories.php.

    /**
     * Whole-school sentinel stored INSIDE audienceClassKeys so the parent's
     * server-side `array-contains-any` query can match school-wide stories
     * without a bypassable client filter (SEC-1). Writers (teacher app +
     * admin panel) emit `[AUDIENCE_ALL]` for a whole-school story instead of
     * an empty list. Legacy empty-list docs remain readable (the rule treats
     * empty == whole-school) but won't match the array query; they expire in
     * 24h. NEVER a valid class token (canonClassToken never yields "*").
     */
    const val AUDIENCE_ALL = "*"

    /**
     * Canonical "is this a whole-school story?" test for READ-side code.
     * A whole-school story is either the legacy empty list (pre-v1 docs) OR
     * carries the [AUDIENCE_ALL] sentinel (current writers emit `["*"]`).
     * Every reader that used to check `audienceClassKeys.isEmpty()` must use
     * this instead, or `["*"]` docs get hidden from teachers / render a raw
     * "*" chip.
     */
    fun isWholeSchool(audienceClassKeys: List<String>): Boolean =
        audienceClassKeys.isEmpty() || AUDIENCE_ALL in audienceClassKeys

    /** Canonical class+section token, e.g. ("Class 8th","Section A") → "8-a". */
    fun audienceKey(className: String, section: String): String =
        "${canonClassToken(className)}-${canonSectionToken(section)}"

    private fun canonClassToken(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.startsWith("class ")) s = s.removePrefix("class ").trim()
        // strip ordinal suffix on a pure-numeric class: "8th" → "8", "12th" → "12"
        Regex("^(\\d+)(st|nd|rd|th)$").find(s)?.let { s = it.groupValues[1] }
        return s
    }

    private fun canonSectionToken(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.startsWith("section ")) s = s.removePrefix("section ").trim()
        return s
    }

    // ── Lifecycle ──────────────────────────────────────────────────
    /** Story lives 24h by default. */
    const val EXPIRY_MILLIS = 86_400_000L

    // ── Content caps ───────────────────────────────────────────────
    const val MAX_CAPTION_LENGTH = 500
    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024   // 10 MB
    const val MAX_VIDEO_BYTES = 50L * 1024 * 1024   // 50 MB
    val ALLOWED_TYPES = setOf("image", "video")

    // ── Rate limits (Hardening #4) ─────────────────────────────────
    /** Max stories a single teacher can upload in a rolling 24h window. */
    const val TEACHER_DAILY_LIMIT = 5
    /** Max stories a single admin user can upload in a rolling 24h window. */
    const val ADMIN_DAILY_LIMIT = 10

    // ── Author types ───────────────────────────────────────────────
    const val AUTHOR_TEACHER = "teacher"
    const val AUTHOR_ADMIN   = "admin"

    // ── Priority ───────────────────────────────────────────────────
    const val PRIORITY_HIGH   = "high"
    const val PRIORITY_NORMAL = "normal"
    val ALLOWED_PRIORITIES = setOf(PRIORITY_HIGH, PRIORITY_NORMAL)

    // ── Moderation statuses ────────────────────────────────────────
    const val STATUS_ACTIVE  = "active"
    const val STATUS_FLAGGED = "flagged"
    const val STATUS_REMOVED = "removed"
}
