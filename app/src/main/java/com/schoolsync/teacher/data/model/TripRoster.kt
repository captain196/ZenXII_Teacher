package com.schoolsync.teacher.data.model

/**
 * The conductor-facing view of a trip's students — the head count.
 *
 * Every number here is computed SERVER-SIDE by
 * `Transport_qr_attendance_service::tripRoster()`, the same implementation the
 * admin panel reads. Nothing is derived on the device, deliberately: a count
 * the phone works out for itself is a count that can disagree with the office
 * about whether a child is on the bus.
 */
data class TripRoster(
    val tripId: String = "",
    /** "pickup" (home → school) or "drop" (school → home). */
    val leg: String = "",
    val direction: String = "",
    val status: String = "",
    val routeName: String = "",
    val vehicleId: String = "",
    val counts: RosterCounts = RosterCounts(),
    val students: List<RosterStudent> = emptyList(),
    val blocker: CompletionBlocker = CompletionBlocker(),
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(body: Map<String, Any>): TripRoster {
            val r = body["roster"] as? Map<String, Any> ?: emptyMap()
            val b = body["blockers"] as? Map<String, Any> ?: emptyMap()
            val c = r["counts"] as? Map<String, Any> ?: emptyMap()
            val rows = r["students"] as? List<Map<String, Any>> ?: emptyList()
            return TripRoster(
                tripId    = r.str("trip_id"),
                leg       = r.str("leg"),
                direction = r.str("direction"),
                status    = r.str("status"),
                routeName = r.str("route_name"),
                vehicleId = r.str("vehicle_id"),
                counts    = RosterCounts(
                    expected        = c.int("expected"),
                    boarded         = c.int("boarded"),
                    alighted        = c.int("alighted"),
                    onBoard         = c.int("on_board"),
                    absent          = c.int("absent"),
                    alternate       = c.int("alternate"),
                    resolvedOther   = c.int("resolved_other"),
                    unresolved      = c.int("unresolved"),
                    offRosterAboard = c.int("off_roster_aboard"),
                ),
                students  = rows.map {
                    RosterStudent(
                        studentId  = it.str("student_id"),
                        name       = it.str("name"),
                        className  = it.str("class"),
                        state      = it.str("state"),
                        resolution = it.str("resolution"),
                        boarded    = it["boarded"] as? Boolean ?: false,
                        alighted   = it["alighted"] as? Boolean ?: false,
                        onRoster   = it["on_roster"] as? Boolean ?: true,
                    )
                },
                blocker   = CompletionBlocker(
                    blocked = b["blocked"] as? Boolean ?: false,
                    reason  = b.str("reason"),
                    message = b.str("message"),
                ),
            )
        }

        private fun Map<String, Any>.str(k: String) = (this[k] as? String).orEmpty()
        private fun Map<String, Any>.int(k: String) = (this[k] as? Number)?.toInt() ?: 0
    }
}

/**
 * The head count, at pickup and at drop-off.
 *
 * `onBoard` is the number that matters while the bus is moving: boarded minus
 * alighted. `unresolved` is the number that stops the trip being closed — a
 * child nobody has accounted for.
 */
data class RosterCounts(
    val expected: Int = 0,
    val boarded: Int = 0,
    val alighted: Int = 0,
    val onBoard: Int = 0,
    val absent: Int = 0,
    val alternate: Int = 0,
    val resolvedOther: Int = 0,
    val unresolved: Int = 0,
    /** Aboard but NOT on this trip's roster — a child on the wrong bus. */
    val offRosterAboard: Int = 0,
)

data class RosterStudent(
    val studentId: String = "",
    val name: String = "",
    val className: String = "",
    val state: String = "",
    val resolution: String = "",
    val boarded: Boolean = false,
    val alighted: Boolean = false,
    val onRoster: Boolean = true,
)

/**
 * Why the trip cannot be completed yet.
 *
 * Mirrors `completionBlockers()`. The conductor sees the reason in plain words
 * rather than a refused button with no explanation; the server remains the
 * authority and refuses the completion regardless of what the UI shows.
 */
data class CompletionBlocker(
    val blocked: Boolean = false,
    val reason: String = "",
    val message: String = "",
)

/** The outcome of one QR scan, as the server classified it. */
data class ScanOutcome(
    val status: String = "",
    val message: String = "",
    val studentId: String = "",
    val studentName: String = "",
) {
    val isFresh: Boolean          get() = status == "success"
    val isDuplicate: Boolean      get() = status == "already"
    val needsConfirmation: Boolean get() = status == "needs_confirmation"
}
