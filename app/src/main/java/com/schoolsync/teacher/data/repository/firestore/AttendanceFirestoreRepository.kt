package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.AttendanceDoc
import com.schoolsync.teacher.data.model.firestore.AttendanceSummaryDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for attendance operations from the teacher side.
 * Supports reading and writing attendance records and summaries.
 *
 * Collections used:
 * - attendance: daily per-student records
 * - attendanceSummary: monthly rollups with dayWise string and computed stats
 */
@Singleton
class AttendanceFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Mark attendance for an entire section on a given date.
     *
     * @param sectionKey e.g. "9th_A"
     * @param date ISO date string e.g. "2026-03-24"
     * @param studentStatuses Map of studentId to Pair(status, studentName)
     * @return count of successfully written documents
     */
    suspend fun markAttendance(
        sectionKey: String,
        date: String,
        studentStatuses: Map<String, Pair<String, String>>
    ): Result<Int> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        return try {
            var count = 0
            for ((studentId, statusPair) in studentStatuses) {
                val (status, studentName) = statusPair
                val docId = "${schoolCode}_${date}_${studentId}"
                val data = hashMapOf(
                    "schoolId" to schoolCode,
                    "session" to session,
                    "date" to date,
                    "sectionKey" to sectionKey,
                    "studentId" to studentId,
                    "studentName" to studentName,
                    "status" to status,
                    "markedBy" to teacherId,
                    "markedAt" to firestoreService.serverTimestamp(),
                    "late" to false,
                    "lateMinutes" to 0,
                    "notified" to false
                )
                firestoreService.setDocument(
                    Constants.Firestore.ATTENDANCE,
                    docId,
                    data,
                    merge = true
                )
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all attendance records for a section on a given date.
     * Query: schoolId + sectionKey + date.
     */
    suspend fun getAttendanceForSection(
        sectionKey: String,
        date: String
    ): Result<List<AttendanceDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val records = firestoreService.queryDocumentsAs<AttendanceDoc>(
                Constants.Firestore.ATTENDANCE
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("date", date)
            }
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a student's attendance summary for a specific month.
     * Uses direct document read with ID pattern: {schoolId}_{session}_{month}_{studentId}.
     */
    suspend fun getStudentAttendanceSummary(
        studentId: String,
        month: String
    ): Result<AttendanceSummaryDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val docId = "${schoolCode}_${session}_${month}_${studentId}"

        return try {
            val doc = firestoreService.getDocumentAs<AttendanceSummaryDoc>(
                Constants.Firestore.ATTENDANCE_SUMMARY,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recompute and write the attendance summary for a student and month.
     *
     * Parses the [dayWise] string (e.g. "PPAPLHV...") to count each status character
     * and computes percentage as: present / (totalDays - holiday - vacation) * 100.
     *
     * @param studentId the student's ID
     * @param studentName the student's display name
     * @param sectionKey e.g. "9th_A"
     * @param month e.g. "March 2026"
     * @param dayWise the full month attendance string
     */
    suspend fun updateAttendanceSummary(
        studentId: String,
        studentName: String,
        sectionKey: String,
        month: String,
        dayWise: String
    ): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        // Count each status character
        val present = dayWise.count { it == 'P' }
        val absent = dayWise.count { it == 'A' }
        val leave = dayWise.count { it == 'L' }
        val holiday = dayWise.count { it == 'H' }
        val trip = dayWise.count { it == 'T' }
        val vacation = dayWise.count { it == 'V' }
        val totalDays = dayWise.length
        val workingDays = totalDays - holiday - vacation
        val percentage = if (workingDays > 0) {
            (present.toDouble() / workingDays) * 100.0
        } else {
            0.0
        }

        val docId = "${schoolCode}_${session}_${month}_${studentId}"
        val data = hashMapOf(
            "schoolId" to schoolCode,
            "session" to session,
            "month" to month,
            "studentId" to studentId,
            "studentName" to studentName,
            "sectionKey" to sectionKey,
            "dayWise" to dayWise,
            "present" to present,
            "absent" to absent,
            "late" to trip,
            "leave" to leave,
            "holiday" to holiday,
            "vacation" to vacation,
            "totalDays" to totalDays,
            "workingDays" to workingDays,
            "percentage" to percentage,
            "updatedAt" to firestoreService.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.ATTENDANCE_SUMMARY,
                docId,
                data,
                merge = false
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
