package com.schoolsync.teacher.ui.stories

/**
 * Whether a story view should be recorded — and when it shouldn't, WHY.
 *
 * Extracted as a pure function for two reasons. It is the decision that
 * silently produced "0 views forever" (a self-view is skipped by design, but
 * nothing said so), and it had no test coverage at all: the logic was inline
 * in a ViewModel behind Firebase, so the only way to exercise it was on a
 * device with two accounts.
 *
 * Returning a REASON rather than a Boolean is deliberate — the caller logs it,
 * which is what turns "no viewer doc appeared" from a mystery into a one-line
 * answer.
 */
enum class ViewRecordDecision {
    /** Write the viewer doc. */
    RECORD,
    /** The author opened their own story. Instagram and WhatsApp both exclude
     *  these, so we do too — it is not a bug, but it IS the usual reason a
     *  count looks stuck while you're testing on one account. */
    SKIP_SELF_VIEW,
    /** Already counted for this user; re-viewing never inflates the number. */
    SKIP_ALREADY_SEEN,
    /** Missing story or user identity — nothing coherent to write. */
    SKIP_INVALID
}

object StoryViewPolicy {

    /**
     * @param storyId       the story being viewed
     * @param authorId      who posted it (blank when unknown)
     * @param currentUserId the viewer
     * @param alreadySeen   this user already has a viewer doc / local seen mark
     */
    fun decide(
        storyId: String,
        authorId: String,
        currentUserId: String,
        alreadySeen: Boolean
    ): ViewRecordDecision = when {
        storyId.isBlank() || currentUserId.isBlank() -> ViewRecordDecision.SKIP_INVALID
        alreadySeen -> ViewRecordDecision.SKIP_ALREADY_SEEN
        // Blank authorId means we can't prove it ISN'T self — but a story
        // always has an author, so treat blank as "not me" and record. Failing
        // open here loses nothing: the server transaction is still idempotent.
        authorId.isNotBlank() && authorId == currentUserId -> ViewRecordDecision.SKIP_SELF_VIEW
        else -> ViewRecordDecision.RECORD
    }
}
