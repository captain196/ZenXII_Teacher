package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.Attachment
import com.schoolsync.teacher.data.model.firestore.HomeworkDoc
import com.schoolsync.teacher.data.model.firestore.SubmissionDoc
import com.schoolsync.teacher.data.model.firestore.toFirestoreMap
import com.schoolsync.teacher.util.Constants
import com.schoolsync.teacher.util.debugLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Result of [HomeworkFirestoreRepository.reviewOrMark]. */
enum class ReviewOrMarkOutcome { SubmissionReviewed, MarkRecorded }

/**
 * What a teacher has recorded for a non-submitter — score + remark + the
 * status the teacher selected ("complete" / "incomplete" / "reviewed" /
 * etc.). Status was added 2026-05-06; older docs returning empty default to
 * "reviewed" since that was the previous hardcoded behaviour.
 */
data class TeacherMarkEntry(
    val score: Int,
    val remark: String,
    val status: String
)

/**
 * Repository for full CRUD homework operations from the teacher side.
 *
 * Collections used:
 * - homework: class-level homework documents created by teachers
 * - submissions: per-student submission documents with review capability
 * - teacherMarks: evaluations for students who didn't submit (no fabricated submissions)
 */
@Singleton
class HomeworkFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    companion object {
        /**
         * Mint a fresh homework document ID.
         *
         * Format: `{schoolCode}_{epochMs}_{8 hex chars}` — exact parity with
         * the Admin PHP mint in Homework.php (Finding #8 hardening, 2026-05-15:
         * `"{$school}_" . round(microtime(true) * 1000) . '_' . bin2hex(random_bytes(4))`).
         *
         * The 32-bit random suffix from [java.security.SecureRandom] makes a
         * within-millisecond collision between two creates in the same
         * school statistically unreachable in practice (1 in 4.3 billion
         * even when the millisecond AND schoolCode happen to match). Prior
         * to this fix the ID was `{schoolCode}_{epochMs}` only — collision
         * would silently full-overwrite the earlier doc, and (post Step 4
         * attachments) the loser's attachments would orphan in Storage
         * while the winner's would mix with them under the same path scope.
         * See `teacher_hwid_entropy_backlog.md` in operator memory for the
         * full rationale.
         *
         * Treated as an opaque ID by all downstream consumers — no parser
         * relies on the internal `{school}_{ms}` shape, so adding the
         * `_{hex}` suffix is non-breaking.
         */
        fun mintHwId(schoolCode: String): String {
            val random = ByteArray(4)
            java.security.SecureRandom().nextBytes(random)
            val suffix = random.joinToString("") { "%02x".format(it) }
            return "${schoolCode}_${System.currentTimeMillis()}_$suffix"
        }
    }

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
        // Step 4 (2026-05-15) — rich attachment list. Caller (typically
        // HomeworkTeacherViewModel) uploads each file via
        // HomeworkAttachmentUploader.uploadSuspending() first and passes
        // the resulting Attachment objects here. We dual-emit them into
        // BOTH Firestore fields below so legacy readers (Parent UI today,
        // pre-Step-2 readers in general) see the download URLs in the
        // existing `attachments: List<String>` field while new readers
        // (Step 2 parser onward) get full metadata via `attachmentObjects`.
        attachments: List<Attachment> = emptyList(),
        // Step 4 (2026-05-15) — optional caller-supplied hwId. When the
        // caller pre-mints the hwId (so it can use the same value as the
        // {homeworkId} segment in attachment Storage paths uploaded
        // BEFORE this Firestore write), pass it here. Caller is then
        // responsible for using the SAME hwId as the storage path scope.
        // Defaults to the legacy "{schoolCode}_{ms}" mint when null —
        // preserving exact behaviour for any caller that doesn't upload
        // attachments first.
        existingHwId: String? = null
    ): Result<String> {
        // BUG-020 — input-boundary length validation (mirror of admin BUG-013).
        // Byte-count via toByteArray().size for accurate Firestore 1MB doc-cap
        // semantics; user-facing message says "characters" for clarity.
        if (title.toByteArray().size > 200) {
            return Result.failure(IllegalArgumentException("Title exceeds 200 characters."))
        }
        if (subject.toByteArray().size > 100) {
            return Result.failure(IllegalArgumentException("Subject exceeds 100 characters."))
        }
        if (description.toByteArray().size > 10000) {
            return Result.failure(IllegalArgumentException("Description exceeds 10000 characters."))
        }

        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val cls = Constants.classKey(className)
        val sec = Constants.sectionKey(section)
        val sectionKey = "${cls}/${sec}"
        val hwId = existingHwId ?: mintHwId(schoolCode)
        val normalizedDueDate = normalizeDueDate(dueDate)

        // Dual-emit: legacy `attachments` (URLs only) + rich
        // `attachmentObjects` (full metadata as Maps).
        val attachmentUrls: List<String> = attachments
            .map { it.downloadUrl }
            .filter { it.isNotBlank() }
        val attachmentMaps: List<Map<String, Any?>> = attachments
            .map { it.toFirestoreMap() }

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
            "dueDate" to normalizedDueDate,
            "createdAt" to firestoreService.serverTimestamp(),
            "status" to "active",
            "submissionCount" to 0,
            "totalStudents" to totalStudents,
            "attachments" to attachmentUrls,
            "attachmentObjects" to attachmentMaps
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
                // Pre-format the due date in IST so the Cloud Function (or
                // any future notification renderer) doesn't have to parse
                // ISO. End user sees "06 May 2026, 11:59 PM IST" instead
                // of "2026-05-06T23:59:59+05:30".
                val dueDateDisplay = try {
                    val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    val parser = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ssXXX",
                        java.util.Locale.US
                    )
                    val date = parser.parse(normalizedDueDate)
                    val out = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US)
                    out.timeZone = tz
                    if (date != null) "${out.format(date)} IST" else normalizedDueDate
                } catch (_: Exception) { normalizedDueDate }

                firestoreService.setDocument("pushRequests", reqId, mapOf(
                    "schoolId"        to schoolCode,
                    "studentId"       to "",
                    "mark"            to "HOMEWORK_CREATED",
                    "class"           to cls,
                    "section"         to sec,
                    "day"             to 0,
                    "month"           to "",
                    "date"            to "",
                    "source"          to "homework_created",
                    "markedBy"        to teacherName,
                    "status"          to "pending",
                    "homeworkId"      to hwId,
                    "title"           to title,
                    "subject"         to subject,
                    "dueDate"         to normalizedDueDate,   // raw ISO
                    "dueDateDisplay"  to dueDateDisplay,       // human-readable
                    "sectionKey"      to sectionKey,
                    "createdAt"       to com.google.firebase.Timestamp.now()
                ), merge = false)
            } catch (e: Exception) {
                // Best-effort: a notification failure shouldn't roll back the
                // homework create. Log so an operator triaging "parents
                // didn't get notified" can find the trail in logcat.
                android.util.Log.w(
                    "HomeworkRepo",
                    "createHomework pushRequests write failed (non-fatal): ${e.message}"
                )
            }

            Result.success(hwId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Edit an existing homework's text fields + dueDate + subject WITHOUT
     * touching submissions, teacherMarks, submissionCount, totalStudents or
     * attachments. Mirrors create's input-boundary length validation and
     * dueDate normalisation (end-of-day IST ISO) so edits stay consistent
     * with freshly-created docs. A retroactively-shortened dueDate is allowed
     * to land as-is — the Parent app's isOverdue() handles it the same way it
     * handles any past-due homework, so no special force flag is needed.
     */
    suspend fun updateHomework(
        homeworkId: String,
        title: String,
        description: String,
        subject: String,
        dueDate: String
    ): Result<Unit> {
        if (title.toByteArray().size > 200) {
            return Result.failure(IllegalArgumentException("Title exceeds 200 characters."))
        }
        if (subject.toByteArray().size > 100) {
            return Result.failure(IllegalArgumentException("Subject exceeds 100 characters."))
        }
        if (description.toByteArray().size > 10000) {
            return Result.failure(IllegalArgumentException("Description exceeds 10000 characters."))
        }

        val normalizedDueDate = normalizeDueDate(dueDate)
        return try {
            firestoreService.updateDocument(
                Constants.Firestore.HOMEWORK,
                homeworkId,
                mapOf(
                    "title" to title,
                    "description" to description,
                    "subject" to subject,
                    "dueDate" to normalizedDueDate
                )
            )
            debugLog("ACC_HW_UPDATE_OK hwId=$homeworkId")
            Result.success(Unit)
        } catch (e: Exception) {
            debugLog("ACC_HW_UPDATE_FAILED hwId=$homeworkId err=${e.javaClass.simpleName}:${e.message}")
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
        // S1 session scoping: when an active session is set, filter homework
        // to that session so docs from prior academic sessions don't leak in.
        // Blank/absent session ⇒ null ⇒ run the legacy unfiltered query (no
        // behavioural change for callers without a seeded session). The
        // composite index for the filtered path is
        // [schoolId, sectionKey, session, createdAt DESC] — see fallback below
        // for the not-yet-deployed window.
        val session = getSession()

        return try {
            val homework = firestoreService.queryDocumentsAs<HomeworkDoc>(
                Constants.Firestore.HOMEWORK
            ) { ref ->
                var q: Query = ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
                if (session != null) q = q.whereEqualTo("session", session)
                q.orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(homework)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                android.util.Log.w(
                    "HomeworkRepo",
                    "Composite index missing for homework(schoolId+sectionKey+session+createdAt) — falling back to client-side sort"
                )
                runCatching {
                    val rows = firestoreService.queryDocumentsAs<HomeworkDoc>(
                        Constants.Firestore.HOMEWORK
                    ) { ref ->
                        var q: Query = ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("sectionKey", sectionKey)
                        // Keep the same session scoping in the fallback so the
                        // result set is identical whether or not the composite
                        // index is deployed yet.
                        if (session != null) q = q.whereEqualTo("session", session)
                        q
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
     * Real-time stream of submissions for a homework. Used by the detail
     * sheet so the teacher sees new submissions arrive without manual
     * refresh. Resolves school code via the tokenManager.schoolId flow so
     * the listener re-attaches if the user signs in/out mid-session.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSubmissions(homeworkId: String): Flow<List<SubmissionDoc>> {
        return tokenManager.schoolId
            .flatMapLatest { schoolCode ->
                if (schoolCode.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    firestoreService.observeQuery(
                        Constants.Firestore.SUBMISSIONS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("homeworkId", homeworkId)
                    }.map { snap ->
                        snap.documents.mapNotNull { it.toObject(SubmissionDoc::class.java) }
                    }
                }
            }
    }

    /**
     * Atomic "review-or-mark" — replaces the read-then-branch pattern in the
     * ViewModel which was racy: between `getSubmissions()` finishing and the
     * teacherMarks write firing, a parent could submit fresh homework and
     * end up with both a submission doc AND a teacherMarks doc for the same
     * student, confusing the teacher's UI.
     *
     * Inside one Firestore transaction we:
     *   1. Read the specific submission doc by deterministic id
     *      `{homeworkId}_{studentId}`.
     *   2. If it exists → update review fields on the submission.
     *      If not       → create a teacherMarks doc instead (canonical
     *                     "evaluated without submission" record).
     *
     * Firestore retries the transaction automatically if the submission
     * doc's version changes mid-flight, so a parent submitting in the gap
     * is handled cleanly: the second attempt sees the submission and takes
     * the review branch.
     *
     * Push notifications fire outside the transaction (best-effort) so a
     * notification failure doesn't roll back the grade.
     */
    suspend fun reviewOrMark(
        homeworkId: String,
        studentId: String,
        studentName: String,
        score: Int,
        remark: String,
        reviewedBy: String,
        teacherId: String,
        // Status the teacher actually picked from the dropdown. Was previously
        // hardcoded to "reviewed" inside this method, which silently dropped
        // any "complete" / "incomplete" / "pending" pick — the row stayed on
        // "Pending" because the chosen status was never persisted, while the
        // chip counts treated the doc as "Done" (any teacherMark = done).
        // Whitelist defends against arbitrary client values.
        status: String = "reviewed"
    ): Result<ReviewOrMarkOutcome> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        // Whitelist — any other value falls back to "reviewed". Caller-supplied
        // status flows through to both the submission (existing students) and
        // the teacherMark (non-submitters) so the displayed pill always
        // matches what the teacher selected.
        val safeStatus = when (status.lowercase()) {
            "reviewed", "complete", "incomplete", "submitted", "pending" -> status.lowercase()
            else -> "reviewed"
        }

        val firestore = FirebaseFirestore.getInstance()
        val docId = "${homeworkId}_${studentId}"
        val homeworkRef = firestore.collection(Constants.Firestore.HOMEWORK).document(homeworkId)
        val submissionRef = firestore.collection(Constants.Firestore.SUBMISSIONS).document(docId)
        val markRef = firestore.collection("teacherMarks").document(docId)
        val trimmedRemark = remark.trim()

        return try {
            val outcome = firestore.runTransaction { txn ->
                // Inside the transaction so the closed-status check is
                // serialized with the read-modify-write below — if an admin
                // closes the homework while we're in flight, the transaction
                // retries and the second pass refuses the write.
                val hwSnap = txn.get(homeworkRef)
                val hwStatus = hwSnap.getString("status") ?: "active"
                if (hwStatus.equals("closed", ignoreCase = true)) {
                    throw IllegalStateException("Homework is closed — reviews are not accepted")
                }
                // Defensive 2026-05-14: isSameSchoolStrict() now denies reads
                // of non-existent /submissions docs (PERMISSION_DENIED) instead
                // of returning a snapshot with exists()==false. Catch the
                // denial here and treat as "submission does not exist" so the
                // teacherMark fall-through branch still fires for unsubmitted
                // students. Other exceptions still abort the transaction.
                val snap: com.google.firebase.firestore.DocumentSnapshot? = try {
                    txn.get(submissionRef)
                } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                    if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        null
                    } else {
                        throw e
                    }
                }
                if (snap != null && snap.exists()) {
                    txn.update(
                        submissionRef,
                        mapOf(
                            "remark"     to trimmedRemark,
                            "score"      to score,
                            "status"     to safeStatus,
                            "reviewedBy" to reviewedBy,
                            "reviewedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ReviewOrMarkOutcome.SubmissionReviewed
                } else {
                    // teacherMarks now carries `status` so the row pill +
                    // chip-count logic can read the teacher's actual pick.
                    // Without this field, every teacher mark used to render
                    // as the row's default "Pending" state.
                    txn.set(
                        markRef,
                        mapOf(
                            "schoolId"   to schoolCode,
                            "homeworkId" to homeworkId,
                            "studentId"  to studentId,
                            "teacherId"  to teacherId,
                            "score"      to score,
                            "remark"     to trimmedRemark,
                            "status"     to safeStatus,
                            "createdAt"  to FieldValue.serverTimestamp()
                        )
                    )
                    ReviewOrMarkOutcome.MarkRecorded
                }
            }.await()

            // Best-effort push so parents see the review. Only fires for the
            // submission-review branch — teacherMark-only events don't yet
            // have a notification template.
            if (outcome == ReviewOrMarkOutcome.SubmissionReviewed) {
                try {
                    val reqId = "${schoolCode}_hw_review_${docId}"
                    firestoreService.setDocument(
                        "pushRequests", reqId, mapOf(
                            "schoolId"    to schoolCode,
                            "studentId"   to studentId,
                            "mark"        to "HOMEWORK_REVIEWED",
                            "class"       to "",
                            "section"     to "",
                            "day"         to 0,
                            "month"       to "",
                            "date"        to "",
                            "source"      to "homework_reviewed",
                            "markedBy"    to reviewedBy,
                            "status"      to "pending",
                            "homeworkId"  to homeworkId,
                            "studentName" to studentName,
                            "score"       to score,
                            "remark"      to trimmedRemark,
                            "createdAt"   to com.google.firebase.Timestamp.now()
                        ), merge = false
                    )
                } catch (e: Exception) {
                    android.util.Log.w(
                        "HomeworkRepo",
                        "reviewOrMark pushRequests write failed (non-fatal): ${e.message}"
                    )
                }
            }

            Result.success(outcome)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Close a homework assignment (no further submissions accepted).
     */
    suspend fun closeHomework(homeworkId: String): Result<Unit> {
        return try {
            // BUG-018 — capture prevStatus + idempotent short-circuit + structured
            // before/after debugLog. Mobile analog of admin BUG-008 (closed-verified)
            // log_audit enrichment. Idempotent short-circuit on already-closed
            // skips the no-op write AND emits a distinct ACC_HW_CLOSE_NOOP signal so
            // operator can distinguish re-clicks from real closing events.
            // TOCTOU window between read and write remains (separate BUG-N candidate;
            // mobile equivalent of admin BUG-009 which is itself oos-blocked on
            // Firebase.php transaction primitives).
            val existingSnap = firestoreService.getDocument(
                Constants.Firestore.HOMEWORK,
                homeworkId
            )
            val prevStatus = existingSnap?.getString("status")?.takeIf { it.isNotBlank() } ?: "active"
            if (prevStatus.lowercase() == "closed") {
                debugLog("ACC_HW_CLOSE_NOOP hwId=$homeworkId reason=already_closed")
                return Result.success(Unit)
            }
            firestoreService.updateDocument(
                Constants.Firestore.HOMEWORK,
                homeworkId,
                mapOf("status" to "closed")
            )
            debugLog("ACC_HW_CLOSE_OK hwId=$homeworkId prevStatus=$prevStatus newStatus=closed")
            Result.success(Unit)
        } catch (e: Exception) {
            debugLog("ACC_HW_CLOSE_FAILED hwId=$homeworkId err=${e.javaClass.simpleName}:${e.message}")
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

    /**
     * Fetch all teacherMarks recorded for one homework, keyed by studentId.
     * Used to surface evaluations the teacher made for students who did NOT
     * submit (we don't forge fake submissions — see teacherMarks collection).
     */
    suspend fun getTeacherMarksForHomework(homeworkId: String): Result<Map<String, TeacherMarkEntry>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val rows = firestoreService.queryDocuments(Constants.Firestore.TEACHER_MARKS) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("homeworkId", homeworkId)
            }
            val out = mutableMapOf<String, TeacherMarkEntry>()
            for (d in rows.documents) {
                val sid = d.getString("studentId") ?: continue
                val sc  = (d.getLong("score") ?: -1L).toInt()
                val rk  = d.getString("remark") ?: ""
                // Default to "reviewed" for legacy docs that pre-date the
                // status field — keeps their pill rendering consistent
                // with the old "everything is a review" assumption.
                val st  = d.getString("status")?.takeIf { it.isNotBlank() } ?: "reviewed"
                out[sid] = TeacherMarkEntry(score = sc, remark = rk, status = st)
            }
            Result.success(out)
        } catch (e: Exception) {
            // BUG-019 — route through debugLog (OEM-strip-immune cache-file sink
            // per the F1 Phase-1 stabilization finding; OxygenOS/ColorOS/MIUI
            // strip third-party Log.w from logcat, leaving operator triage blind).
            // Result.success(emptyMap()) contract preserved so callers don't break.
            debugLog("ACC_HW_REPO_GET_TEACHER_MARKS_FAILED hwId=$homeworkId err=${e.javaClass.simpleName}:${e.message}")
            Result.success(emptyMap())  // best-effort — don't block submissions screen
        }
    }

    // NOTE (issue #9): tokenManager.schoolId and tokenManager.schoolCode hold
    // the SAME value in practice. AuthRepository sets LoginUser.schoolCode =
    // schoolId, then calls saveProfile (KEY_SCHOOL_ID = schoolId) AND
    // saveSchoolCode(schoolId). So homework queries (schoolId) and the roster
    // query / Storage path scope (schoolCode) resolve to the same SCH_XXXXXX
    // value — they do NOT diverge. The two TokenManager keys are kept distinct
    // only for historical reasons (schoolCode once held the numeric login code).
    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Normalise a dueDate to ISO 8601 with timezone offset (school's
     * local time, IST = +05:30). Inputs already in ISO-with-TZ form are
     * passed through unchanged; date-only "YYYY-MM-DD" is pinned to end
     * of day so date-picker UIs keep working. Reads on legacy docs are
     * unaffected — this is write-side only.
     */
    private fun normalizeDueDate(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return s
        val isoTz = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([+-]\\d{2}:?\\d{2}|Z)$")
        if (isoTz.matches(s)) return s
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return "${s}T23:59:59+05:30"
        return s
    }
}
