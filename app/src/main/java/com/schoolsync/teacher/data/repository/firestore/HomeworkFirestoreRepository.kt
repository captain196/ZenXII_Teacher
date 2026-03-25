package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.HomeworkDoc
import com.schoolsync.teacher.data.model.firestore.SubmissionDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for full CRUD homework operations from the teacher side.
 *
 * Collections used:
 * - homework: class-level homework documents created by teachers
 * - submissions: per-student submission documents with review capability
 */
@Singleton
class HomeworkFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Create a new homework assignment.
     *
     * @return the auto-generated document ID (hwId)
     */
    suspend fun createHomework(
        title: String,
        description: String,
        subject: String,
        className: String,
        section: String,
        dueDate: String,
        teacherId: String,
        teacherName: String,
        totalStudents: Int,
        attachments: List<String> = emptyList()
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val cls = Constants.classKey(className)
        val sec = Constants.sectionKey(section)
        val sectionKey = "${cls}/${sec}"
        val hwId = "${schoolCode}_${System.currentTimeMillis()}"

        val data = hashMapOf(
            "schoolId" to schoolCode,
            "session" to session,
            "className" to cls,
            "section" to sec,
            "sectionKey" to sectionKey,
            "title" to title,
            "description" to description,
            "subject" to subject,
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "dueDate" to dueDate,
            "createdAt" to firestoreService.serverTimestamp(),
            "status" to "active",
            "submissionCount" to 0,
            "totalStudents" to totalStudents,
            "attachments" to attachments
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.HOMEWORK,
                hwId,
                data
            )
            Result.success(hwId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all homework for a section, ordered by creation date descending.
     * Query: schoolId + sectionKey, ordered by createdAt desc.
     */
    suspend fun getHomework(sectionKey: String): Result<List<HomeworkDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val homework = firestoreService.queryDocumentsAs<HomeworkDoc>(
                Constants.Firestore.HOMEWORK
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(homework)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all submissions for a specific homework assignment.
     * Query: homeworkId match.
     */
    suspend fun getSubmissions(homeworkId: String): Result<List<SubmissionDoc>> {
        return try {
            val submissions = firestoreService.queryDocumentsAs<SubmissionDoc>(
                Constants.Firestore.SUBMISSIONS
            ) { ref ->
                ref.whereEqualTo("homeworkId", homeworkId)
            }
            Result.success(submissions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Review a student's submission: add remark, score, and mark as "reviewed".
     */
    suspend fun reviewSubmission(
        submissionId: String,
        remark: String,
        score: Int,
        reviewedBy: String
    ): Result<Unit> {
        return try {
            firestoreService.updateDocument(
                Constants.Firestore.SUBMISSIONS,
                submissionId,
                mapOf(
                    "remark" to remark,
                    "score" to score,
                    "status" to "reviewed",
                    "reviewedBy" to reviewedBy,
                    "reviewedAt" to firestoreService.serverTimestamp()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Close a homework assignment (no further submissions accepted).
     */
    suspend fun closeHomework(homeworkId: String): Result<Unit> {
        return try {
            firestoreService.updateDocument(
                Constants.Firestore.HOMEWORK,
                homeworkId,
                mapOf("status" to "closed")
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a homework document.
     */
    suspend fun deleteHomework(homeworkId: String): Result<Unit> {
        return try {
            firestoreService.deleteDocument(
                Constants.Firestore.HOMEWORK,
                homeworkId
            )
            Result.success(Unit)
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
