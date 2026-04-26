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
