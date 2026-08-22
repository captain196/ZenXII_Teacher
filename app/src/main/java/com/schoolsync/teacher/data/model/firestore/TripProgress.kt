package com.schoolsync.teacher.data.model.firestore

/**
 * Stop progress for the trip the driver is running — F13.2 teacher slice.
 *
 * Derived from `tripLogs.checkpoints[]`, the same itinerary the admin fleet map
 * and the parent app read, using the same rules so all three surfaces agree on
 * which stop a bus is on:
 *
 *  • a stop counts as served only when the DRIVER confirmed it
 *    (`checkpoint_state` = Reached or Skipped) — never inferred from GPS;
 *  • order is applied defensively, so a partially-written document cannot
 *    scramble the route.
 *
 * No map, no ETA. The driver needs to know how much of the route is left, not a
 * prediction — and F13.2 keeps ETA deferred to F13.3 because no travel-time
 * model exists.
 */
data class TripProgress(
    val totalStops: Int = 0,
    val stopsDone: Int = 0,
    val stopsRemaining: Int = 0,
    val nextStopName: String = "",
    val progressPct: Int = 0,
) {
    /** One line for the driver, or null when there is nothing honest to show. */
    fun summaryOrNull(): String? = when {
        totalStops == 0 -> null
        stopsRemaining == 0 -> "All $totalStops stops done"
        nextStopName.isNotBlank() ->
            "Stop $stopsDone of $totalStops · next: $nextStopName"
        else -> "Stop $stopsDone of $totalStops · $stopsRemaining remaining"
    }

    companion object {
        fun from(checkpoints: List<Map<String, Any?>>): TripProgress {
            if (checkpoints.isEmpty()) return TripProgress()
            val ordered = checkpoints.sortedBy { (it["order"] as? Number)?.toInt() ?: 0 }

            var done = 0
            var next = ""
            for (c in ordered) {
                val state = (c["checkpoint_state"] as? String).orEmpty().ifBlank { "Pending" }
                val served = state == "Reached" || state == "Skipped"
                if (served) done++
                if (next.isBlank() && !served) next = (c["stop_name"] as? String).orEmpty()
            }
            return TripProgress(
                totalStops = ordered.size,
                stopsDone = done,
                stopsRemaining = (ordered.size - done).coerceAtLeast(0),
                nextStopName = next,
                progressPct = if (ordered.isEmpty()) 0 else (done * 100) / ordered.size,
            )
        }
    }
}
