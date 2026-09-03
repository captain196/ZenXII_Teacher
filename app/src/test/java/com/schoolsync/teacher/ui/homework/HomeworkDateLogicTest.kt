package com.schoolsync.teacher.ui.homework

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * JVM unit tests for the pure date helpers in HomeworkTeacherScreen.kt
 * (parseDueInstant / isDuePassed / formatDueDateIST). All inputs are fixed
 * strings; assertions that could depend on the wall clock use far-past /
 * far-future dates so they stay deterministic regardless of when they run.
 *
 * These functions use only java.util / java.text (TimeZone, SimpleDateFormat,
 * Calendar, Date) — no Android framework or Firebase — so they run under a
 * plain JVM test with no Robolectric.
 */
class HomeworkDateLogicTest {

    private val ist: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /** Parse an exact ISO instant (with fractional seconds) for expected-value comparison. */
    private fun iso(s: String): Date {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        return sdf.parse(s)!!
    }

    // ── parseDueInstant ──────────────────────────────────────────────────

    @Test
    fun parseDueInstant_dateOnly_isEndOfDayIST() {
        val d = parseDueInstant("2026-05-06")
        assertNotNull(d)
        // Should be pinned to 23:59:59.999 IST of that calendar day.
        val c = Calendar.getInstance(ist).apply { time = d!! }
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, c.get(Calendar.MONTH))
        assertEquals(6, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, c.get(Calendar.MINUTE))
        assertEquals(59, c.get(Calendar.SECOND))
        assertEquals(999, c.get(Calendar.MILLISECOND))
    }

    @Test
    fun parseDueInstant_dateOnly_matchesExactIstEndOfDayInstant() {
        val d = parseDueInstant("2026-05-06")
        assertEquals(iso("2026-05-06T23:59:59.999+05:30").time, d!!.time)
    }

    @Test
    fun parseDueInstant_timeBearingIstOffset_usedAsWritten() {
        val d = parseDueInstant("2026-05-06T15:30:00+05:30")
        assertEquals(iso("2026-05-06T15:30:00.000+05:30").time, d!!.time)
    }

    @Test
    fun parseDueInstant_boundary_2359Ist() {
        val d = parseDueInstant("2026-05-06T23:59:00+05:30")
        assertEquals(iso("2026-05-06T23:59:00.000+05:30").time, d!!.time)
    }

    @Test
    fun parseDueInstant_zuluUtc_parsedAsUtc() {
        val d = parseDueInstant("2026-05-06T10:00:00Z")
        assertEquals(iso("2026-05-06T10:00:00.000+00:00").time, d!!.time)
    }

    /**
     * Regression for finding #1 (fixed via isLenient=false): a dash-form
     * legacy date "06-05-2026" is now correctly read as 6 May 2026 and pinned
     * to end-of-day IST — strict parsing makes "yyyy-MM-dd" reject it (day
     * 2026 out of range) so it falls through to "dd-MM-yyyy".
     */
    @Test
    fun parseDueInstant_legacyDdMmYyyyDash_isEndOfDayIST() {
        val d = parseDueInstant("06-05-2026")
        assertEquals(iso("2026-05-06T23:59:59.999+05:30").time, d!!.time)
    }

    @Test
    fun parseDueInstant_legacyDdMmYyyySlash_isEndOfDayIST() {
        // Slash form escapes the "yyyy-MM-dd" trap ('/' != '-' literal), so it
        // correctly falls through to "dd/MM/yyyy" and pins to end-of-day IST.
        val d = parseDueInstant("06/05/2026")
        assertEquals(iso("2026-05-06T23:59:59.999+05:30").time, d!!.time)
    }

    @Test
    fun parseDueInstant_blank_returnsNull() {
        assertNull(parseDueInstant(""))
        assertNull(parseDueInstant("   "))
    }

    @Test
    fun parseDueInstant_unparseable_returnsNull() {
        assertNull(parseDueInstant("not-a-date"))
    }

    /**
     * Regression for finding #2 (fixed via isLenient=false): out-of-range
     * fields are now rejected instead of rolling over — "2026-13-40"
     * (month 13, day 40) returns null.
     */
    @Test
    fun parseDueInstant_outOfRangeFields_returnsNull() {
        assertNull(parseDueInstant("2026-13-40"))
    }

    // ── isDuePassed ──────────────────────────────────────────────────────

    @Test
    fun isDuePassed_farPast_isTrue() {
        assertTrue(isDuePassed("2000-01-01"))
    }

    @Test
    fun isDuePassed_farFuture_isFalse() {
        assertFalse(isDuePassed("2999-12-31"))
    }

    @Test
    fun isDuePassed_blankOrUnparseable_isFalse() {
        assertFalse(isDuePassed(""))
        assertFalse(isDuePassed("garbage"))
    }

    // ── formatDueDateIST ─────────────────────────────────────────────────

    // The blank case now resolves a string resource, so it moved to the
    // Context-taking overload and is out of scope for a JVM test. What matters
    // here — parsing and IST end-of-day — is on the pure overload below.
    @Test
    fun formatDueDateIST_blank_returnsEmptyFromPureOverload() {
        assertEquals("", formatDueDateIST(""))
    }

    @Test
    fun formatDueDateIST_unparseable_returnsRaw() {
        assertEquals("total-garbage", formatDueDateIST("total-garbage"))
    }

    /**
     * Regression for finding #3 (fixed): formatDueDateIST() now parses via
     * parseDueInstant(), so a date-only value is pinned to 23:59:59 IST
     * end-of-day and rendered as "11:59 PM IST" (previously it parsed at
     * midnight in the default TZ). diff>6 => long "dd MMM yyyy" branch.
     */
    @Test
    fun formatDueDateIST_farFutureDateOnly_longFormatEndOfDayIST() {
        val out = formatDueDateIST("2999-12-31")
        assertTrue(out, out.startsWith("Due 31 Dec 2999"))
        assertTrue(out, out.contains("11:59 PM IST"))
    }

    @Test
    fun formatDueDateIST_convertsZuluTimeToIST() {
        // 09:00 UTC == 14:30 IST.
        val out = formatDueDateIST("2999-06-15T09:00:00Z")
        assertTrue(out, out.contains("2:30 PM IST"))
        assertTrue(out, out.contains("15 Jun 2999"))
    }

    @Test
    fun formatDueDateIST_keepsIstOffsetTimeAsWritten() {
        val out = formatDueDateIST("2999-06-15T09:00:00+05:30")
        assertTrue(out, out.contains("9:00 AM IST"))
        assertTrue(out, out.contains("15 Jun 2999"))
    }
}
