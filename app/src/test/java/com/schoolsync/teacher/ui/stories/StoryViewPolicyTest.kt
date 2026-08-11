package com.schoolsync.teacher.ui.stories

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the "should this view be recorded?" decision.
 *
 * This is the logic that produced "0 views forever" and had no coverage at
 * all — it lived inline in a ViewModel behind Firebase, so the only way to
 * exercise it was on a device with two accounts. The self-view case in
 * particular is easy to mistake for a bug, so it is pinned here.
 */
class StoryViewPolicyTest {

    private val ME = "STA0011"
    private val SOMEONE_ELSE = "STA0067"
    private val STORY = "SCH_X_STA0011_123"

    @Test
    fun otherPersonsStory_isRecorded() {
        assertEquals(
            ViewRecordDecision.RECORD,
            StoryViewPolicy.decide(STORY, authorId = SOMEONE_ELSE, currentUserId = ME, alreadySeen = false)
        )
    }

    @Test
    fun ownStory_isSkippedAsSelfView() {
        // Instagram and WhatsApp both exclude the author's own views. This is
        // the single most common reason a count looks stuck during testing.
        assertEquals(
            ViewRecordDecision.SKIP_SELF_VIEW,
            StoryViewPolicy.decide(STORY, authorId = ME, currentUserId = ME, alreadySeen = false)
        )
    }

    @Test
    fun alreadySeen_isSkipped_soReViewingNeverInflatesTheCount() {
        assertEquals(
            ViewRecordDecision.SKIP_ALREADY_SEEN,
            StoryViewPolicy.decide(STORY, authorId = SOMEONE_ELSE, currentUserId = ME, alreadySeen = true)
        )
    }

    @Test
    fun alreadySeenWins_overSelfView() {
        // Ordering matters only for the LOG's accuracy, but a wrong reason
        // sends the next person debugging in the wrong direction.
        assertEquals(
            ViewRecordDecision.SKIP_ALREADY_SEEN,
            StoryViewPolicy.decide(STORY, authorId = ME, currentUserId = ME, alreadySeen = true)
        )
    }

    @Test
    fun blankStoryId_isInvalid() {
        assertEquals(
            ViewRecordDecision.SKIP_INVALID,
            StoryViewPolicy.decide("", authorId = SOMEONE_ELSE, currentUserId = ME, alreadySeen = false)
        )
    }

    @Test
    fun blankViewer_isInvalid_ratherThanWritingAnAnonymousView() {
        // A blank userId means the session is broken; writing would produce a
        // doc the tenant rule rejects anyway.
        assertEquals(
            ViewRecordDecision.SKIP_INVALID,
            StoryViewPolicy.decide(STORY, authorId = SOMEONE_ELSE, currentUserId = "", alreadySeen = false)
        )
    }

    @Test
    fun blankAuthor_stillRecords() {
        // We can't prove it isn't a self-view, but a story always has an
        // author and the server transaction is idempotent — so failing open
        // costs nothing and avoids silently dropping a real view.
        assertEquals(
            ViewRecordDecision.RECORD,
            StoryViewPolicy.decide(STORY, authorId = "", currentUserId = ME, alreadySeen = false)
        )
    }

    @Test
    fun parentViewingATeachersStory_isRecorded() {
        // The exact case that was reported broken: a parent opening a staff
        // member's story must always count.
        assertEquals(
            ViewRecordDecision.RECORD,
            StoryViewPolicy.decide(STORY, authorId = "STA0011", currentUserId = "STU0012", alreadySeen = false)
        )
    }
}
