package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StaffDoc
import com.schoolsync.teacher.data.model.firestore.StaffPrivateDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
     * Fetch the current staff member's OWN staffPrivate doc (audit fix C1).
     * Doc id is the SAME as the staff doc: {schoolId}_{staffId}. Rules permit
     * reading only the owner's own doc.
     *
     * Fail-soft: a missing doc (pre-migration), a permission denial, or any
     * error returns null rather than throwing, so the caller falls back to the
     * inline staff-doc values and the profile never breaks.
     */
    suspend fun getStaffPrivate(staffId: String): StaffPrivateDoc? {
        return try {
            firestoreService.getDocumentAs<StaffPrivateDoc>(
                Constants.Firestore.STAFF_PRIVATE,
                staffId
            )
        } catch (e: Exception) {
            null
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
        ).catch { emit(null) }   // listener error → emit null, keep flow alive (no UI crash)
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
