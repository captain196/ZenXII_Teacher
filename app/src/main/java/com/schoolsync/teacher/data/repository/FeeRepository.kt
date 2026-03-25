package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassFeeOverview
import com.schoolsync.teacher.data.model.FeeDefaulter
import com.schoolsync.teacher.data.model.StudentFeeStatus
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-backed Fee repository used by [FeesTeacherViewModel].
 * Teachers can view fee overview, student statuses, and defaulter lists
 * for their assigned classes (read-only).
 */
@Singleton
class FeeRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Get an aggregated fee overview for a class/section.
     */
    suspend fun getClassFeeOverview(
        className: String,
        section: String
    ): Result<ClassFeeOverview> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))

            val cls = Constants.classKey(className)
            val sec = Constants.sectionKey(section)

            // Read pending fees for the class/section
            val pendingPath = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/$cls/$sec/${Constants.Firebase.PENDING_FEES}"
            val snapshot = firebaseService.readSnapshot(pendingPath)

            var totalStudents = 0
            var paidCount = 0
            var defaulterCount = 0
            var totalCollected = 0.0
            var totalPending = 0.0
            var examBlockedCount = 0
            var resultWithheldCount = 0

            for (child in snapshot.children) {
                totalStudents++
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                val pending = (data["pending"] as? Number)?.toDouble()
                    ?: (data["totalPending"] as? Number)?.toDouble() ?: 0.0
                val paid = (data["paid"] as? Number)?.toDouble()
                    ?: (data["totalPaid"] as? Number)?.toDouble() ?: 0.0
                val examBlocked = data["examBlocked"] as? Boolean ?: false
                val resultWithheld = data["resultWithheld"] as? Boolean ?: false

                totalCollected += paid
                totalPending += pending
                if (pending <= 0) paidCount++
                if (pending > 0) defaulterCount++
                if (examBlocked) examBlockedCount++
                if (resultWithheld) resultWithheldCount++
            }

            Result.success(
                ClassFeeOverview(
                    className = cls,
                    section = sec,
                    totalStudents = totalStudents,
                    paidCount = paidCount,
                    defaulterCount = defaulterCount,
                    totalCollected = totalCollected,
                    totalPending = totalPending,
                    examBlockedCount = examBlockedCount,
                    resultWithheldCount = resultWithheldCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get fee status for each student in a class/section.
     */
    suspend fun getStudentFeeStatuses(
        className: String,
        section: String
    ): Result<List<StudentFeeStatus>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))

            val cls = Constants.classKey(className)
            val sec = Constants.sectionKey(section)

            val pendingPath = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/$cls/$sec/${Constants.Firebase.PENDING_FEES}"
            val snapshot = firebaseService.readSnapshot(pendingPath)

            val statuses = mutableListOf<StudentFeeStatus>()
            for (child in snapshot.children) {
                val studentId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue

                statuses.add(
                    StudentFeeStatus(
                        studentId = studentId,
                        studentName = data["studentName"]?.toString()
                            ?: data["name"]?.toString() ?: "",
                        rollNo = data["rollNo"]?.toString()
                            ?: data["roll_no"]?.toString() ?: "",
                        totalPending = (data["pending"] as? Number)?.toDouble()
                            ?: (data["totalPending"] as? Number)?.toDouble() ?: 0.0,
                        isDefaulter = (data["isDefaulter"] as? Boolean) ?: false,
                        examBlocked = (data["examBlocked"] as? Boolean) ?: false,
                        resultWithheld = (data["resultWithheld"] as? Boolean) ?: false,
                        lastPaymentDate = data["lastPaymentDate"]?.toString() ?: "",
                        fatherName = data["fatherName"]?.toString()
                            ?: data["father_name"]?.toString() ?: "",
                        phone = data["phone"]?.toString() ?: ""
                    )
                )
            }

            Result.success(statuses.sortedBy { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the list of fee defaulters for a class/section.
     */
    suspend fun getDefaulterList(
        className: String,
        section: String
    ): Result<List<FeeDefaulter>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))

            val cls = Constants.classKey(className)
            val sec = Constants.sectionKey(section)

            val defaulterPath = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/$cls/$sec/${Constants.Firebase.FEE_DEFAULTERS}"
            val snapshot = firebaseService.readSnapshot(defaulterPath)

            val defaulters = mutableListOf<FeeDefaulter>()
            for (child in snapshot.children) {
                val studentId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue

                @Suppress("UNCHECKED_CAST")
                val unpaidMonths = (data["unpaidMonths"] as? List<String>) ?: emptyList()

                defaulters.add(
                    FeeDefaulter(
                        studentId = studentId,
                        studentName = data["studentName"]?.toString()
                            ?: data["name"]?.toString() ?: "",
                        rollNo = data["rollNo"]?.toString()
                            ?: data["roll_no"]?.toString() ?: "",
                        totalDues = (data["totalDues"] as? Number)?.toDouble()
                            ?: (data["pending"] as? Number)?.toDouble() ?: 0.0,
                        unpaidMonths = unpaidMonths,
                        examBlocked = (data["examBlocked"] as? Boolean) ?: false,
                        resultWithheld = (data["resultWithheld"] as? Boolean) ?: false,
                        fatherName = data["fatherName"]?.toString()
                            ?: data["father_name"]?.toString() ?: "",
                        phone = data["phone"]?.toString() ?: ""
                    )
                )
            }

            Result.success(defaulters.sortedByDescending { it.totalDues })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
