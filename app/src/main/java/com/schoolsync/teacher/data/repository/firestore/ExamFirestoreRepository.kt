package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.ExamDoc
import com.schoolsync.teacher.data.model.firestore.ExamScheduleDoc
import com.schoolsync.teacher.data.model.firestore.MarksDoc
import com.schoolsync.teacher.data.model.firestore.ResultDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for exam, marks, and result operations from the teacher side.
 * Supports reading exams/schedules/results and writing/batch-writing marks.
 *
 * Collections used:
 * - exams: exam definitions
 * - examSchedule: per-class-section subject-wise schedules
 * - marks: per-student per-subject marks entries
 * - results: computed per-student result documents
 */
@Singleton
class ExamFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all exams for the current school and session, ordered by start date.
     */
    suspend fun getExams(): Result<List<ExamDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val exams = firestoreService.queryDocumentsAs<ExamDoc>(
                Constants.Firestore.EXAMS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .orderBy("startDate")
            }
            Result.success(exams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the exam schedule for a specific exam, class, and section.
     * Doc ID pattern: `{schoolId}_{examId}_{className}_{section}`
     */
    suspend fun getExamSchedule(
        examId: String,
        className: String,
        section: String
    ): Result<ExamScheduleDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val docId = "${schoolCode}_${examId}_${Constants.classKey(className)}_${Constants.sectionKey(section)}"

        return try {
            val doc = firestoreService.getDocumentAs<ExamScheduleDoc>(
                Constants.Firestore.EXAM_SCHEDULE,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all marks for a specific exam, section, and subject.
     * Query: schoolId + examId + sectionKey + subject.
     */
    suspend fun getStudentMarks(
        examId: String,
        sectionKey: String,
        subject: String
    ): Result<List<MarksDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val marks = firestoreService.queryDocumentsAs<MarksDoc>(
                Constants.Firestore.MARKS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("examId", examId)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("subject", subject)
            }
            Result.success(marks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save marks for a single student in a specific exam/subject.
     * Doc ID pattern: `{schoolId}_{examId}_{sectionKey}_{subject}_{studentId}`
     *
     * Uses merge write so partial updates do not erase existing fields.
     */
    suspend fun saveMarks(
        examId: String,
        sectionKey: String,
        className: String,
        section: String,
        subject: String,
        studentId: String,
        studentName: String,
        theory: Double,
        practical: Double,
        total: Double,
        absent: Boolean
    ): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        val cls = Constants.classKey(className)
        val sec = Constants.sectionKey(section)
        // Match the admin's Exam_result_store::marksDocId exactly: hashed
        // class/section/subject tokens (NO raw '/' which is an invalid id).
        val docId = "${schoolCode}_${examId}_${Constants.idToken(cls)}_${Constants.idToken(sec)}_${Constants.idToken(subject)}_${studentId}"
        val data = hashMapOf(
            "schoolId" to schoolCode,
            "session" to session,
            "examId" to examId,
            "sectionKey" to sectionKey,
            "className" to cls,
            "section" to sec,
            "subject" to subject,
            "studentId" to studentId,
            "studentName" to studentName,
            "theory" to theory,
            "practical" to practical,
            "total" to total,
            "absent" to absent,
            "savedBy" to teacherId,
            "savedAt" to firestoreService.serverTimestamp(),
            "status" to "draft"
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.MARKS,
                docId,
                data,
                merge = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save marks for multiple students in a single batch.
     *
     * Each entry in [marksList] contains a [MarksDoc] with the student's data.
     * Returns the count of successfully written documents.
     *
     * Doc ID pattern: `{schoolId}_{examId}_{sectionKey}_{subject}_{studentId}`
     */
    suspend fun saveBatchMarks(
        examId: String,
        sectionKey: String,
        className: String,
        section: String,
        subject: String,
        marksList: List<MarksDoc>
    ): Result<Int> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        val cls = Constants.classKey(className)
        val sec = Constants.sectionKey(section)

        return try {
            // Build every student's marks doc, then commit them in ONE atomic
            // batch (single round-trip, all-or-nothing) instead of N sequential
            // setDocument calls. NOTE: marksAudit + examResultMeta are SERVER-ONLY
            // collections (Firestore rules deny client writes) — the admin compute
            // owns the audit trail + staleness flag, so the teacher writes marks
            // ONLY here. savedBy + savedAt record who last changed each mark.
            val items = marksList.map { entry ->
                // Match the admin's Exam_result_store::marksDocId exactly: hashed
                // class/section/subject tokens (NO raw '/' which is an invalid id).
                val docId = "${schoolCode}_${examId}_${Constants.idToken(cls)}_${Constants.idToken(sec)}_${Constants.idToken(subject)}_${entry.studentId}"
                val data: Any = hashMapOf(
                    "schoolId" to schoolCode,
                    "session" to session,
                    "examId" to examId,
                    "sectionKey" to sectionKey,
                    "className" to cls,
                    "section" to sec,
                    "subject" to subject,
                    "studentId" to entry.studentId,
                    "studentName" to entry.studentName,
                    // componentMarks is the CANONICAL shape the admin report cards /
                    // tabulation read component columns from ([{name, value}]); the
                    // VM builds it (omitting Practical when the subject has none).
                    "componentMarks" to entry.componentMarks,
                    "theory" to entry.theory,
                    "practical" to entry.practical,
                    "total" to entry.total,
                    "maxMarks" to entry.maxMarks,     // = subject.maxTotal (schedule)
                    "absent" to entry.absent,
                    "savedBy" to teacherId,
                    "savedAt" to firestoreService.serverTimestamp(),
                    "status" to "draft"
                )
                Triple(Constants.Firestore.MARKS, docId, data)
            }
            // Firestore caps a batch at 500 ops; chunk defensively for huge sections.
            items.chunked(450).forEach { chunk ->
                firestoreService.setDocumentsBatch(chunk, merge = true)
            }
            Result.success(items.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the result for a specific exam, section, and student.
     * Query: schoolId + examId + sectionKey + studentId.
     */
    suspend fun getResult(
        examId: String,
        sectionKey: String,
        studentId: String
    ): Result<ResultDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val results = firestoreService.queryDocumentsAs<ResultDoc>(
                Constants.Firestore.RESULTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("examId", examId)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("studentId", studentId)
                    .limit(1)
            }
            Result.success(results.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all results for a section in an exam, ordered by percentage descending
     * (useful for ranking view).
     */
    suspend fun getSectionResults(
        examId: String,
        sectionKey: String
    ): Result<List<ResultDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val results = firestoreService.queryDocumentsAs<ResultDoc>(
                Constants.Firestore.RESULTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("examId", examId)
                    .whereEqualTo("sectionKey", sectionKey)
                    .orderBy("percentage", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(results)
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

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
