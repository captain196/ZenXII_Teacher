package com.schoolsync.teacher.util

import android.content.Context

/**
 * Durable queue of story views whose Firestore write did NOT land.
 *
 * Why this exists: marking a story seen used to be fire-and-forget —
 * `seenIds` was updated optimistically and the write's Result discarded.
 * If that write failed (offline, expired token, transient PERMISSION_DENIED
 * before a claims refresh), the story was already in the local seen set, so
 * the `alreadySeen` short-circuit meant it was NEVER retried. The view was
 * lost permanently and the author's "who viewed" list silently under-counted
 * that person for the life of the story.
 *
 * Now a failed view is parked here and retried on the next viewer-VM start
 * (i.e. every time stories are opened) until it succeeds. Entries are keyed
 * by storyId; stories expire in 24h so the queue is self-limiting, but
 * [prune] caps it anyway so a permanently-failing id can't grow it unbounded.
 *
 * SharedPreferences (not DataStore) deliberately: this is a tiny string set
 * read once per VM construction, and it must be writable from a failure path
 * that may be racing app teardown.
 */
object PendingStoryViews {

    private const val PREFS = "story_pending_views"
    private const val KEY = "ids"
    /** Hard cap — oldest entries drop first. */
    private const val MAX_ENTRIES = 200

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Park a story id whose viewer-doc write failed. */
    @Synchronized
    fun add(context: Context, storyId: String) {
        if (storyId.isBlank()) return
        val current = read(context)
        if (storyId in current) return
        // LinkedHashSet keeps insertion order so prune drops the oldest.
        val next = LinkedHashSet(current).apply { add(storyId) }
        write(context, prune(next))
    }

    /** Drop a story id once its write finally lands. */
    @Synchronized
    fun remove(context: Context, storyId: String) {
        if (storyId.isBlank()) return
        val current = read(context)
        if (storyId !in current) return
        write(context, LinkedHashSet(current).apply { remove(storyId) })
    }

    /** Everything still owed a write, oldest first. */
    @Synchronized
    fun all(context: Context): List<String> = read(context).toList()

    private fun read(context: Context): Set<String> =
        // getStringSet returns an unmodifiable set that must never be mutated
        // in place (documented Android footgun) — copy it out immediately.
        LinkedHashSet(prefs(context).getStringSet(KEY, emptySet()).orEmpty())

    private fun write(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY, LinkedHashSet(ids)).apply()
    }

    private fun prune(ids: LinkedHashSet<String>): LinkedHashSet<String> {
        if (ids.size <= MAX_ENTRIES) return ids
        val it = ids.iterator()
        var toDrop = ids.size - MAX_ENTRIES
        while (it.hasNext() && toDrop > 0) { it.next(); it.remove(); toDrop-- }
        return ids
    }
}
