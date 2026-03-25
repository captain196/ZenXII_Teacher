package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StudentDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
     * Fetch all students in a specific class and section within the teacher's school.
     */
    suspend fun getStudentsByClass(className: String, section: String): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("className", className)
                    .whereEqualTo("section", section)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all students belonging to the teacher's school.
     */
    suspend fun getStudentsBySchool(): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search students by name within the teacher's school.
     *
     * Uses Firestore range query on the [name] field to match names
     * starting with the given [query] string (case-sensitive prefix match).
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
     */
    fun observeStudent(studentId: String): Flow<StudentDoc?> {
        return firestoreService.observeDocumentAs<StudentDoc>(
            Constants.Firestore.STUDENTS,
            studentId
        )
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
