package com.schoolsync.teacher.data.local.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * C1 — one durable, offline-queued attendance submission.
 *
 * The Teacher app marks student attendance section-wide through
 * `POST attendance/save` (absent / leave / late lists; everyone else Present).
 * A [PendingMark] is exactly that one submission, persisted to disk so it
 * survives activity destruction, process death, reboot and multiple offline
 * days, and is replayed verbatim through the SAME endpoint when connectivity
 * returns — offline is a transport condition, never an authorization mode.
 *
 * Trust boundary: [schoolId] and [session] are stored for display / routing /
 * safety-checks ONLY. The server re-derives the tenant and session from the
 * authenticated context on every replay; nothing here is trusted for
 * authorization. [dateIso] is the day the teacher actually marked — the server
 * validates it against its own "today" (the mark_date date-pin) and refuses a
 * mis-dated flush rather than silently recording it on the wrong day.
 */
@Entity(tableName = "pending_mark")
data class PendingMark(
    @PrimaryKey val id: String,          // client UUIDv4 = idempotency key (never regenerated)
    val schoolId: String,                // display/routing only — NOT trusted for auth
    val session: String,                 // session-pin (compared to active session at flush)
    val className: String,
    val section: String,
    val dateIso: String,                 // "2026-09-14" — the date-pin
    val absentJson: String = "[]",       // JSON array of studentIds → Absent
    val leaveJson: String = "[]",        // JSON array of studentIds → Leave
    val lateJson: String = "[]",         // JSON array of {studentId,lateMinutes,time?} → Present+late
    val reason: String = "",
    val createdAt: Long,                 // device epoch ms — ordering (oldest first)
    val attempts: Int = 0,
    val state: String = QueueState.PENDING,
    val lastError: String = "",
    val updatedAt: Long = createdAt
) {
    /** Rows sharing this key are the same section+date+session — later supersedes earlier. */
    fun collapseKey(): String = "$session|$className|$section|$dateIso"

    /** Defensive: a malformed row must never reach the network or crash the worker. */
    fun isStructurallyValid(): Boolean {
        if (id.isBlank()) return false
        if (session.isBlank()) return false
        if (className.isBlank() || section.isBlank()) return false
        if (!DATE_RE.matches(dateIso)) return false
        if (createdAt <= 0L) return false
        if (state !in QueueState.ALL) return false
        return true
    }

    companion object {
        val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    }
}

/** The queue state machine. Kept as string constants so Room needs no TypeConverter. */
object QueueState {
    const val PENDING = "PENDING"                 // waiting to sync
    const val SENDING = "SENDING"                 // in flight (see crash recovery)
    const val FAILED_RETRYABLE = "FAILED_RETRYABLE" // transient (network/5xx) — will retry
    const val FAILED_TERMINAL = "FAILED_TERMINAL"   // permanent (403/422/423/date-pin) — will NOT retry

    val ALL = setOf(PENDING, SENDING, FAILED_RETRYABLE, FAILED_TERMINAL)

    /** States the flush worker should attempt to send (oldest first). */
    val SENDABLE = setOf(PENDING, FAILED_RETRYABLE)
}
