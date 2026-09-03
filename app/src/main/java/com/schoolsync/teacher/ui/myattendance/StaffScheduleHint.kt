package com.schoolsync.teacher.ui.myattendance

/**
 * Client-side GUIDANCE mirror of the server's staff check-in classification
 * (Attendance_policy::_evaluate_checkin). It only informs the button — the
 * server remains the sole authority on the actual mark:
 *   - on-time  → now ≤ shiftStart + grace              → Present
 *   - late     → now  > shiftStart + grace             → Tardy (+lateMinutes)
 *   - closed   → a latest-check-in cutoff is set and now > it → rejected
 * Half-day (M) is intentionally NOT predicted here: the server decides it at
 * check-OUT from hours worked, which isn't known at check-in.
 */
enum class CheckInWindow { BEFORE_OPEN, ON_TIME, LATE, CLOSED, UNKNOWN }

data class CheckInHint(
    val window: CheckInWindow,
    val lateMinutes: Int = 0,
    val onTimeBy: String? = null,   // HH:mm — clock in by this to be on time
    val opensAt: String? = null,    // HH:mm — attendance opens at this time
    val closedAt: String? = null,   // HH:mm — the passed cutoff
)

fun staffCheckInHint(
    shiftStartMin: Int?,
    graceMin: Int,
    earliestCheckInMin: Int?,
    latestCheckInMin: Int?,
    nowMin: Int,
): CheckInHint {
    if (shiftStartMin == null) return CheckInHint(CheckInWindow.UNKNOWN)
    // Opening gate: can't check in before attendance opens (mirrors the server's
    // 'too_early' rejection).
    if (earliestCheckInMin != null && nowMin < earliestCheckInMin) {
        return CheckInHint(CheckInWindow.BEFORE_OPEN, opensAt = fmtMinOfDay(earliestCheckInMin))
    }
    if (latestCheckInMin != null && nowMin > latestCheckInMin) {
        return CheckInHint(CheckInWindow.CLOSED, closedAt = fmtMinOfDay(latestCheckInMin))
    }
    val onTimeCutoff = shiftStartMin + graceMin
    return if (nowMin <= onTimeCutoff) {
        CheckInHint(CheckInWindow.ON_TIME, onTimeBy = fmtMinOfDay(onTimeCutoff))
    } else {
        CheckInHint(CheckInWindow.LATE, lateMinutes = (nowMin - shiftStartMin).coerceAtLeast(0))
    }
}

/** Minutes-of-day → "HH:mm". */
fun fmtMinOfDay(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}
