package com.schoolsync.teacher.data.local.offline

/**
 * C1 — the pure decision core of the flush worker.
 *
 * Deliberately free of Android, Room, Retrofit and coroutines so the ENTIRE
 * safety contract (fail-closed mapping, session-pin, date-pin, retry cap) is
 * unit-testable on the plain JVM with no device. The worker is a thin shell that
 * does IO and defers every judgement to these functions.
 */
object FlushDecision {

    /** Max transient retries before a row is parked TERMINAL (stops infinite 5xx retry). */
    const val MAX_ATTEMPTS = 8

    enum class Action {
        DELETE,          // applied — remove the row
        RETRY,           // transient — keep row, let WorkManager back off
        REAUTH,          // 401 — keep row, refresh auth, retry
        TERMINAL         // permanent — park row, surface to teacher
    }

    data class Outcome(val action: Action, val message: String)

    /**
     * PRE-SEND gate. Returns a TERMINAL outcome if the row must NOT be sent as-is
     * (session rolled over, or its day has passed), else null (safe to send).
     *
     * These are client-side guards that PREVENT a mis-dated / mis-sessioned
     * write; the server enforces the same invariants again on replay (defence in
     * depth — a wrong device clock cannot get past the server's own checks).
     */
    fun preSend(row: PendingMark, activeSession: String, serverToday: String): Outcome? {
        if (!row.isStructurallyValid()) {
            return Outcome(Action.TERMINAL, "This attendance entry was corrupted and can't be sent.")
        }
        if (activeSession.isNotBlank() && row.session != activeSession) {
            return Outcome(
                Action.TERMINAL,
                "This attendance was marked in a previous academic session and can no longer be " +
                    "submitted. Please re-enter it for the current session if it is still needed."
            )
        }
        // Date-pin: attendance is a same-day activity. If the marked day has
        // passed, a direct save would be recorded on the wrong day (or refused
        // by the server lock). Route the teacher to the correction workflow.
        if (serverToday.isNotBlank() && row.dateIso != serverToday) {
            return Outcome(
                Action.TERMINAL,
                "This attendance is from ${row.dateIso}, which is no longer today. Please file a " +
                    "correction request to record it."
            )
        }
        return null
    }

    /**
     * POST-RESPONSE mapping. Fail-closed: only an explicit HTTP-200 + application
     * status \"success\" deletes the row. Everything else keeps it (retry) or parks
     * it (terminal); an unexpected shape is NEVER treated as success.
     *
     * @param httpCode transport status (or a synthetic 0 for a network failure)
     * @param transportOk retrofit response.isSuccessful
     * @param appStatus the body's "status" field ("success"/"error"), or null
     * @param serverMessage the body's "message"/"error" text, if any
     * @param attempts attempts already made on this row (BEFORE this one)
     */
    fun mapResponse(
        httpCode: Int,
        transportOk: Boolean,
        appStatus: String?,
        serverMessage: String?,
        attempts: Int,
        isPartial: Boolean = false
    ): Outcome {
        val svr = serverMessage?.takeIf { it.isNotBlank() }

        // Success requires BOTH transport success AND an explicit app-level ok.
        if (transportOk && httpCode == 200) {
            return if (appStatus == "success") {
                if (isPartial) {
                    // OFFLINE-PARTIAL-1: the server persisted only SOME of the
                    // section's per-day docs (partial:true / writtenCount <
                    // expectedCount) yet still returned 200 success. Deleting the
                    // row would lose the un-written students. The whole save is
                    // idempotent, so RETRY the entire row instead.
                    retryOrGiveUp(attempts, "Partially saved — retrying the rest.")
                } else {
                    Outcome(Action.DELETE, "Synced")
                }
            } else {
                // 200 with a non-success body = the server rejected the content.
                // Retrying won't change it → terminal (fail-closed: do NOT delete).
                Outcome(Action.TERMINAL, svr ?: "The server did not accept this attendance.")
            }
        }

        return when (httpCode) {
            // OFFLINE-REAUTH-CAP-1: a 401 refreshes the token and retries, but it
            // must NOT spin forever (a disabled account 401s every time). Cap it
            // like every other transient failure so the row eventually surfaces.
            401 -> if (attempts + 1 >= MAX_ATTEMPTS)
                       Outcome(Action.TERMINAL, "Couldn't sign in to sync this attendance. Please sign in again.")
                   else
                       Outcome(Action.REAUTH, "Signing back in…")
            403 -> Outcome(
                Action.TERMINAL,
                svr ?: "You're no longer assigned to this class/section, so this couldn't be saved."
            )
            422 -> Outcome(Action.TERMINAL, svr ?: "This attendance couldn't be accepted.")
            423 -> Outcome(
                Action.TERMINAL,
                svr ?: "Attendance for this date is locked. Please file a correction request."
            )
            400 -> Outcome(Action.TERMINAL, svr ?: "This attendance entry was invalid.")
            408, 429, in 500..599 -> retryOrGiveUp(attempts, svr ?: "Couldn't reach ZenXii — will retry.")
            0 -> retryOrGiveUp(attempts, "No connection — waiting to sync.")
            else -> // Unknown/unexpected: fail SAFE. Retry (capped), never delete.
                retryOrGiveUp(attempts, svr ?: "Unexpected response — will retry.")
        }
    }

    private fun retryOrGiveUp(attempts: Int, retryMsg: String): Outcome =
        if (attempts + 1 >= MAX_ATTEMPTS)
            Outcome(Action.TERMINAL, "Could not sync after several attempts. Please try again later.")
        else
            Outcome(Action.RETRY, retryMsg)
}
