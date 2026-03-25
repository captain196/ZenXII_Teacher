package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.FeeDemandDoc
import com.schoolsync.teacher.data.model.firestore.FeeDefaulterDoc
import com.schoolsync.teacher.data.model.firestore.FeeStructureDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading fee-related data from Firestore (teacher-side, read-only).
 *
 * Teachers can view fee structures, class defaulter lists, and individual
 * student fee statuses to support academic decisions (exam blocking, etc.).
 *
 * Collections used:
 * - feeStructures: class/section fee breakdown per session
 * - feeDemands: monthly fee demands per student
 * - feeDefaulters: defaulter flags per student
 */
@Singleton
class FeeFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the fee structure for a specific class and section.
     * Doc ID pattern: `{schoolId}_{session}_{className}_{section}`
     */
    suspend fun getFeeStructure(
        className: String,
        section: String
    ): Result<FeeStructureDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val docId = "${schoolCode}_${session}_${Constants.classKey(className)}_${Constants.sectionKey(section)}"

        return try {
            val doc = firestoreService.getDocumentAs<FeeStructureDoc>(
                Constants.Firestore.FEE_STRUCTURES,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all fee defaulters for a specific class and section in the current session.
     * Query: schoolId + session, then filter by className + section.
     */
    suspend fun getClassDefaulters(
        className: String,
        section: String
    ): Result<List<FeeDefaulterDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val defaulters = firestoreService.queryDocumentsAs<FeeDefaulterDoc>(
                Constants.Firestore.FEE_DEFAULTERS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", Constants.classKey(className))
                    .whereEqualTo("section", Constants.sectionKey(section))
            }
            Result.success(defaulters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all fee demands for a specific student in the current session.
     */
    suspend fun getStudentFeeStatus(studentId: String): Result<List<FeeDemandDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val demands = firestoreService.queryDocumentsAs<FeeDemandDoc>(
                Constants.Firestore.FEE_DEMANDS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("month")
            }
            Result.success(demands)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the defaulter status for a specific student.
     * Doc ID pattern: `{schoolId}_{studentId}`
     */
    suspend fun getStudentDefaulterStatus(studentId: String): Result<FeeDefaulterDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val docId = "${schoolCode}_${studentId}"

        return try {
            val doc = firestoreService.getDocumentAs<FeeDefaulterDoc>(
                Constants.Firestore.FEE_DEFAULTERS,
                docId
            )
            Result.success(doc)
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
}
