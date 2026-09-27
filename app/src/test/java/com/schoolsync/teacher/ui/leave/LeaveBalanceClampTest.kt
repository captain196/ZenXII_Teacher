package com.schoolsync.teacher.ui.leave

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ESS leave balance must never render a negative "remaining".
 *
 * FOUND 2026-08-21 during the Staff Lifecycle cross-platform sweep.
 *
 * The admin server (Hr.php) stores the balance clamped:
 *
 *     'balance' => max(0.0, $liveAlloc + $carried - $newUsed)
 *
 * The Teacher app computed `total - used` UNCLAMPED, in two places: the
 * LeaveBalance data-class default, and the balance-mapping in
 * LeaveViewModel.loadBalances(). Both now route through remainingLeave().
 *
 * This is reachable rather than theoretical. `used` accrues for UNPAID leave
 * types too — the approval path records "Unpaid type — ALL days are LWP, but
 * still tracked in `used`" — while such a type may carry little or no
 * allocation. Once `used` passes `allocated + carried`, the app showed a
 * negative remaining while the stored balance and the admin panel both showed
 * 0. Same tenant, same employee, two contradictory numbers.
 *
 * Clamping is the server's established semantic, so the app follows it rather
 * than inventing a third interpretation. `used` is unaffected and still carries
 * the truth about over-consumption; only the derived display value is clamped —
 * exactly as on the server.
 */
class LeaveBalanceClampTest {

    private val delta = 0.0001

    // ── The clamp ────────────────────────────────────────────────────────

    @Test
    fun `ordinary balance is unaffected`() {
        assertEquals(7.0, remainingLeave(total = 12.0, used = 5.0), delta)
    }

    @Test
    fun `exactly exhausted is zero`() {
        assertEquals(0.0, remainingLeave(total = 12.0, used = 12.0), delta)
    }

    /** THE DEFECT. Over-consumption must render 0, never a negative. */
    @Test
    fun `over-consumption clamps to zero instead of going negative`() {
        assertEquals(0.0, remainingLeave(total = 0.0, used = 5.0), delta)
        assertEquals(0.0, remainingLeave(total = 2.0, used = 7.5), delta)
    }

    /** The unpaid-leave shape that makes this reachable. */
    @Test
    fun `unpaid leave type with no allocation still shows zero`() {
        // allocated = 0, carried = 0, used grows with every LWP day taken.
        assertEquals(0.0, remainingLeave(total = 0.0, used = 3.0), delta)
    }

    // ── Fractions survive the clamp ──────────────────────────────────────

    @Test
    fun `half day balances are preserved, not rounded`() {
        assertEquals(0.5, remainingLeave(total = 1.0, used = 0.5), delta)
        assertEquals(2.5, remainingLeave(total = 12.0, used = 9.5), delta)
    }

    @Test
    fun `a quarter day survives`() {
        assertEquals(0.25, remainingLeave(total = 1.0, used = 0.75), delta)
    }

    @Test
    fun `clamping never rounds a small positive down to zero`() {
        assertEquals(0.5, remainingLeave(total = 5.5, used = 5.0), delta)
    }

    // ── Carried-forward days count toward total ──────────────────────────

    @Test
    fun `carried forward days are already inside total`() {
        // total = allocated + carried, computed by the caller.
        val allocated = 12.0
        val carried = 3.5
        assertEquals(10.5, remainingLeave(total = allocated + carried, used = 5.0), delta)
    }

    // ── The data class default uses the same rule ────────────────────────

    @Test
    fun `data class default is clamped too`() {
        val bal = LeaveBalance(type = "Loss of Pay", used = 4.0, total = 0.0)

        assertEquals(0.0, bal.remaining, delta)
    }

    @Test
    fun `data class default matches the helper exactly`() {
        val bal = LeaveBalance(type = "Casual", used = 5.5, total = 12.0)

        assertEquals(remainingLeave(12.0, 5.5), bal.remaining, delta)
        assertEquals(6.5, bal.remaining, delta)
    }

    /** `used` itself must NOT be clamped — it carries the over-consumption truth. */
    @Test
    fun `used is reported unclamped so over-consumption stays visible`() {
        val bal = LeaveBalance(type = "Loss of Pay", used = 9.0, total = 2.0)

        assertEquals(9.0, bal.used, delta)
        assertEquals(0.0, bal.remaining, delta)
    }

    @Test
    fun `zero balance with zero used is zero`() {
        assertEquals(0.0, remainingLeave(total = 0.0, used = 0.0), delta)
    }
}
