package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Parent-Teacher Meeting (PTM) event scheduled by the school.
 *
 * Collection: `ptmEvents`
 * Doc ID:     `{schoolId}_{ptmEventId}`
 *
 * Teachers view PTMs where they are assigned to one or more slots
 * (`slots[].teacherId == self.staffId`).
 */
data class PtmEventDoc(
    @DocumentId
    val id: String = "",
    val ptmEventId: String = "",
    val schoolId: String = "",
    val session: String = "",
    val sectionKey: String = "",
    val className: String = "",
    val section: String = "",
    val date: String = "",
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val status: String = "scheduled",

    // ── Phase-A (section-wise PTM model) ───────────────────────────────
    /** Common time window for the whole PTM. Empty on legacy docs — fall
     *  back via [windowStartTime]/[windowEndTime]. */
    val startTime: String = "",
    val endTime: String   = "",
    /** One entry per (className, section) the PTM serves. Snapshotted at
     *  save time — later changes to `sections.classTeacherId` do not
     *  retroactively reroute submitted RSVPs. */
    val sections: List<PtmSectionAssignment> = emptyList(),

    // ── Legacy fields (Round 1–3 slot-based model) ─────────────────────
    val slots: List<PtmSlot> = emptyList(),
    val totalCapacity: Int = 0,

    val createdAt: Any? = null,
    val updatedAt: Any? = null
)

data class PtmSectionAssignment(
    val className: String = "",
    val section: String = "",
    val sectionKey: String = "",
    val classTeacherId: String = "",
    val classTeacherName: String = ""
)

data class PtmSlot(
    val slotIndex: Int = 0,
    val startTime: String = "",
    val endTime: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val capacity: Int = 1
)

/**
 * Parent's response — teacher reads to see who's coming.
 *
 * Round-3 evolution: a parent can book multiple teachers per PTM. Each leg
 * lives in [bookings]. Top-level slot/teacher fields are a legacy mirror
 * (populated from `bookings[0]` on write) so older app builds keep working
 * during rollout. Always go through [normalizedBookings] when reading.
 */
data class PtmRsvpDoc(
    @DocumentId
    val id: String = "",
    val ptmEventId: String = "",
    val schoolId: String = "",
    val sectionKey: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val rollNo: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val parentEmail: String = "",

    // ── New canonical shape (Round 3) ─────────────────────────────────
    val bookings: List<PtmBooking> = emptyList(),

    // ── Phase-A (section-wise PTM) ─────────────────────────────────────
    /** Token / queue number per (PTM × section). `null` = unassigned. */
    val queueNumber: Int? = null,
    /** Server-set when status transitions to applied. */
    val appliedAt: Any? = null,
    /** Server-set when class teacher marks delivered. */
    val deliveredAt: Any? = null,

    // ── Legacy mirror (Round 1–2) — also reused by Phase-A flat shape:
    //      teacherId / teacherName = the section's class teacher
    //      status                   = applied / delivered / no-show / declined
    val slotIndex: Int = -1,
    val slotStartTime: String = "",
    val slotEndTime: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val status: String = "pending",
    val note: String = "",
    val respondedAt: Any? = null,
    val respondedBy: String = "",
    val markedBy: String = ""
)

/**
 * One leg of a multi-teacher RSVP. The fields mirror the slot's snapshot
 * at the moment of booking; do not derive these from the live PTM doc, so
 * a later admin edit doesn't silently mutate response history.
 */
data class PtmBooking(
    val slotIndex: Int = -1,
    val teacherId: String = "",
    val teacherName: String = "",
    val slotStartTime: String = "",
    val slotEndTime: String = "",
    /** "pending" / "confirmed" / "declined" / "attended" / "no-show" */
    val status: String = "confirmed",
    val note: String = "",
    val respondedAt: Any? = null,
    val markedBy: String = "",
    val markedAt: Any? = null
)

/**
 * Bookings the rest of the app should render. New docs ship a non-empty
 * `bookings` list directly; legacy docs are materialised from the
 * top-level fields. Returns empty when neither path has data.
 */
fun PtmRsvpDoc.normalizedBookings(): List<PtmBooking> {
    if (bookings.isNotEmpty()) return bookings
    val hasLegacyBooking =
        teacherId.isNotBlank() || slotStartTime.isNotBlank() || slotIndex >= 0
    if (!hasLegacyBooking) return emptyList()
    return listOf(
        PtmBooking(
            slotIndex     = slotIndex,
            teacherId     = teacherId,
            teacherName   = teacherName,
            slotStartTime = slotStartTime,
            slotEndTime   = slotEndTime,
            status        = status,
            note          = note,
            respondedAt   = respondedAt
        )
    )
}

/**
 * Filter [normalizedBookings] to those assigned to a specific teacher.
 * The teacher app uses this to render its per-teacher schedule view.
 */
fun PtmRsvpDoc.bookingsForTeacher(staffId: String): List<PtmBooking> =
    normalizedBookings().filter { it.teacherId == staffId }

/**
 * Computed roll-up across [normalizedBookings] — derived on read so we
 * never store a stale value after a single booking's status flips.
 */
fun PtmRsvpDoc.computedOverallStatus(): String {
    val list = normalizedBookings()
    if (list.isEmpty()) return "pending"
    val statuses = list.map { it.status.ifBlank { "pending" } }.toSet()
    return when {
        statuses == setOf("attended")                  -> "attended"
        statuses.all { it == "declined" }              -> "all_declined"
        statuses.all { it == "confirmed" || it == "attended" } -> "all_confirmed"
        statuses.contains("confirmed") || statuses.contains("attended") -> "partial"
        else                                           -> "pending"
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Phase-A normalizers — section-wise PTM model, back-compat with legacy
// ════════════════════════════════════════════════════════════════════════

/**
 * Sections served by this PTM. New-shape returns [PtmEventDoc.sections].
 * Legacy slot-based docs are synthesised from [PtmEventDoc.sectionKey]
 * ONLY — slot teacherId is intentionally not carried over, because in
 * the slot model that's typically a subject teacher, not the section's
 * class teacher. Consumers must do a real lookup when classTeacherId
 * comes back blank.
 */
fun PtmEventDoc.activeSections(): List<PtmSectionAssignment> {
    if (sections.isNotEmpty()) return sections
    if (sectionKey.isBlank() && className.isBlank()) return emptyList()
    return listOf(
        PtmSectionAssignment(
            className        = className,
            section          = section,
            sectionKey       = sectionKey.ifBlank { "$className/$section" }.ifBlank { "ALL" },
            classTeacherId   = "",
            classTeacherName = ""
        )
    )
}

fun PtmEventDoc.windowStartTime(): String =
    if (startTime.isNotBlank()) startTime else slots.firstOrNull()?.startTime.orEmpty()

fun PtmEventDoc.windowEndTime(): String =
    if (endTime.isNotBlank()) endTime else slots.lastOrNull()?.endTime.orEmpty()

/**
 * Find the section assignments this teacher is responsible for. A teacher
 * may serve multiple sections inside an all-school PTM if they are the
 * class teacher of more than one section (rare but possible).
 */
fun PtmEventDoc.assignmentsForTeacher(staffId: String): List<PtmSectionAssignment> =
    activeSections().filter { it.classTeacherId == staffId }

/** Phase-A unified status: applied / delivered / no-show / declined / "". */
fun PtmRsvpDoc.normalizedStatus(): String {
    val root = status.lowercase()
    if (root in setOf("applied", "delivered", "no-show", "declined")) return root
    val list = normalizedBookings()
    if (list.isEmpty()) return ""
    val st = list.first().status.lowercase()
    return when (st) {
        "confirmed" -> "applied"
        "attended"  -> "delivered"
        "no-show"   -> "no-show"
        "declined"  -> "declined"
        else        -> ""
    }
}

/**
 * Token / queue number to render, or null when unassigned. Treats any
 * non-positive value (legacy 0) as "not set" so a stray 0 doesn't
 * poison the ordering when the teacher app sorts the attendance list.
 */
fun PtmRsvpDoc.assignedQueueNumber(): Int? =
    queueNumber?.takeIf { it > 0 }
