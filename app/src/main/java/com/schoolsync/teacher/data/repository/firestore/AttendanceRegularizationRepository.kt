package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.DocumentId
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AttendanceRegularizationRepository — teacher-side submit + read of GPS staff
 * attendance regularization ("Attendance Request") docs, written DIRECTLY to
 * Firestore (no backend needed to submit — mirrors [LeaveFirestoreRepository]).
 *
 * Flow: teacher submits one doc PER date (grouped by a shared `batchId`) with
 * `status = "pending"`. The school admin panel later lists pending docs, and on
 * approval stamps the corrected day onto attendance via the same writer the GPS
 * punch uses (that approval side ships with the staff-attendance backend deploy).
 *
 * Collection: [Constants.Firestore.ATTENDANCE_REGULARIZATIONS].
 * Doc id: {schoolId}_{staffId}_{date}_{ts}.
 */
@Singleton
class AttendanceRegularizationRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {

    /**
     * Submit a batch of regularization requests — one Firestore doc per date,
     * committed atomically in a single WriteBatch. `requestedStatus` is always
     * "P" (Present): a regularization asks the admin to correct the day to
     * present with the claimed in/out times.
     *
     * @param entries per-date claims (date + optional ISO check-in/out).
     * @param reason  required note shared across the batch.
     * @return the generated batchId on success.
     */
    suspend fun submit(
        entries: List<RegularizationEntry>,
        reason: String,
    ): Result<String> {
        if (entries.isEmpty()) return Result.failure(IllegalArgumentException("No dates selected."))
        val schoolId = getSchoolCode() ?: return Result.failure(Exception("School code not available"))
        val staffId = getStaffId() ?: return Result.failure(Exception("Staff ID not available"))
        val staffName = getStaffName() ?: staffId
        val now = System.currentTimeMillis()
        val batchId = "REG_${schoolId}_${staffId}_$now"

        val items = entries.map { e ->
            val docId = "${schoolId}_${staffId}_${e.date}_$now"
            val data = hashMapOf<String, Any?>(
                "schoolId" to schoolId,
                "staffId" to staffId,
                "staffName" to staffName,
                "personType" to "staff",
                "date" to e.date,                       // yyyy-MM-dd
                "requestedStatus" to "P",
                "claimedCheckInAt" to e.checkInAt,      // ISO-8601 | null
                "claimedCheckOutAt" to e.checkOutAt,    // ISO-8601 | null
                "reason" to reason,
                "batchId" to batchId,
                "status" to "pending",
                "appliedAt" to firestoreService.serverTimestamp(),
                "reviewedBy" to "",
                "reviewedAt" to null,
                "remarks" to "",
                "source" to "teacher_app",
            )
            Triple(Constants.Firestore.ATTENDANCE_REGULARIZATIONS, docId, data as Any)
        }

        return try {
            firestoreService.setDocumentsBatch(items, merge = false)
            Result.success(batchId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * My regularization requests (all statuses), newest date first. Query avoids
     * orderBy to skip a composite-index requirement; sorted client-side.
     */
    suspend fun getMyRequests(): Result<List<RegularizationDoc>> {
        val schoolId = getSchoolCode() ?: return Result.failure(Exception("School code not available"))
        val staffId = getStaffId() ?: return Result.failure(Exception("Staff ID not available"))
        return try {
            val rows = firestoreService.queryDocumentsAs<RegularizationDoc>(
                Constants.Firestore.ATTENDANCE_REGULARIZATIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("staffId", staffId)
                    .whereEqualTo("personType", "staff")
            }
            Result.success(rows.sortedByDescending { it.date })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getSchoolCode(): String? =
        tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun getStaffId(): String? =
        tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun getStaffName(): String? =
        tokenManager.userName.firstOrNull()?.takeIf { it.isNotBlank() }
}

/** One date's regularization claim (assembled in the sheet before submit). */
data class RegularizationEntry(
    val date: String,          // yyyy-MM-dd
    val checkInAt: String?,    // ISO-8601 | null
    val checkOutAt: String?,   // ISO-8601 | null
)

/** A submitted regularization doc read back for the "My requests" history. */
data class RegularizationDoc(
    @DocumentId val id: String = "",
    val date: String = "",
    val requestedStatus: String = "P",
    val claimedCheckInAt: String? = null,
    val claimedCheckOutAt: String? = null,
    val reason: String = "",
    val batchId: String = "",
    val status: String = "pending",   // pending | approved | rejected | cancelled | auto_rejected
    val reviewedBy: String = "",
    val remarks: String = "",
)
