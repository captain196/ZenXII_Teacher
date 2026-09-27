package com.schoolsync.teacher.ui.lessonplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHAT THE EDIT SHEET SAYS WHEN THERE IS NO TOPIC TO SHOW.
 *
 * The topic list is read from the curriculum the admin panel maintains. That
 * read can fail, and the repository used to answer a failure with an empty
 * list — so the sheet said "No topics found in curriculum", which is a claim
 * about the school's syllabus rather than about the read that just failed.
 *
 * These are the two lines a teacher actually sees, and they must never be the
 * same line for those two different situations.
 */
class TopicPlaceholderTest {

    @Test
    fun loadingSaysSo() {
        assertEquals("Loading topics…", topicFieldPlaceholder(loading = true, error = null))
    }

    @Test
    fun loadingWinsOverAStaleError() {
        assertEquals("Loading topics…", topicFieldPlaceholder(loading = true, error = "boom"))
    }

    @Test
    fun nothingChosenIsNotAFailure() {
        assertEquals("— No topic —", topicFieldPlaceholder(loading = false, error = null))
    }

    @Test
    fun aFailedReadIsNotNothingChosen() {
        val shown = topicFieldPlaceholder(loading = false, error = "PERMISSION_DENIED")
        assertEquals("Topics unavailable", shown)
        assertTrue("a failure must not read as an ordinary empty selection",
            shown != topicFieldPlaceholder(loading = false, error = null))
    }

    @Test
    fun anEmptySyllabusIsReportedAsEmpty() {
        assertEquals("No topics found in curriculum", topicMenuMessage(null))
    }

    @Test
    fun anUnreadableSyllabusIsNotReportedAsEmpty() {
        val msg = topicMenuMessage("UNAVAILABLE")
        assertTrue("the message must not assert the curriculum is empty",
            !msg.contains("No topics found"))
        assertTrue("and it should say the read can be retried",
            msg.contains("try again"))
    }

    @Test
    fun theTwoMenuMessagesAreDistinct() {
        assertTrue(topicMenuMessage(null) != topicMenuMessage("UNAVAILABLE"))
    }
}
