package com.schoolsync.teacher.data.local.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * C1 — the safety contract of the offline flush, proven on the plain JVM.
 *
 * These tests pin fail-closed mapping, the session-pin and the date-pin so a
 * future edit cannot quietly turn a transport failure into a delete, backdate a
 * stale mark, or resurrect a terminal one.
 */
class FlushDecisionTest {

    private fun row(
        session: String = "2026-27",
        dateIso: String = "2026-09-14",
        state: String = QueueState.PENDING
    ) = PendingMark(
        id = "11111111-1111-4111-8111-111111111111",
        schoolId = "SCH_X",
        session = session,
        className = "Class 1st",
        section = "A",
        dateIso = dateIso,
        createdAt = 1_000L,
        state = state
    )

    // ── mapResponse: only 200 + success deletes ──────────────────────────────

    @Test fun success_deletes() {
        val o = FlushDecision.mapResponse(200, true, "success", null, 0)
        assertEquals(FlushDecision.Action.DELETE, o.action)
    }

    // OFFLINE-PARTIAL-1: a 200+success with partial:true must RETRY, never delete.
    @Test fun partial_success_retries_not_deletes() {
        val o = FlushDecision.mapResponse(200, true, "success", null, 0, isPartial = true)
        assertEquals(FlushDecision.Action.RETRY, o.action)
    }

    @Test fun partial_success_parks_terminal_after_cap() {
        val o = FlushDecision.mapResponse(200, true, "success", null, FlushDecision.MAX_ATTEMPTS - 1, isPartial = true)
        assertEquals(FlushDecision.Action.TERMINAL, o.action)
    }

    // OFFLINE-REAUTH-CAP-1: 401 reauths, but caps out to TERMINAL (no infinite spin).
    @Test fun unauthorized_reauths_below_cap() {
        assertEquals(FlushDecision.Action.REAUTH,
            FlushDecision.mapResponse(401, false, null, null, 0).action)
    }

    @Test fun unauthorized_parks_terminal_at_cap() {
        val o = FlushDecision.mapResponse(401, false, null, null, FlushDecision.MAX_ATTEMPTS - 1)
        assertEquals(FlushDecision.Action.TERMINAL, o.action)
    }

    @Test fun http200_but_app_error_is_terminal_never_deletes() {
        val o = FlushDecision.mapResponse(200, true, "error", "bad content", 0)
        assertEquals(FlushDecision.Action.TERMINAL, o.action)
    }

    @Test fun unauthorized_triggers_reauth_not_drop() {
        val o = FlushDecision.mapResponse(401, false, null, null, 0)
        assertEquals(FlushDecision.Action.REAUTH, o.action)
    }

    @Test fun forbidden_is_terminal() {
        assertEquals(FlushDecision.Action.TERMINAL,
            FlushDecision.mapResponse(403, false, null, "not assigned", 0).action)
    }

    @Test fun unprocessable_is_terminal() {
        assertEquals(FlushDecision.Action.TERMINAL,
            FlushDecision.mapResponse(422, false, null, "before admission", 0).action)
    }

    @Test fun locked_is_terminal() {
        assertEquals(FlushDecision.Action.TERMINAL,
            FlushDecision.mapResponse(423, false, null, "locked", 0).action)
    }

    @Test fun bad_request_is_terminal() {
        assertEquals(FlushDecision.Action.TERMINAL,
            FlushDecision.mapResponse(400, false, null, "invalid", 0).action)
    }

    @Test fun server_5xx_is_retryable() {
        assertEquals(FlushDecision.Action.RETRY,
            FlushDecision.mapResponse(503, false, null, null, 0).action)
    }

    @Test fun network_failure_is_retryable() {
        assertEquals(FlushDecision.Action.RETRY,
            FlushDecision.mapResponse(0, false, null, null, 0).action)
    }

    @Test fun too_many_requests_is_retryable() {
        assertEquals(FlushDecision.Action.RETRY,
            FlushDecision.mapResponse(429, false, null, null, 0).action)
    }

    @Test fun unknown_code_fails_safe_retry_never_delete() {
        val o = FlushDecision.mapResponse(418, false, null, null, 0)
        assertEquals(FlushDecision.Action.RETRY, o.action)
    }

    @Test fun retry_cap_parks_terminal_to_stop_infinite_retry() {
        val o = FlushDecision.mapResponse(503, false, null, null, FlushDecision.MAX_ATTEMPTS - 1)
        assertEquals(FlushDecision.Action.TERMINAL, o.action)
    }

    @Test fun server_message_is_surfaced_on_terminal() {
        val o = FlushDecision.mapResponse(423, false, null, "Attendance for this date is locked.", 0)
        assertEquals("Attendance for this date is locked.", o.message)
    }

    // ── preSend: session-pin / date-pin / structural ─────────────────────────

    @Test fun presend_ok_when_session_and_date_match() {
        assertNull(FlushDecision.preSend(row(), activeSession = "2026-27", serverToday = "2026-09-14"))
    }

    @Test fun presend_session_mismatch_is_terminal() {
        val o = FlushDecision.preSend(row(session = "2025-26"), "2026-27", "2026-09-14")
        assertNotNull(o); assertEquals(FlushDecision.Action.TERMINAL, o!!.action)
    }

    @Test fun presend_stale_date_is_terminal() {
        val o = FlushDecision.preSend(row(dateIso = "2026-09-13"), "2026-27", "2026-09-14")
        assertNotNull(o); assertEquals(FlushDecision.Action.TERMINAL, o!!.action)
    }

    @Test fun presend_structurally_invalid_is_terminal() {
        val bad = row().copy(dateIso = "not-a-date")
        val o = FlushDecision.preSend(bad, "2026-27", "2026-09-14")
        assertNotNull(o); assertEquals(FlushDecision.Action.TERMINAL, o!!.action)
    }

    @Test fun presend_blank_active_session_does_not_block_on_session() {
        // If the app can't read the active session yet, don't wrongly terminal;
        // the server still enforces session on replay.
        assertNull(FlushDecision.preSend(row(), activeSession = "", serverToday = "2026-09-14"))
    }
}
