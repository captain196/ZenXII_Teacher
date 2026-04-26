package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.PtmEventDoc
import com.schoolsync.teacher.data.model.firestore.PtmRsvpDoc
import com.schoolsync.teacher.data.model.firestore.PtmSectionAssignment
import com.schoolsync.teacher.data.model.firestore.activeSections
import com.schoolsync.teacher.data.model.firestore.assignedQueueNumber
import com.schoolsync.teacher.data.model.firestore.assignmentsForTeacher
import com.schoolsync.teacher.data.model.firestore.normalizedStatus
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row in the teacher's PTM-attendance list — Phase-C section-wise
 * model. Each entry is one student of one of the teacher's assigned
 * sections, with their queue number and current lifecycle state.
 */
data class TeacherStudentView(
    val rsvp: PtmRsvpDoc,
    val sectionKey: String,
    val queueNumber: Int?,        // null = legacy or not allocated
    val status: String            // applied / delivered / no-show
)

/**
 * PTM access for the teacher app — Phase-C.
 *
 * The teacher sees:
 *   - PTMs at this school where at least one section assigns them as the
 *     class teacher (`sections[].classTeacherId == self.staffId`), or
 *     legacy slot-based PTMs where they were a slot teacher.
 *   - Per-PTM, every applied/delivered RSVP whose `teacherId` is them.
 *
 * The teacher writes only the lifecycle field (`status`) on those RSVPs.
 */
@Singleton
class PtmFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * PTMs this teacher should see. Phase-C uses `sections[]` as the
     * primary signal; falls back to legacy `slots[]` so old PTMs created
     * before Phase B still surface.
     */
    suspend fun getMyPtms(): Result<List<PtmEventDoc>> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))
        val staffId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Staff id not available"))

        val now = java.util.Date()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(now)
        val nowHm = java.text.SimpleDateFormat("HH:mm",      java.util.Locale.US).format(now)

        return try {
            val all = firestoreService.queryDocumentsAs<PtmEventDoc>(
                "ptmEvents"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
            }
            val mine = all.filter { p ->
                val statusOk = p.status.lowercase() != "cancelled"
                // Time-aware date filter — hide today's PTM once the window
                // has ended; future and dateless still surface.
                val dateOk = when {
                    p.date.isBlank() -> true
                    p.date > today   -> true
                    p.date < today   -> false
                    else             -> {
                        val end = p.endTime.ifBlank { p.slots.lastOrNull()?.endTime.orEmpty() }
                        end.isBlank() || end > nowHm
                    }
                }
                // Phase-C primary: section assignment to this teacher.
                val phaseCInvolved = p.assignmentsForTeacher(staffId).isNotEmpty()
                // Legacy slot-based fallback (Round 3 docs only).
                val legacyInvolved = p.slots.any { it.teacherId == staffId }
                statusOk && dateOk && (phaseCInvolved || legacyInvolved)
            }.sortedBy { it.date }
            Result.success(mine)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Per-student attendance rows for this teacher's sections in the given
     * PTM. Reads flat-shape Phase-C RSVPs (status=applied/delivered/no-show);
     * declined RSVPs are hidden by design — the attendance list shows only
     * people who plan to attend.
     *
     * Takes the [ptm] doc (not just its id) so future per-PTM filters have
     * the snapshot in scope without a re-fetch.
     */
    suspend fun getRsvpsForMyPtm(ptm: PtmEventDoc): Result<List<TeacherStudentView>> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))
        val staffId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Staff id not available"))

        val ptmEventId = ptm.ptmEventId.ifBlank { ptm.id }

        return try {
            val all = firestoreService.queryDocumentsAs<PtmRsvpDoc>(
                "ptmRsvps"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("ptmEventId", ptmEventId)
            }
            val views = mutableListOf<TeacherStudentView>()
            for (rsvp in all) {
                val status = rsvp.normalizedStatus()
                // Hide declined; show applied/delivered/no-show.
                if (status !in setOf("applied", "delivered", "no-show")) continue
                // Phase-C ownership: root teacherId is the section's class
                // teacher and must match this staffId.
                if (rsvp.teacherId != staffId) continue

                val sectionKey = rsvp.sectionKey.ifBlank {
                    "${rsvp.className}/${rsvp.section}"
                }
                views.add(
                    TeacherStudentView(
                        rsvp        = rsvp,
                        sectionKey  = sectionKey,
                        queueNumber = rsvp.assignedQueueNumber(),
                        status      = status
                    )
                )
            }
            // Order: by queue number (ascending), unassigned (null) at end,
            // ties broken by student name for stable rendering.
            val sorted = views.sortedWith(
                compareBy(
                    { it.queueNumber ?: Int.MAX_VALUE },
                    { it.rsvp.studentName.lowercase() }
                )
            )
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a single student as delivered. Updates the RSVP doc via direct
     * Firestore write; only fires when the requester's staffId matches the
     * RSVP's teacherId (Phase-C ownership invariant). Phase-D Firestore
     * Rules will enforce this server-side; today it's app-side until Rules
     * tighten.
     */
    suspend fun markDelivered(
        ptmEventId: String,
        studentId: String,
        status: String = "delivered"
    ): Result<Unit> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))
        val staffId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Staff id not available"))

        if (status !in setOf("delivered", "no-show", "applied")) {
            return Result.failure(IllegalArgumentException("Invalid status: $status"))
        }

        val docId = "${schoolId}_${ptmEventId}_${studentId}"
        return try {
            val existing = firestoreService.getDocumentAs<PtmRsvpDoc>("ptmRsvps", docId)
                ?: return Result.failure(Exception("RSVP not found."))
            // Ownership: teacherId on the RSVP must equal the requester.
            // This is the Phase-C field-level invariant; Rules will pin it
            // properly in Phase D. App-side check stops accidental writes.
            if (existing.teacherId != staffId) {
                return Result.failure(Exception("You're not the class teacher for this student's section."))
            }

            val rootTs = com.google.firebase.firestore.FieldValue.serverTimestamp()
            val patch = mutableMapOf<String, Any?>(
                "status"     to status,
                "markedBy"   to staffId,
                "updatedAt"  to rootTs,
            )
            if (status == "delivered") {
                patch["deliveredAt"] = rootTs
            }
            firestoreService.updateDocument("ptmRsvps", docId, patch)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bulk delivered marking — flips every applied RSVP for [ptmEventId]
     * whose `teacherId == self.staffId` to delivered, in one batched write.
     * Returns the count actually flipped (skips already-delivered/no-show).
     */
    suspend fun markAllDelivered(ptmEventId: String): Result<Int> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))
        val staffId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Staff id not available"))

        return try {
            val all = firestoreService.queryDocumentsAs<PtmRsvpDoc>(
                "ptmRsvps"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("ptmEventId", ptmEventId)
                    .whereEqualTo("teacherId", staffId)
            }

            val firestore = FirebaseFirestore.getInstance()
            val batch = firestore.batch()
            val rootTs = com.google.firebase.firestore.FieldValue.serverTimestamp()
            var flipped = 0
            for (rsvp in all) {
                val status = rsvp.normalizedStatus()
                if (status != "applied") continue
                val ref = firestore.collection("ptmRsvps")
                    .document("${schoolId}_${ptmEventId}_${rsvp.studentId}")
                batch.update(
                    ref,
                    mapOf(
                        "status"      to "delivered",
                        "markedBy"    to staffId,
                        "deliveredAt" to rootTs,
                        "updatedAt"   to rootTs,
                    )
                )
                flipped++
            }
            if (flipped > 0) batch.commit().await()
            Result.success(flipped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Phase-C-aware status flip — replaces the deprecated [updateRsvpStatus]
     * shim. Routes through [markDelivered]; the legacy `markAttendance`
     * (per-booking, slot-based) is intentionally retired and any caller
     * still using it is a build error.
     */
    @Deprecated(
        "Phase-C uses markDelivered. Migrate call sites.",
        ReplaceWith("markDelivered(ptmEventId, studentId, status)")
    )
    suspend fun updateRsvpStatus(
        ptmEventId: String,
        studentId: String,
        status: String
    ): Result<Unit> = markDelivered(ptmEventId, studentId, status)
}
