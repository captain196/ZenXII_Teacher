package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.BehaviorSummaryDoc
import com.schoolsync.teacher.data.model.firestore.HostelAllocationDoc
import com.schoolsync.teacher.data.model.firestore.IncidentDoc
import com.schoolsync.teacher.data.model.firestore.LibraryBookDoc
import com.schoolsync.teacher.data.model.firestore.LibraryFineDoc
import com.schoolsync.teacher.data.model.firestore.LibraryIssueDoc
import com.schoolsync.teacher.data.model.firestore.LostFoundDoc
import com.schoolsync.teacher.data.model.firestore.MealMenuDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for campus life features from the teacher side.
 * Includes hostel, library (with issue/return), behavior (with write access),
 * and lost & found.
 *
 * Collections used:
 * - hostelAllocations: per-student hostel room assignments
 * - mealMenus: current meal schedules
 * - libraryBooks: book catalogue
 * - libraryIssues: books issued to students/staff
 * - libraryFines: outstanding fines
 * - behaviorSummary: aggregated behavior metrics per student
 * - incidents: individual behavior incidents
 * - lostFound: lost and found item listings
 */
@Singleton
class CampusLifeFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Hostel ──────────────────────────────────────────────────────────────

    /**
     * Fetch hostel allocation for a specific student.
     * Document ID pattern: {schoolId}_{studentId}
     */
    suspend fun getHostelAllocation(studentId: String): Result<HostelAllocationDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docId = "${schoolCode}_${studentId}"
            val doc = firestoreService.getDocumentAs<HostelAllocationDoc>(
                Constants.Firestore.HOSTEL_ALLOCATIONS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the current meal menu for the school.
     * Document ID: {schoolId}_current
     */
    suspend fun getMealMenu(): Result<MealMenuDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docId = "${schoolCode}_current"
            val doc = firestoreService.getDocumentAs<MealMenuDoc>(
                Constants.Firestore.MEAL_MENUS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Library ─────────────────────────────────────────────────────────────

    /**
     * Search the library catalogue by title prefix.
     */
    suspend fun searchBooks(query: String): Result<List<LibraryBookDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val lowerQuery = query.lowercase().trim()
            val upperBound = lowerQuery + "\uf8ff"
            val books = firestoreService.queryDocumentsAs<LibraryBookDoc>(
                Constants.Firestore.LIBRARY_BOOKS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereGreaterThanOrEqualTo("searchTitle", lowerQuery)
                    .whereLessThanOrEqualTo("searchTitle", upperBound)
                    .limit(50)
            }
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all books currently issued to a borrower (student or staff).
     */
    suspend fun getMyIssuedBooks(studentId: String): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("borrowerId", studentId)
                    .whereEqualTo("status", "issued")
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch outstanding library fines for a student.
     *
     * If the `libraryFines` Firestore rule isn't deployed yet, this query
     * fails with PERMISSION_DENIED. We treat that as "no fines" rather than
     * surfacing an error to the UI, so the rest of the library screen stays
     * usable during local testing. Other errors bubble up normally.
     */
    suspend fun getMyFines(studentId: String): Result<List<LibraryFineDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val fines = firestoreService.queryDocumentsAs<LibraryFineDoc>(
                Constants.Firestore.LIBRARY_FINES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("paid", false)
            }
            Result.success(fines)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                android.util.Log.w(
                    "CampusLifeRepo",
                    "libraryFines rule not deployed yet — returning empty list"
                )
                Result.success(emptyList())
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Issue a book to a borrower. Creates a new library issue document.
     * Returns the generated issue document ID.
     *
     * Teacher-only operation.
     */
    suspend fun issueBook(
        bookId: String,
        borrowerId: String,
        borrowerName: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        val docId = "${schoolCode}_${bookId}_${System.currentTimeMillis()}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "bookId" to bookId,
            "borrowerId" to borrowerId,
            "borrowerName" to borrowerName,
            "issuedBy" to teacherId,
            "status" to "issued",
            "issuedAt" to FieldValue.serverTimestamp(),
            "dueDate" to null,
            "returnedAt" to null
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.LIBRARY_ISSUES,
                docId,
                data
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a book as returned by updating the issue document.
     *
     * Teacher-only operation.
     */
    suspend fun returnBook(issueId: String): Result<Unit> {
        return try {
            firestoreService.updateDocument(
                Constants.Firestore.LIBRARY_ISSUES,
                issueId,
                mapOf(
                    "status" to "returned",
                    "returnedAt" to FieldValue.serverTimestamp()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Behavior ────────────────────────────────────────────────────────────

    /**
     * Fetch the aggregated behavior summary for a student.
     * Document ID pattern: {schoolId}_{session}_{studentId}
     */
    suspend fun getBehaviorSummary(studentId: String): Result<BehaviorSummaryDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val docId = "${schoolCode}_${session}_${studentId}"
            val doc = firestoreService.getDocumentAs<BehaviorSummaryDoc>(
                Constants.Firestore.BEHAVIOR_SUMMARY,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch behavior incidents for a student, ordered by most recent first.
     */
    suspend fun getIncidents(studentId: String): Result<List<IncidentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val incidents = firestoreService.queryDocumentsAs<IncidentDoc>(
                Constants.Firestore.INCIDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(incidents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Log a new behavior incident for a student. Returns the generated document ID.
     *
     * Teacher-only operation.
     */
    suspend fun logIncident(
        studentId: String,
        studentName: String,
        sectionKey: String,
        category: String,
        severity: String,
        description: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "session" to session,
            "studentId" to studentId,
            "studentName" to studentName,
            "sectionKey" to sectionKey,
            "category" to category,
            "severity" to severity,
            "description" to description,
            "loggedBy" to teacherId,
            "loggedByName" to teacherName,
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.INCIDENTS,
                docId,
                data
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Award or deduct behavior points for a student.
     * Updates the behavior summary document with the point adjustment.
     *
     * Teacher-only operation.
     *
     * @param type "award" or "deduct"
     * @param points number of points (always positive; sign determined by type)
     * @param category e.g. "academic", "discipline", "sports"
     * @param reason description of why points were awarded/deducted
     */
    suspend fun awardPoints(
        studentId: String,
        type: String,
        points: Int,
        category: String,
        reason: String
    ): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        val summaryDocId = "${schoolCode}_${session}_${studentId}"
        val delta = if (type == "award") points.toLong() else -points.toLong()

        return try {
            firestoreService.updateDocument(
                Constants.Firestore.BEHAVIOR_SUMMARY,
                summaryDocId,
                mapOf(
                    "totalPoints" to FieldValue.increment(delta),
                    "lastUpdatedBy" to teacherId,
                    "lastUpdatedAt" to FieldValue.serverTimestamp(),
                    "lastAction" to mapOf(
                        "type" to type,
                        "points" to points,
                        "category" to category,
                        "reason" to reason,
                        "by" to teacherId,
                        "at" to FieldValue.serverTimestamp()
                    )
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Lost & Found ────────────────────────────────────────────────────────

    /**
     * Fetch all active lost and found items for the school.
     */
    suspend fun getLostFoundItems(): Result<List<LostFoundDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val items = firestoreService.queryDocumentsAs<LostFoundDoc>(
                Constants.Firestore.LOST_FOUND
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("resolved", false)
                    .orderBy("reportedAt", Query.Direction.DESCENDING)
                    .limit(100)
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Report a lost item from the teacher side. Returns the generated document ID.
     */
    suspend fun reportLostItem(
        description: String,
        category: String,
        location: String,
        photo: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "description" to description,
            "category" to category,
            "location" to location,
            "photo" to photo,
            "type" to "lost",
            "reportedBy" to teacherId,
            "reportedByName" to teacherName,
            "reportedByRole" to "teacher",
            "resolved" to false,
            "reportedAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.LOST_FOUND,
                docId,
                data
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }
}
