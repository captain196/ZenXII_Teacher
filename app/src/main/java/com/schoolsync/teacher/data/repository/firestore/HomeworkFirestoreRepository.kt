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

            // HW-1: Write a push request so parents get notified
            try {
                val reqId = "${schoolCode}_hw_${hwId}"
                firestoreService.setDocument("pushRequests", reqId, mapOf(
                    "schoolId"    to schoolCode,
                    "studentId"   to "",
                    "mark"        to "HOMEWORK_CREATED",
                    "class"       to cls,
                    "section"     to sec,
                    "day"         to 0,
                    "month"       to "",
                    "date"        to "",
                    "source"      to "homework_created",
                    "markedBy"    to teacherName,
                    "status"      to "pending",
                    "homeworkId"  to hwId,
                    "title"       to title,
                    "subject"     to subject,
                    "dueDate"     to dueDate,
                    "sectionKey"  to sectionKey,
                    "createdAt"   to com.google.firebase.Timestamp.now()
                ), merge = false)
            } catch (_: Exception) { /* push is best-effort */ }

            Result.success(hwId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all homework for a section, ordered by creation date descending.
     *
     * Primary query: `schoolId + sectionKey + orderBy(createdAt desc)`. This
     * needs a composite index in Firestore. If the index isn't deployed yet
     * (FAILED_PRECONDITION), we automatically retry without the orderBy and
     * sort the result client-side so the screen still works during local
     * testing. The fallback path is logged so it stays visible in logcat.
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
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                android.util.Log.w(
                    "HomeworkRepo",
                    "Composite index missing for homework(schoolId+sectionKey+createdAt) — falling back to client-side sort"
                )
                runCatching {
                    val rows = firestoreService.queryDocumentsAs<HomeworkDoc>(
                        Constants.Firestore.HOMEWORK
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("sectionKey", sectionKey)
                    }
                    rows.sortedByDescending { row ->
                        // createdAt is Any? — could be a Firestore Timestamp,
                        // Long ms, or null if just written. Fall back to the
                        // doc id which embeds System.currentTimeMillis().
                        when (val ts = row.createdAt) {
                            is com.google.firebase.Timestamp -> ts.seconds
                            is Long -> ts / 1000
                            is Number -> ts.toLong() / 1000
                            else -> row.id.substringAfterLast('_', "0")
                                .toLongOrNull() ?: 0L
                        }
                    }
                }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { Result.failure(it) }
                )
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all submissions for a specific homework assignment.
     * Query: homeworkId match.
     */
    suspend fun getSubmissions(homeworkId: String): Result<List<SubmissionDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            // Must include schoolId in query — Firestore rules require
            // isSameSchool() which checks resource.data.schoolId matches
            // the auth token. Queries without it get PERMISSION_DENIED.
            val submissions = firestoreService.queryDocumentsAs<SubmissionDoc>(
                Constants.Firestore.SUBMISSIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("homeworkId", homeworkId)
            }
            Result.success(submissions)
        } catch (e: Exception) {
            android.util.Log.e("HomeworkRepo", "getSubmissions failed for $homeworkId", e)
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
        val schoolCode = getSchoolCode() ?: ""

        return try {
            // Read the submission first to get student info
            val submission = firestoreService.getDocumentAs<SubmissionDoc>(
                Constants.Firestore.SUBMISSIONS, submissionId
            )

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

            // HW-2: Push to parent when homework is graded
            if (submission != null && submission.studentId.isNotBlank()) {
                try {
                    val reqId = "${schoolCode}_hw_review_${submissionId}"
                    firestoreService.setDocument("pushRequests", reqId, mapOf(
                        "schoolId"     to schoolCode,
                        "studentId"    to submission.studentId,
                        "mark"         to "HOMEWORK_REVIEWED",
                        "class"        to "",
                        "section"      to "",
                        "day"          to 0,
                        "month"        to "",
                        "date"         to "",
                        "source"       to "homework_reviewed",
                        "markedBy"     to reviewedBy,
                        "status"       to "pending",
                        "homeworkId"   to submission.homeworkId,
                        "studentName"  to submission.studentName,
                        "score"        to score,
                        "remark"       to remark,
                        "createdAt"    to com.google.firebase.Timestamp.now()
                    ), merge = false)
                } catch (_: Exception) { /* push is best-effort */ }
            }

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
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
