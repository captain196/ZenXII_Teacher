package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.LibraryBookDoc
import com.schoolsync.teacher.data.model.firestore.LibraryFineDoc
import com.schoolsync.teacher.data.model.firestore.LibraryIssueDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the Library feature on the teacher side.
 *
 * Provides read access to the book catalogue, issue tracking,
 * overdue monitoring, and fine management.
 *
 * Collections used:
 * - libraryBooks: book catalogue
 * - libraryIssues: books issued to students/staff
 * - libraryFines: outstanding fines
 */
@Singleton
class LibraryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Catalogue ────────────────────────────────────────────────────────

    /**
     * Fetch all books for the school, optionally filtering by title prefix.
     */
    suspend fun getBooks(query: String = ""): Result<List<LibraryBookDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val books = if (query.isBlank()) {
                firestoreService.queryDocumentsAs<LibraryBookDoc>(
                    Constants.Firestore.LIBRARY_BOOKS
                ) { ref ->
                    ref.whereEqualTo("schoolId", schoolCode)
                        .limit(100)
                }
            } else {
                val lowerQuery = query.lowercase().trim()
                val upperBound = lowerQuery + "\uf8ff"
                firestoreService.queryDocumentsAs<LibraryBookDoc>(
                    Constants.Firestore.LIBRARY_BOOKS
                ) { ref ->
                    ref.whereEqualTo("schoolId", schoolCode)
                        .whereGreaterThanOrEqualTo("searchTitle", lowerQuery)
                        .whereLessThanOrEqualTo("searchTitle", upperBound)
                        .limit(50)
                }
            }
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Issues ───────────────────────────────────────────────────────────

    /**
     * Fetch all currently issued books for the school.
     */
    suspend fun getIssuedBooks(): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "issued")
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all overdue books for the school.
     */
    suspend fun getOverdueBooks(): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "overdue")
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all issue records for the school, ordered by issue date descending.
     */
    suspend fun getAllIssues(): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("issueDate", Query.Direction.DESCENDING)
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch issue records for a specific student.
     */
    suspend fun getStudentIssues(studentId: String): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("borrowerId", studentId)
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Fines ────────────────────────────────────────────────────────────

    /**
     * Fetch all pending (unpaid) fines for the school.
     */
    suspend fun getFines(): Result<List<LibraryFineDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val fines = firestoreService.queryDocumentsAs<LibraryFineDoc>(
                Constants.Firestore.LIBRARY_FINES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "pending")
            }
            Result.success(fines)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
