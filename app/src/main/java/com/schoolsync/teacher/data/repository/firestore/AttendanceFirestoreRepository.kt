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
 * Repository for READING teacher-side attendance from Firestore.
 *
 * Writes are NOT performed here. Attendance is written exclusively through the
 * server-authoritative endpoint (POST attendance/save via ApiService) — the
 * single source of truth that enforces the stage-gate, holiday and lock rules
 * and the audit trail. The former direct-Firestore writers
 * (markAttendance / updateAttendanceSummary) were removed: they bypassed that
 * server path and had zero callers.
 *
 * Collections read:
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


    /** "April 2026" → "2026-04". Pass-through if already YYYY-MM. */
    private fun monthLabelToKey(monthOrKey: String): String {
        val s = monthOrKey.trim()
        if (s.matches(Regex("^\\d{4}-\\d{2}$"))) return s
        val parts = s.split(" ")
        if (parts.size != 2) return s
        val year = parts[1].toIntOrNull() ?: return s
        val monthNum = monthNameToNumber(parts[0]) ?: return s
        return "%d-%02d".format(year, monthNum)
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
