package com.schoolsync.teacher.ui.stories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the segmented story ring's pure geometry. No Compose, no
 * Android — the maths was extracted out of the Composable precisely so the
 * awkward cases (1 story, 30 stories, a degenerate radius) can be asserted
 * here instead of eyeballed on a device.
 */
class StoryRingGeometryTest {

    /** Typical ring at 62dp / 2.5dp stroke on an xxhdpi device (3×). */
    private val radiusPx = 89.25f    // ((62 - 2.5) / 2) * 3
    private val strokePx = 7.5f      // 2.5 * 3

    // ── Degenerate + single-story cases ──────────────────────────────────

    @Test
    fun zeroStories_drawsOneUnbrokenCircle() {
        val arcs = StoryRingGeometry.arcs(0, radiusPx, strokePx)
        assertEquals(1, arcs.size)
        assertEquals(360f, arcs[0].sweepAngle, 0.001f)
    }

    @Test
    fun oneStory_drawsOneUnbrokenCircle_withNoGap() {
        // WhatsApp draws no gap for a single status; one arc with one gap
        // reads as a broken ring rather than as a single story.
        val arcs = StoryRingGeometry.arcs(1, radiusPx, strokePx)
        assertEquals(1, arcs.size)
        assertEquals(0f, arcs[0].startAngle, 0.001f)
        assertEquals(360f, arcs[0].sweepAngle, 0.001f)
    }

    @Test
    fun singleStory_doesNotUseRoundCaps() {
        // A full circle has no ends to round, and rounding them would make the
        // seam visible at 3 o'clock.
        assertFalse(StoryRingGeometry.useRoundCaps(0))
        assertFalse(StoryRingGeometry.useRoundCaps(1))
    }

    // ── Multi-story layout ───────────────────────────────────────────────

    @Test
    fun threeStories_produceThreeArcs() {
        assertEquals(3, StoryRingGeometry.arcs(3, radiusPx, strokePx).size)
    }

    @Test
    fun firstArcStartsAtTwelveOClock() {
        // -90° is 12 o'clock in drawArc's convention. The first arc must begin
        // there (plus half a gap) so the ring reads as starting at the top.
        val arcs = StoryRingGeometry.arcs(3, radiusPx, strokePx)
        assertTrue(
            "first arc should start just after -90°, was ${arcs[0].startAngle}",
            arcs[0].startAngle > -90f && arcs[0].startAngle < -80f
        )
    }

    @Test
    fun arcsAreEvenlySpacedAroundTheCircle() {
        val n = 5
        val arcs = StoryRingGeometry.arcs(n, radiusPx, strokePx)
        val expectedStep = 360f / n
        for (i in 1 until n) {
            assertEquals(
                "arc $i should sit one segment past arc ${i - 1}",
                expectedStep,
                arcs[i].startAngle - arcs[i - 1].startAngle,
                0.01f
            )
        }
    }

    @Test
    fun allArcsShareTheSameSweep() {
        val arcs = StoryRingGeometry.arcs(4, radiusPx, strokePx)
        arcs.forEach { assertEquals(arcs[0].sweepAngle, it.sweepAngle, 0.001f) }
    }

    @Test
    fun arcsNeverOverlap() {
        // Each arc must end before the next begins, or the gaps disappear and
        // the ring silently collapses back into the unbroken circle this whole
        // feature exists to replace.
        for (n in 2..30) {
            val arcs = StoryRingGeometry.arcs(n, radiusPx, strokePx)
            for (i in 1 until n) {
                val prevEnd = arcs[i - 1].startAngle + arcs[i - 1].sweepAngle
                assertTrue(
                    "n=$n: arc $i starts at ${arcs[i].startAngle} but arc ${i - 1} ends at $prevEnd",
                    prevEnd <= arcs[i].startAngle + 0.001f
                )
            }
        }
    }

    @Test
    fun arcsStayVisibleEvenAtHighStoryCounts() {
        // An admin isn't bound by the 5/day teacher cap, so the ring has to
        // survive counts the UI was never designed around.
        for (n in 2..40) {
            val arcs = StoryRingGeometry.arcs(n, radiusPx, strokePx)
            arcs.forEach {
                assertTrue(
                    "n=$n produced a sweep of ${it.sweepAngle}",
                    it.sweepAngle >= StoryRingGeometry.MIN_SWEEP_DEGREES
                )
            }
        }
    }

    @Test
    fun totalInkNeverExceedsTheCircle() {
        for (n in 2..30) {
            val arcs = StoryRingGeometry.arcs(n, radiusPx, strokePx)
            val total = arcs.sumOf { it.sweepAngle.toDouble() }
            assertTrue("n=$n drew $total° of arc", total <= 360.0 + 0.01)
        }
    }

    @Test
    fun gapNeverEatsMoreThanItsShareOfASegment() {
        // At high counts the fixed 7° gap would swallow the segment; it must
        // shrink instead so arcs never degrade into dots.
        val n = 30
        val segment = 360f / n
        val arcs = StoryRingGeometry.arcs(n, radiusPx, strokePx)
        val gapPlusCaps = segment - arcs[0].sweepAngle
        assertTrue(
            "gap+caps of $gapPlusCaps° ate too much of a $segment° segment",
            gapPlusCaps <= segment * StoryRingGeometry.MAX_GAP_SHARE + 2f * 3f
        )
    }

    // ── Cap selection ────────────────────────────────────────────────────

    @Test
    fun roundCapsUsedOnlyWhileArcsAreWideEnough() {
        assertTrue(StoryRingGeometry.useRoundCaps(2))
        assertTrue(StoryRingGeometry.useRoundCaps(StoryRingGeometry.ROUND_CAP_MAX_SEGMENTS))
        // Past the threshold the cap overhang would close the gaps entirely.
        assertFalse(StoryRingGeometry.useRoundCaps(StoryRingGeometry.ROUND_CAP_MAX_SEGMENTS + 1))
    }

    // ── Robustness ───────────────────────────────────────────────────────

    @Test
    fun zeroRadiusDoesNotProduceNaN() {
        // A ring measured at zero (an unlaid-out slot) must not emit NaN
        // angles into drawArc, which would throw at draw time.
        val arcs = StoryRingGeometry.arcs(3, radiusPx = 0f, strokePx = strokePx)
        arcs.forEach {
            assertFalse("startAngle was NaN", it.startAngle.isNaN())
            assertFalse("sweepAngle was NaN", it.sweepAngle.isNaN())
        }
    }

    @Test
    fun negativeSegmentCountIsTreatedAsEmpty() {
        val arcs = StoryRingGeometry.arcs(-3, radiusPx, strokePx)
        assertEquals(1, arcs.size)
        assertEquals(360f, arcs[0].sweepAngle, 0.001f)
    }
}
