package com.schoolsync.teacher.ui.marks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure marks helpers extracted into MarksLogic.kt. These
 * touch no Android framework or Firebase, so they run under a plain JVM test.
 */
class MarksLogicTest {

    // ── formatMark ───────────────────────────────────────────────────────

    @Test
    fun formatMark_wholeNumber_dropsTrailingZero() {
        assertEquals("7", formatMark(7.0))
    }

    @Test
    fun formatMark_fractional_keepsDecimals() {
        assertEquals("7.5", formatMark(7.5))
    }

    @Test
    fun formatMark_zero_isZero() {
        assertEquals("0", formatMark(0.0))
    }

    @Test
    fun formatMark_largeWhole_dropsTrailingZero() {
        assertEquals("100", formatMark(100.0))
    }

    // ── isValidMarkInput ─────────────────────────────────────────────────

    @Test
    fun isValidMarkInput_acceptsEmpty() {
        assertTrue(isValidMarkInput(""))
    }

    @Test
    fun isValidMarkInput_acceptsInteger() {
        assertTrue(isValidMarkInput("40"))
    }

    @Test
    fun isValidMarkInput_acceptsDecimal() {
        assertTrue(isValidMarkInput("37.5"))
    }

    @Test
    fun isValidMarkInput_rejectsTwoDots() {
        assertFalse(isValidMarkInput("4.5.6"))
    }

    @Test
    fun isValidMarkInput_rejectsNonNumeric() {
        assertFalse(isValidMarkInput("12x"))
    }

    @Test
    fun isValidMarkInput_rejectsTooLong() {
        assertFalse(isValidMarkInput("123456"))
    }

    // ── buildComponentMarks ──────────────────────────────────────────────

    @Test
    fun buildComponentMarks_theoryOnly_whenNoPractical() {
        val components = buildComponentMarks(theory = 80.0, practical = 0.0, hasPractical = false)
        assertEquals(1, components.size)
        assertEquals("Theory", components[0]["name"])
        assertEquals(80.0, components[0]["value"])
    }

    @Test
    fun buildComponentMarks_theoryAndPractical_whenHasPractical() {
        val components = buildComponentMarks(theory = 60.0, practical = 18.0, hasPractical = true)
        assertEquals(2, components.size)
        assertEquals("Theory", components[0]["name"])
        assertEquals(60.0, components[0]["value"])
        assertEquals("Practical", components[1]["name"])
        assertEquals(18.0, components[1]["value"])
    }

    // ── exceedsMaxTotal ──────────────────────────────────────────────────

    @Test
    fun exceedsMaxTotal_overCap_isTrue() {
        assertTrue(exceedsMaxTotal(total = 105.0, maxTotal = 100, isAbsent = false))
    }

    @Test
    fun exceedsMaxTotal_atCap_isFalse() {
        assertFalse(exceedsMaxTotal(total = 100.0, maxTotal = 100, isAbsent = false))
    }

    @Test
    fun exceedsMaxTotal_underCap_isFalse() {
        assertFalse(exceedsMaxTotal(total = 50.0, maxTotal = 100, isAbsent = false))
    }

    @Test
    fun exceedsMaxTotal_nullTotal_isFalse() {
        assertFalse(exceedsMaxTotal(total = null, maxTotal = 100, isAbsent = false))
    }

    @Test
    fun exceedsMaxTotal_absentIgnoresOverCap_isFalse() {
        // An absent student has no numeric total to validate — never flagged.
        assertFalse(exceedsMaxTotal(total = 105.0, maxTotal = 100, isAbsent = true))
    }
}
