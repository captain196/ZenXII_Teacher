package com.schoolsync.teacher.data.repository.firestore

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.AttendanceDoc
import com.schoolsync.teacher.data.model.firestore.AttendanceSummaryDoc
import com.schoolsync.teacher.data.remote.ApiService
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
    private val tokenManager: TokenManager,
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "AttendanceFirestoreRepo"
    }

    /**
     * Mark attendance for an entire section on a given date.
     *
     * @param sectionKey e.g. "Class 9th/Section A"
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
                // Prior status for this student+date — used to notify the parent
                // ONLY when the status actually changes to A/T, so re-saving the
                // same attendance (or correcting an unrelated field) never
                // re-sends a duplicate "Absent/Late" push.
                val prevStatus = try {
                    firestoreService.getDocumentAs<AttendanceDoc>(
                        Constants.Firestore.ATTENDANCE, docId
                    )?.status
                } catch (_: Exception) { null }
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

                // Fire parent push for A/T marks via the Firestore pushRequests
                // collection (admin poller dispatches). Only on a real status
                // change (see prevStatus above) to avoid duplicate alerts.
                if ((status == "A" || status == "T") && status != prevStatus) {
                    try {
                        val parts = sectionKey.split("/")
                        val cls = parts.getOrNull(0)?.trim() ?: ""
                        val sec = parts.getOrNull(1)?.replace("Section ", "")?.trim() ?: ""
                        val dayOfMonth = date.split("-").getOrNull(2)?.toIntOrNull() ?: 0
                        val monthName = java.time.LocalDate.parse(date).month
                            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)

                        val reqId = "${schoolCode}_${studentId}_${date}_${status}"
                        firestoreService.setDocument("pushRequests", reqId, mapOf(
                            "schoolId"  to schoolCode,
                            "studentId" to studentId,
                            "mark"      to status,
                            "class"     to cls,
                            "section"   to sec,
                            "day"       to dayOfMonth,
                            "month"     to monthName,
                            "date"      to date,
                            "source"    to "teacher",
                            "markedBy"  to teacherId,
                            "status"    to "pending",
                            "createdAt" to com.google.firebase.Timestamp.now()
                        ), merge = false)
                        Log.d(TAG, "Firestore push request written for $studentId ($status)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Push request write failed (non-fatal)", e)
                    }
                }
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
     * Fetch every student's attendance summary for one class+month in a
     * single query. Replaces the per-student N+1 pattern that used to
     * dominate dashboard load time.
     *
     * @param sectionKey e.g. "Class 8th/Section A"
     * @param month either "Month YYYY" (e.g. "April 2026") or
     *              "YYYY-MM" (e.g. "2026-04") — both accepted.
     */
    suspend fun getClassMonthlySummaries(
        sectionKey: String,
        month: String
    ): Result<List<AttendanceSummaryDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val monthKey = monthLabelToKey(month)
        return try {
            val docs = firestoreService.queryDocumentsAs<AttendanceSummaryDoc>(
                Constants.Firestore.ATTENDANCE_SUMMARY
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("month", monthKey)
            }
            Result.success(docs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a student's attendance summary for a specific month.
     *
     * Doc id format: `{schoolId}_{studentId}_{YYYY-MM}` — matches what
     * admin `save_student_attendance` and the parent app both use.
     *
     * @param month either "Month YYYY" (e.g. "April 2026") or
     *              "YYYY-MM" (e.g. "2026-04") — both accepted.
     */
    suspend fun getStudentAttendanceSummary(
        studentId: String,
        month: String
    ): Result<AttendanceSummaryDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val monthKey = monthLabelToKey(month)
        val docId = "${schoolCode}_${studentId}_${monthKey}"

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
     * @param sectionKey e.g. "Class 9th/Section A"
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

        // Count each status character. T = Tardy (late arrival).
        val present = dayWise.count { it == 'P' }
        val absent = dayWise.count { it == 'A' }
        val leave = dayWise.count { it == 'L' }
        val holiday = dayWise.count { it == 'H' }
        val tardy = dayWise.count { it == 'T' }
        val vacation = dayWise.count { it == 'V' }
        val totalDays = dayWise.length
        val workingDays = totalDays - holiday - vacation
        // Phase 9b: include tardy in numerator to match admin + parent formula
        val percentage = if (workingDays > 0) {
            ((present + tardy).toDouble() / workingDays) * 100.0
        } else {
            0.0
        }

        // Phase 7h (2026-04-08): canonical doc id matches admin
        // `save_student_attendance` so all three apps share one doc.
        val monthKey = monthLabelToKey(month)            // "2026-04"
        val monthLabelStr = monthKeyToLabel(monthKey)    // "April 2026"
        val canonicalDocId = "${schoolCode}_${studentId}_${monthKey}"

        // NOTE: do NOT write `lateTimes` here — that's the per-day
        // arrival-time map owned by the admin path. Overwriting it from
        // the teacher app would clobber the times the admin recorded.
        val data = hashMapOf(
            "schoolId" to schoolCode,
            "session" to session,
            "month" to monthKey,
            "monthLabel" to monthLabelStr,
            "studentId" to studentId,
            "studentName" to studentName,
            "sectionKey" to sectionKey,
            "type" to "student",
            "dayWise" to dayWise,
            "present" to present,
            "absent" to absent,
            "tardy" to tardy,
            "leave" to leave,
            "holiday" to holiday,
            "vacation" to vacation,
            "totalDays" to totalDays,
            "workingDays" to workingDays,
            "percentage" to percentage,
            "updatedAt" to firestoreService.serverTimestamp()
        )

        return try {
            // merge = true so we don't clobber `lateTimes` (admin-owned)
            // or any other field the admin write set.
            firestoreService.setDocument(
                Constants.Firestore.ATTENDANCE_SUMMARY,
                canonicalDocId,
                data,
                merge = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** "April 2026" → "2026-04". Pass-through if already YYYY-MM. */
    private fun monthLabelToKey(monthOrKey: String): String {
        val s = monthOrKey.trim()
        if (s.matches(Regex("^\\d{4}-\\d{2}$"))) return s
        val parts = s.split(" ")
        if (parts.size != 2) return s
        val year = parts[1].toIntOrNull() ?: return s
        val monthNum = monthNameToNumber(parts[0]) ?: return s
        return String.format(java.util.Locale.ROOT, "%d-%02d", year, monthNum)
    }

    /** "2026-04" → "April 2026". Pass-through if not YYYY-MM. */
    private fun monthKeyToLabel(monthKey: String): String {
        val parts = monthKey.split("-")
        if (parts.size != 2) return monthKey
        val year = parts[0].toIntOrNull() ?: return monthKey
        val monthNum = parts[1].toIntOrNull() ?: return monthKey
        val name = monthNumberToName(monthNum) ?: return monthKey
        return "$name $year"
    }

    private fun monthNameToNumber(name: String): Int? = when (name.lowercase()) {
        "january" -> 1; "february" -> 2; "march" -> 3
        "april" -> 4; "may" -> 5; "june" -> 6
        "july" -> 7; "august" -> 8; "september" -> 9
        "october" -> 10; "november" -> 11; "december" -> 12
        else -> null
    }

    private fun monthNumberToName(n: Int): String? = when (n) {
        1 -> "January"; 2 -> "February"; 3 -> "March"
        4 -> "April"; 5 -> "May"; 6 -> "June"
        7 -> "July"; 8 -> "August"; 9 -> "September"
        10 -> "October"; 11 -> "November"; 12 -> "December"
        else -> null
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
