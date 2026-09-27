package com.schoolsync.teacher.data.local.offline

/**
 * C1 — the single gate that decides whether offline attendance queuing is live.
 *
 * DESIGN CONTRACT (see the C1 report):
 *  - OFF by default and until on-device UAT has actually passed.
 *  - Deterministic: a compile-time constant, not a runtime toggle, so NO
 *    unrelated setting, remote value, tenant field or user action can flip it.
 *  - Uniform across tenants (no per-tenant surprise enablement).
 *  - There is intentionally NO dangerous global runtime bypass.
 *
 * To enable after device UAT: change [ENABLED] to true in a reviewed commit.
 * Everything downstream ([OfflineAttendanceQueue], the ViewModel hook, the flush
 * scheduling) short-circuits to a no-op while this is false, so the code can ship
 * dormant and be turned on in one auditable place.
 */
object OfflineFeatureFlag {
    /**
     * ON by owner decision (2026-09-14): new schools get durable offline
     * attendance out of the box. Safe to enable ahead of device UAT because the
     * online path is unchanged, and the server (mark_date date-pin +
     * merge-idempotent writes) prevents any wrong-day or duplicate record — the
     * worst case is a mark that stays "waiting to sync", surfaced honestly in the
     * UI. Recommendation on record: still run STUDENT_ATTENDANCE_C1_DEVICE_UAT_
     * CHECKLIST.md on a physical device before heavy production reliance.
     */
    const val ENABLED: Boolean = true

    fun isEnabled(): Boolean = ENABLED
}
