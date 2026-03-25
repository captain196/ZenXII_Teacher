package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StaffDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading staff / teacher data from Firestore.
 * Collection: staff
 *
 * All list queries are scoped to the teacher's school via [TokenManager.schoolCode].
 */
@Singleton
class StaffFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch a single staff document by ID.
     */
    suspend fun getStaff(staffId: String): Result<StaffDoc> {
        return try {
            val doc = firestoreService.getDocumentAs<StaffDoc>(
                Constants.Firestore.STAFF,
                staffId
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("Staff not found: $staffId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all staff members belonging to the teacher's school.
     */
    suspend fun getStaffBySchool(): Result<List<StaffDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val staff = firestoreService.queryDocumentsAs<StaffDoc>(
                Constants.Firestore.STAFF
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
            }
            Result.success(staff)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch staff members filtered by department within the teacher's school.
     */
    suspend fun getStaffByDepartment(dept: String): Result<List<StaffDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val staff = firestoreService.queryDocumentsAs<StaffDoc>(
                Constants.Firestore.STAFF
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("department", dept)
            }
            Result.success(staff)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all staff with role "teacher" within the teacher's school.
     * Compound query: schoolId == schoolCode AND role == "teacher"
     */
    suspend fun getTeachers(): Result<List<StaffDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val teachers = firestoreService.queryDocumentsAs<StaffDoc>(
                Constants.Firestore.STAFF
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("role", "teacher")
            }
            Result.success(teachers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a single staff document for real-time updates.
     */
    fun observeStaff(staffId: String): Flow<StaffDoc?> {
        return firestoreService.observeDocumentAs<StaffDoc>(
            Constants.Firestore.STAFF,
            staffId
        )
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
