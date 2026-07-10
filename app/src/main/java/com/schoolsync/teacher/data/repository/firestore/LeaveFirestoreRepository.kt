package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.LeaveApplicationDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for teacher leave application operations in Firestore.
 *
 * Collection used:
 * - leaveApplications: leave requests submitted by staff with approval workflow
 */
@Singleton
class LeaveFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Submit a new leave application for the current teacher.
     * Reads teacherId and teacherName from [TokenManager].
     *
     * @return the auto-generated document ID (leaveId)
     */
    suspend fun submitLeave(
        leaveType: String,
        startDate: String,
        endDate: String,
        numberOfDays: Double,          // MEDIUM #5: Double so half-day stores 0.5
        reason: String,
        attachments: List<String> = emptyList(),
        typePaid: Boolean? = null,           // CR-3 cross-system: snapshot at submit time
        halfDay: Boolean = false,            // Phase 4 cross-system
        halfDayPeriod: String = ""           // 'AM' | 'PM'
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()
            ?: return Result.failure(Exception("Teacher name not available"))

        val leaveId = "${schoolCode}_${teacherId}_${System.currentTimeMillis()}"

        val data = hashMapOf<String, Any?>(
            "schoolId" to schoolCode,
            "applicantType" to "staff",
            "applicantId" to teacherId,
            "applicantName" to teacherName,
            "sectionKey" to "",
            "leaveType" to leaveType,
            "startDate" to startDate,
            "endDate" to endDate,
            "numberOfDays" to numberOfDays,
            "reason" to reason,
            "attachments" to attachments,
            "status" to "pending",
            "appliedAt" to firestoreService.serverTimestamp(),
            "approvedBy" to "",
            "remarks" to "",
            // CR-3 fallback data: snapshot the type's paid status at submission
            // time so payroll can still classify correctly even if the school
            // later deletes this leave type from config.
            "typePaid" to (typePaid ?: true),
            "type_paid" to (typePaid ?: true),
            // Phase 4 half-day support — payroll skips attendance 'L' marking
            // when halfDay=true and applies a 0.5-day deduction (if unpaid).
            "halfDay" to halfDay,
            "half_day" to halfDay,
            "halfDayPeriod" to halfDayPeriod,
            "half_day_period" to halfDayPeriod
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                data
            )
            Result.success(leaveId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the leave history for the current teacher, ordered by appliedAt descending.
     * Query: schoolId + applicantId + applicantType=="staff".
     */
    suspend fun getLeaveHistory(): Result<List<LeaveApplicationDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        return try {
            // Query without orderBy to avoid composite index requirement.
            // Sort client-side instead.
            val leaves = firestoreService.queryDocumentsAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("applicantId", teacherId)
                    .whereEqualTo("applicantType", "staff")
            }
            // Sort by startDate descending (most recent first)
            Result.success(leaves.sortedByDescending { it.startDate })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancel a leave application. Only allowed when the current status is "pending".
     *
     * Approved-leave cancellation is intentionally rejected here — those go
     * through the admin-side cancel handler, which knows how to undo the
     * attendance stamp + payroll-day adjustments. We only own the
     * Pending → Cancelled transition.
     *
     * Balance restore is NOT performed here: Firestore rules now make BAL
     * docs service-account-only, so any client-side balance write is dead
     * and would fail with PERMISSION_DENIED. The admin/service path owns
     * balance reconciliation on cancel.
     *
     * @throws IllegalStateException if the leave is not in "pending" status
     */
    suspend fun cancelLeave(leaveId: String): Result<Unit> {
        return try {
            // Verify the leave is still pending before cancelling.
            // Case-insensitive compare so docs written by older/legacy
            // writers in CapitalCase are still cancellable.
            val doc = firestoreService.getDocumentAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId
            ) ?: return Result.failure(Exception("Leave application not found: $leaveId"))

            if (!doc.status.equals("pending", ignoreCase = true)) {
                return Result.failure(
                    IllegalStateException("Cannot cancel leave with status '${doc.status}'. Only pending leaves can be cancelled.")
                )
            }

            firestoreService.updateDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                mapOf("status" to "cancelled")
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a specific leave application in real time for status changes
     * (e.g. pending -> approved / rejected).
     */
    fun observeLeaveStatus(leaveId: String): Flow<LeaveApplicationDoc?> {
        return firestoreService.observeDocumentAs<LeaveApplicationDoc>(
            Constants.Firestore.LEAVE_APPLICATIONS,
            leaveId
        )
    }

    /**
     * Fetch leave types from the schools document → leaveTypes map.
     * Returns the raw map of typeId → {name, code, paid, days_per_year, ...} or null.
     */
    suspend fun getSchoolLeaveTypes(schoolId: String): Map<String, Any>? {
        return try {
            val doc = firestoreService.getDocumentMap(
                Constants.Firestore.SCHOOLS,
                schoolId
            )
            @Suppress("UNCHECKED_CAST")
            doc?.get("leaveTypes") as? Map<String, Any>
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch the leave balance document for the given doc ID.
     * Returns the raw map of type → {allocated, used, balance, carried} or null.
     */
    /**
     * Fetch the teacher's gender from staff doc for filtering gender-specific leave types.
     */
    suspend fun getStaffGender(staffDocId: String): String? {
        return try {
            val doc = firestoreService.getDocumentMap(
                Constants.Firestore.STAFF,
                staffDocId
            )
            doc?.get("gender")?.toString() ?: doc?.get("Gender")?.toString()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBalanceDoc(docId: String): Map<String, Any>? {
        return try {
            val doc = firestoreService.getDocumentMap(
                Constants.Firestore.LEAVE_APPLICATIONS,
                docId
            )
            @Suppress("UNCHECKED_CAST")
            val balances = doc?.get("balances") as? Map<String, Any>
            balances
        } catch (e: Exception) {
            null
        }
    }

    // ── Student Leave Approval (teacher as approver) ────────────────────────

    /**
     * Fetch student leave applications for the teacher's school, scoped to a
     * class/section.
     *
     * H8: the status filter is intentionally dropped so processed
     * (approved/rejected/cancelled) rows are returned alongside pending ones —
     * otherwise a teacher's own approval/rejection vanishes instead of moving
     * to the "Processed" section. Callers split by status client-side.
     *
     * H9: [className]/[section] are pushed to the query as equality filters
     * (previously ignored → whole-school scan) and the result is bounded by
     * [limit]. All filters are equality-only + no orderBy, so no composite
     * index is required.
     */
    suspend fun getStudentLeaveRequests(
        className: String? = null,
        section: String? = null,
        limit: Long = 100L
    ): Result<List<LeaveApplicationDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val leaves = firestoreService.queryDocumentsAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS
            ) { ref ->
                var q: Query = ref
                    .whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("applicantType", "student")
                if (!className.isNullOrBlank()) q = q.whereEqualTo("className", className)
                if (!section.isNullOrBlank()) q = q.whereEqualTo("section", section)
                q.limit(limit)
            }.sortedByDescending { it.startDate }
            Result.success(leaves)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Approve a student's leave application.
     */
    suspend fun approveStudentLeave(leaveId: String, remarks: String = ""): Result<Unit> {
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName() ?: teacherId
        val schoolCode = getSchoolCode() ?: ""

        return try {
            // Read the leave doc first to get student info
            val leave = firestoreService.getDocumentAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS, leaveId
            )

            firestoreService.updateDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                mapOf(
                    "status" to "approved",
                    "approvedBy" to teacherName,
                    "approvedAt" to firestoreService.serverTimestamp(),
                    "remarks" to remarks,
                    "attendanceStamped" to false
                )
            )

            // Write a pushRequest so admin fires push + stamps attendance
            if (leave != null) {
                try {
                    val reqId = "${schoolCode}_leave_approve_${leaveId}"
                    firestoreService.setDocument("pushRequests", reqId, mapOf(
                        "schoolId"  to schoolCode,
                        "studentId" to leave.applicantId,
                        "mark"      to "LEAVE_APPROVED",
                        "class"     to leave.className,
                        "section"   to leave.section,
                        "day"       to 0,
                        "month"     to "",
                        "date"      to "",
                        "source"    to "teacher_leave_approve",
                        "markedBy"  to teacherName,
                        "status"    to "pending",
                        "leaveId"   to leaveId,
                        "startDate" to leave.startDate,
                        "endDate"   to leave.endDate,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    ), merge = false)
                } catch (_: Exception) { /* push is best-effort */ }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject a student's leave application.
     */
    suspend fun rejectStudentLeave(leaveId: String, remarks: String): Result<Unit> {
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName() ?: teacherId
        val schoolCode = getSchoolCode() ?: ""

        return try {
            // Read the leave doc first to get student info for the push
            val leave = firestoreService.getDocumentAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS, leaveId
            )

            firestoreService.updateDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                mapOf(
                    "status" to "rejected",
                    "approvedBy" to teacherName,
                    "approvedAt" to firestoreService.serverTimestamp(),
                    "remarks" to remarks
                )
            )

            // Fire push to parent via pushRequests (admin processes it)
            if (leave != null) {
                try {
                    val reqId = "${schoolCode}_leave_reject_${leaveId}"
                    firestoreService.setDocument("pushRequests", reqId, mapOf(
                        "schoolId"  to schoolCode,
                        "studentId" to leave.applicantId,
                        "mark"      to "LEAVE_REJECTED",
                        "class"     to leave.className,
                        "section"   to leave.section,
                        "day"       to 0,
                        "month"     to "",
                        "date"      to "",
                        "source"    to "teacher_leave_reject",
                        "markedBy"  to teacherName,
                        "status"    to "pending",
                        "leaveId"   to leaveId,
                        "remarks"   to remarks,
                        "startDate" to leave.startDate,
                        "endDate"   to leave.endDate,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    ), merge = false)
                } catch (_: Exception) { /* push is best-effort */ }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherName(): String? {
        return tokenManager.userName.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
