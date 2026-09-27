package com.schoolsync.teacher.ui.payslips

import com.schoolsync.teacher.data.model.firestore.SalarySlipDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fractional payroll day counts must survive all the way to what a teacher reads.
 *
 * The admin panel produces half-days three independent ways — attendance mark
 * 'M', half-day leave, and a school-declared Calendar Half-Day — so workingDays
 * can be 21.5, daysAbsent 0.25, and lwpDays 0.5.
 *
 * These were previously Int on the model. Firestore's CustomClassMapper converts
 * a stored double into an Int field via Number.intValue() SILENTLY, with no
 * exception, so 21.5 arrived as 21 and 0.5 arrived as 0. The payslip screen then
 * gated the LWP row on `> 0`, so a real half-day loss of pay disappeared from the
 * teacher's slip while their net pay was still reduced — nothing surfaced it.
 *
 * This test executes the real formatter and the real gate conditions on the real
 * model, on the JVM. It does not need a device, an emulator, or credentials.
 */
class PayslipDayFormattingTest {

    // ── formatDays(): whole numbers stay clean, fractions stay intact ──

    @Test
    fun `whole day counts render without a decimal point`() {
        assertEquals("22", formatDays(22.0))
        assertEquals("26", formatDays(26.0))
        assertEquals("0", formatDays(0.0))
    }

    @Test
    fun `calendar half-day working count keeps its half`() {
        assertEquals("21.5", formatDays(21.5))
        assertEquals("25.5", formatDays(25.5))
    }

    @Test
    fun `quarter days survive - attendance half-day on a calendar half-day`() {
        // S1: 'M' on a Calendar Half-Day covers half of that day's REDUCED
        // expectation, so the uncovered remainder is 0.25.
        assertEquals("25.25", formatDays(25.25))
        assertEquals("0.25", formatDays(0.25))
    }

    @Test
    fun `half and one-and-a-half day LWP both render exactly`() {
        assertEquals("0.5", formatDays(0.5))
        assertEquals("1.5", formatDays(1.5))
    }

    @Test
    fun `formatter never rounds a fraction away`() {
        for (v in listOf(0.25, 0.5, 0.75, 1.5, 21.5, 25.25)) {
            assertTrue(
                "formatDays($v) must keep a decimal point, got '${formatDays(v)}'",
                formatDays(v).contains('.')
            )
        }
    }

    // ── The model must actually hold fractions ──

    @Test
    fun `model carries fractional day counts unchanged`() {
        val slip = SalarySlipDoc(
            workingDays = 25.5,
            presentDays = 25.25,
            daysAbsent = 0.25,
            lwpDays = 0.5,
            paidLeaveDays = 0.5,
            netPayable = 43568.62
        )

        assertEquals(25.5, slip.workingDays, 0.0001)
        assertEquals(25.25, slip.presentDays, 0.0001)
        assertEquals(0.25, slip.daysAbsent, 0.0001)
        assertEquals(0.5, slip.lwpDays, 0.0001)
        assertEquals(0.5, slip.paidLeaveDays, 0.0001)
    }

    @Test
    fun `defaults are zero and render as zero`() {
        val slip = SalarySlipDoc()
        assertEquals("0", formatDays(slip.workingDays))
        assertEquals("0", formatDays(slip.lwpDays))
    }

    // ── The screen's row gates: the exact condition that hid the defect ──

    @Test
    fun `a half-day LWP is visible to the teacher`() {
        val slip = SalarySlipDoc(lwpDays = 0.5)

        // PayslipsScreen: `if (slip.lwpDays > 0.0) InfoRow("LWP days", ...)`.
        // Under the old Int model this value was 0 and the row vanished while
        // net pay was still reduced.
        assertTrue("a 0.5-day LWP must not be hidden", slip.lwpDays > 0.0)
        assertEquals("0.5", formatDays(slip.lwpDays))
    }

    @Test
    fun `a half-day of paid leave is visible to the teacher`() {
        // S2: a full-day leave on a Calendar Half-Day consumes only 0.5.
        val slip = SalarySlipDoc(paidLeaveDays = 0.5)
        assertTrue(slip.paidLeaveDays > 0.0)
        assertEquals("0.5", formatDays(slip.paidLeaveDays))
    }

    @Test
    fun `rows stay hidden when there genuinely is nothing to show`() {
        val slip = SalarySlipDoc(lwpDays = 0.0, paidLeaveDays = 0.0)
        assertFalse(slip.lwpDays > 0.0)
        assertFalse(slip.paidLeaveDays > 0.0)
    }

    // ── The full Calendar Half-Day slip, end to end at the display layer ──

    @Test
    fun `calendar half-day slip renders the way the server computed it`() {
        // Values taken from the live UAT run against the designated test school:
        // expected 25.5, 'M' on the half-day leaves 0.25 uncovered.
        val slip = SalarySlipDoc(
            workingDays = 25.5,
            presentDays = 25.25,
            daysAbsent = 0.25,
            netPayable = 43568.62
        )

        assertEquals("25.5", formatDays(slip.workingDays))
        assertEquals("25.25", formatDays(slip.presentDays))
        assertEquals("0.25", formatDays(slip.daysAbsent))

        // Covered + uncovered must reconstruct the expectation.
        assertEquals(slip.workingDays, slip.presentDays + slip.daysAbsent, 0.0001)
    }

    @Test
    fun `full expectation met on a calendar half-day shows no absence`() {
        // The load-bearing rule: reduced expected work, FULL pay.
        val slip = SalarySlipDoc(
            workingDays = 25.5,
            presentDays = 25.5,
            daysAbsent = 0.0,
            netPayable = 44000.0
        )

        assertEquals("25.5", formatDays(slip.workingDays))
        assertEquals("0", formatDays(slip.daysAbsent))
        assertEquals(44000.0, slip.netPayable, 0.0001)
    }
}
