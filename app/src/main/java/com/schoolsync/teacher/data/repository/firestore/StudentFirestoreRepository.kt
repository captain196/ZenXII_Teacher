package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StudentDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading student data from Firestore.
 * Collection: students
 *
 * All queries are scoped to the teacher's school via [TokenManager.schoolCode].
 */
@Singleton
class StudentFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch a single student document by ID.
     */
    suspend fun getStudent(studentId: String): Result<StudentDoc> {
        return try {
            val doc = firestoreService.getDocumentAs<StudentDoc>(
                Constants.Firestore.STUDENTS,
                studentId
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("Student not found: $studentId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all Active students in a specific class and section within
     * the teacher's school.
     *
     * B3 \u2014 Restricted to status='Active'. Pre-fix, this returned every
     * student in the class regardless of status, so withdrawn / TC'd /
     * deleted students surfaced in class drilldowns and search.
     */
    suspend fun getStudentsByClass(className: String, section: String): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("className", Constants.classKey(className))
                    .whereEqualTo("section", Constants.sectionKey(section))
                    .whereEqualTo("status", "Active")
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all Active students belonging to the teacher's school.
     *
     * B3 \u2014 Restricted to status='Active'.
     */
    suspend fun getStudentsBySchool(): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "Active")
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search Active students by name within the teacher's school.
     *
     * Uses Firestore range query on the [name] field to match names
     * starting with the given [query] string (case-sensitive prefix match).
     *
     * B3 \u2014 Restricted to status='Active'.
     */
    suspend fun searchStudentsByName(query: String): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        if (query.isBlank()) return Result.success(emptyList())

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "Active")
                    .whereGreaterThanOrEqualTo("name", query)
                    .whereLessThanOrEqualTo("name", query + "\uf8ff")
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a single student document for real-time updates.
     *
     * B4 — Pre-fix this passed bare `studentId` as the docId, but the
     * canonical Firestore docId is `{schoolId}_{studentId}` (matches
     * the admin writer). Emits `null` when schoolCode isn't yet known
     * (pre-login state) or when the doc doesn't exist.
     */
    fun observeStudent(studentId: String): Flow<StudentDoc?> = flow {
        val schoolCode = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
        if (schoolCode == null) {
            emit(null)
            return@flow
        }
        val docId = "${schoolCode}_$studentId"
        firestoreService.observeDocumentAs<StudentDoc>(
            Constants.Firestore.STUDENTS,
            docId
        ).collect { emit(it) }
    }.catch { emit(null) }   // listener error → emit null, keep student flow alive (no UI crash)

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
