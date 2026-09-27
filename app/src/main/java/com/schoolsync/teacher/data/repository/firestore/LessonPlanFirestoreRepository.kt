package com.schoolsync.teacher.data.repository.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.DayTimetable
import com.schoolsync.teacher.data.model.TimetableEntry
import com.schoolsync.teacher.data.model.firestore.CurriculumDoc
import com.schoolsync.teacher.data.model.firestore.CurriculumTopicDoc
import com.schoolsync.teacher.data.model.firestore.LessonPlanDoc
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lesson plan reads + writes (Phase 7 — teacher app).
 *
 * Mirrors the doc shape and uniqueness invariant of admin-side
 * `Lesson_plan_service` so both writers produce drop-in compatible
 * documents:
 *   • collection      : `lessonPlans`
 *   • docId           : "{schoolId}_{session}_{teacherId}_{date}_P{periodIndex}"
 *   • version field   : optimistic concurrency (transactional check)
 *
 * Writes go through a Firestore transaction so a stale `expectedVersion`
 * surfaces as [LessonPlanConflictException] (treated as 409 by callers).
 *
 * Validations mirrored client-side (admin enforces same on save_lesson_plan):
 *   1. Period must exist in `timetables/{schoolId}_{session}_{sectionKey}_{day}`
 *      with matching subject AND teacherId.
 *   2. dayOfWeek is ALWAYS server-derived from `date`; never trusted from caller.
 *   3. status=`rescheduled` REQUIRES rescheduledTo in the form "YYYY-MM-DD_P{N}".
 *   4. notes ≤ 2000 chars.
 *
 * Two checks used to be left to somebody else, and neither of them was
 * being made:
 *   • Subject-assignment authorisation was described here as something
 *     Firestore rules "should" reject. The rules for lessonPlans allow any
 *     staff member of the school to write any plan document — there is no
 *     teacherId or assignment condition in them at all — so the sentence
 *     was an assumption, not a guarantee. It is checked here now, the same
 *     way Lesson_plan_service checks it.
 *   • Topic validity was described as something the admin UI enforces. The
 *     admin UI is not in the path of a write made from this app: topicId
 *     and topicTitle arrived from the caller and were stored unread, so a
 *     plan could name a topic from another class, or one deleted from the
 *     syllabus, under any title. The topic is now resolved against the
 *     curriculum and its title is taken from the topic itself.
 *
 * Neither check replaces a security rule. A modified client can still write
 * whatever the rules permit; these make an honest client behave like the
 * admin panel, and the rules gap is recorded separately.
 */

class LessonPlanConflictException(serverVersion: Long, clientVersion: Long) :
    Exception("Conflict: this plan was modified by another user (server v$serverVersion, you had v$clientVersion). Reload and retry.")

class LessonPlanValidationException(message: String) : Exception(message)

@Singleton
class LessonPlanFirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
    private val timetableRepo: TimetableFirestoreRepository,
    private val teacherRepository: TeacherRepository,
) {

    companion object {
        private const val TAG = "LessonPlanRepo"

        /**
         * Whether these assignments let this teacher plan this slot.
         *
         * Mirrors Lesson_plan_service::_canEdit: the subject may be named by
         * its name or its code, and class/section are compared in canonical
         * form so "8" and "Class 8th", "A" and "Section A" are the same
         * classroom. A class-wide assignment has already been expanded into
         * concrete sections by TeacherRepository, so nothing here needs to
         * treat a blank section as a wildcard.
         */
        fun isAssignedTo(
            assignments: List<ClassAssignment>,
            className: String,
            section: String,
            subject: String,
        ): Boolean {
            if (className.isBlank() || section.isBlank() || subject.isBlank()) return false
            val wantClass   = Constants.classKey(className)
            val wantSection = Constants.sectionKey(section)
            val wantSubject = subject.trim()
            return assignments.any { a ->
                Constants.classKey(a.className).equals(wantClass, ignoreCase = true) &&
                Constants.sectionKey(a.section).equals(wantSection, ignoreCase = true) &&
                (a.subject.trim().equals(wantSubject, ignoreCase = true) ||
                 (a.subjectCode.isNotBlank() &&
                  a.subjectCode.trim().equals(wantSubject, ignoreCase = true)))
            }
        }

        /**
         * The title to store for [topicId], or null when this curriculum has
         * no such topic. A title is never taken from the caller: it is what
         * list views, exports and the parent app show long after the topic
         * itself may have changed, so it has to come from the topic.
         */
        fun topicTitleOrNull(topics: List<CurriculumTopicDoc>, topicId: String): String? {
            if (topicId.isBlank()) return null
            val t = topics.firstOrNull { it.topicId == topicId || it.id == topicId }
                ?: return null
            return t.title
        }
        private val VALID_STATUSES = listOf("planned", "completed", "skipped", "rescheduled")
        private val RESCHEDULE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}_P\\d+$")
        private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }

    // ── Read API ────────────────────────────────────────────────────────

    /** A teacher slot for a given day, optionally paired with an existing plan. */
    data class SlotWithPlan(
        val entry: TimetableEntry,
        val plan: LessonPlanDoc?
    )

    /**
     * Build the teacher's "today" view: ordered list of slots they teach today
     * (from the timetable) merged with any existing lesson plans (from the
     * `lessonPlans` collection). Never throws — returns empty list on failure.
     */
    suspend fun getMyDailyPlan(date: String): Result<List<SlotWithPlan>> {
        if (!ISO_DATE_REGEX.matches(date)) {
            return Result.failure(LessonPlanValidationException("date must be YYYY-MM-DD"))
        }
        val teacherId = tokenManager.userId.firstOrNull().orEmpty()
        if (teacherId.isBlank()) return Result.failure(Exception("Not signed in"))

        return try {
            val dayName = dayOfWeekFromIso(date)

            // 1. Slots this teacher teaches today (filter from full-week query)
            // A teacher with no assignments genuinely has no periods. A read
            // that FAILED is a different thing, and it used to look identical:
            // an empty day, with the plans they had already written nowhere in
            // sight. Only the first of those is an empty list.
            val assignedClasses = teacherRepository.getAssignedClasses().getOrElse { e ->
                throw Exception(
                    "Your classes could not be loaded, so today's periods are not " +
                    "being shown. Try again in a moment.", e
                )
            }.map { it.className to it.section }.distinct()
            val daySlots: List<TimetableEntry> = if (assignedClasses.isEmpty()) {
                emptyList()
            } else {
                timetableRepo.getMyTimetable(assignedClasses).getOrElse { e ->
                    throw Exception(
                        "Your timetable could not be loaded, so today's periods are " +
                        "not being shown. Try again in a moment.", e
                    )
                }
                    .firstOrNull { it.day.equals(dayName, ignoreCase = true) }
                    ?.periods
                    ?.filter { it.teacherId == teacherId && !it.isBreak && it.subject.isNotBlank() }
                    ?.sortedBy { it.periodNumber }
                    ?: emptyList()
            }

            // 2. Existing plans (single-equality query, filter session+date client-side)
            val plans = fetchPlansForTeacherDate(teacherId, date)
            val plansByPeriod = plans.associateBy { it.periodIndex }

            val rows = daySlots.map { e ->
                SlotWithPlan(entry = e, plan = plansByPeriod[e.periodNumber - 1])
            }
            Result.success(rows)
        } catch (e: Exception) {
            Log.e(TAG, "getMyDailyPlan failed", e)
            Result.failure(e)
        }
    }

    /** Topics for the curriculum doc that backs (className, section, subject). */
    suspend fun getTopicsForSubject(
        className: String,
        section: String,
        subject: String,
    ): Result<List<CurriculumTopicDoc>> {
        if (className.isBlank() || section.isBlank() || subject.isBlank()) return Result.success(emptyList())
        val schoolId = tokenManager.schoolId.firstOrNull().orEmpty()
        val session  = tokenManager.session.firstOrNull().orEmpty()
        if (schoolId.isBlank() || session.isBlank()) return Result.failure(Exception("Missing school/session"))

        val classSection = "$className/$section"
        val parentId = curriculumDocId(schoolId, session, classSection, subject)

        return try {
            val parent = firestoreService.getDocumentAs<CurriculumDoc>(Constants.Firestore.CURRICULUM, parentId)
                ?: return Result.success(emptyList())  // no curriculum yet for this combo

            val ids = parent.topicIds.filter { it.isNotBlank() }
            if (ids.isEmpty()) return Result.success(emptyList())

            // Subcollection path: curriculum/{parentId}/topics/{topicId}
            // FirestoreService.getDocument doesn't take subcollection paths, so
            // hit the SDK directly for this read.
            val topics = mutableListOf<CurriculumTopicDoc>()
            for (tid in ids) {
                val snap = firestore.collection(Constants.Firestore.CURRICULUM)
                    .document(parentId)
                    .collection("topics")
                    .document(tid)
                    .get()
                    .await()
                snap.toObject(CurriculumTopicDoc::class.java)?.let { topics += it }
            }
            topics.sortBy { it.sortOrder }
            Result.success(topics)
        } catch (e: Exception) {
            // NOT Result.success(emptyList()). A failed read used to reach the
            // edit sheet as "No topics found in curriculum", which is a
            // statement about the school's syllabus and is untrue — and it
            // arrives at the moment a teacher is deciding what to record
            // against a lesson. Planning is still possible without a topic;
            // being told the syllabus is empty is what must not happen.
            Log.w(TAG, "getTopicsForSubject failed for $parentId: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Write API ───────────────────────────────────────────────────────

    /**
     * Inputs for [saveLessonPlan]. Mirrors the admin save_lesson_plan POST body.
     * Use [SaveLessonPlanInput.completing], [.skipping], etc. for quick actions.
     */
    data class SaveLessonPlanInput(
        val className: String,
        val section: String,
        val subject: String,
        val date: String,
        val periodIndex: Int,
        val status: String = "planned",
        val topicId: String = "",
        val topicTitle: String = "",
        val notes: String = "",
        val rescheduledTo: String = "",
        val expectedVersion: Long? = null,
    )

    /**
     * Upsert a lesson plan with optimistic concurrency. Throws
     * [LessonPlanConflictException] when expected_version mismatches the
     * server, [LessonPlanValidationException] for input violations.
     */
    suspend fun saveLessonPlan(input: SaveLessonPlanInput): Result<LessonPlanDoc> {
        return try {
            val teacherId   = tokenManager.userId.firstOrNull().orEmpty()
            val teacherName = tokenManager.userName.firstOrNull().orEmpty()
            val schoolId    = tokenManager.schoolId.firstOrNull().orEmpty()
            val session     = tokenManager.session.firstOrNull().orEmpty()
            if (teacherId.isBlank() || schoolId.isBlank() || session.isBlank()) {
                throw LessonPlanValidationException("Not signed in or missing school/session")
            }

            // ── basic validation ──
            if (input.className.isBlank() || input.section.isBlank() ||
                input.subject.isBlank() || input.date.isBlank() || input.periodIndex < 0) {
                throw LessonPlanValidationException("class, section, subject, date, period_index required")
            }
            if (!ISO_DATE_REGEX.matches(input.date)) {
                throw LessonPlanValidationException("date must be YYYY-MM-DD")
            }
            if (input.status !in VALID_STATUSES) {
                throw LessonPlanValidationException("status must be one of $VALID_STATUSES")
            }
            if (input.notes.length > 2000) {
                throw LessonPlanValidationException("notes too long (max 2000 chars)")
            }
            if (input.rescheduledTo.isNotBlank() && !RESCHEDULE_REGEX.matches(input.rescheduledTo)) {
                throw LessonPlanValidationException("rescheduled_to must look like YYYY-MM-DD_P{N}")
            }
            if (input.status == "rescheduled" && input.rescheduledTo.isBlank()) {
                throw LessonPlanValidationException("rescheduled_to is required when status=rescheduled")
            }

            // Authorisation: this teacher must actually teach this subject to
            // this class. A failed lookup is NOT permission — an outage must
            // not become an open door, and it must not become a false
            // accusation either, so the two are reported differently.
            val assignments = teacherRepository.getAssignedClasses().getOrElse { e ->
                throw LessonPlanValidationException(
                    "Your class assignments could not be read, so this plan was not " +
                    "saved. Try again in a moment."
                )
            }
            if (!isAssignedTo(assignments, input.className, input.section, input.subject)) {
                throw LessonPlanValidationException(
                    "You are not assigned to teach ${input.subject} to " +
                    "${input.className} ${input.section}, so this plan cannot be saved."
                )
            }

            val dayOfWeek    = dayOfWeekFromIso(input.date)              // SERVER-DERIVED, ignores client
            val classSection = "${input.className}/${input.section}"
            val docId        = planDocId(schoolId, session, teacherId, input.date, input.periodIndex)
            val curId        = curriculumDocId(schoolId, session, classSection, input.subject)

            // ── period-match validation ──
            val cell = resolveTimetableCell(
                schoolId = schoolId, session = session,
                className = input.className, section = input.section,
                day = dayOfWeek, periodIndex = input.periodIndex
            ) ?: throw LessonPlanValidationException(
                "No timetable period found for $classSection $dayOfWeek P${input.periodIndex + 1}"
            )
            if (cell.teacherId.isNotBlank() && cell.teacherId != teacherId) {
                throw LessonPlanValidationException(
                    "Period mismatch: timetable shows ${cell.teacher.ifBlank { "another teacher" }} in this slot."
                )
            }
            if (cell.subject.isNotBlank() && !cell.subject.equals(input.subject, ignoreCase = true)) {
                throw LessonPlanValidationException(
                    "Period mismatch: timetable shows '${cell.subject}' in this slot, not '${input.subject}'."
                )
            }

            // The topic, if one was chosen, must belong to THIS class and
            // subject's curriculum, and its title comes from the topic rather
            // than from the caller.
            var topicTitle = ""
            if (input.topicId.isNotBlank()) {
                val topics = getTopicsForSubject(
                    className = input.className, section = input.section,
                    subject = input.subject
                ).getOrElse {
                    throw LessonPlanValidationException(
                        "The curriculum could not be read, so this plan was not checked " +
                        "against it or saved. Try again in a moment."
                    )
                }
                topicTitle = topicTitleOrNull(topics, input.topicId)
                    ?: throw LessonPlanValidationException(
                        "The topic this plan was written for is no longer in the " +
                        "curriculum for ${input.className} ${input.section} / " +
                        "${input.subject}. Choose another topic, or clear it, and save again."
                    )
            }

            // ── transactional upsert with version check ──
            val nowIso = isoNow()
            val docRef = firestore.collection(Constants.Firestore.LESSON_PLANS).document(docId)

            val saved: LessonPlanDoc = firestore.runTransaction { txn ->
                val existing = txn.get(docRef)
                val curVersion = if (existing.exists()) (existing.getLong("version") ?: 0L) else 0L
                if (input.expectedVersion != null && input.expectedVersion != curVersion) {
                    throw LessonPlanConflictException(curVersion, input.expectedVersion)
                }
                // No expected version means the caller believed this period was
                // empty. If it is not, the caller was working from something
                // stale — a day that failed to load, or another device that got
                // there first — and writing would replace a plan nobody saw.
                if (input.expectedVersion == null && existing.exists()) {
                    throw LessonPlanConflictException(curVersion, 0L)
                }

                val isNew = !existing.exists()
                val createdAt    = if (isNew) nowIso else existing.getString("createdAt") ?: nowIso
                val createdByUid = if (isNew) teacherId else existing.getString("createdByUid") ?: teacherId
                val createdByNm  = if (isNew) teacherName else existing.getString("createdByName") ?: teacherName
                val priorCompletedAt = existing.getString("completedAt") ?: ""
                val completedAt = if (input.status == "completed") {
                    if (priorCompletedAt.isNotBlank()) priorCompletedAt else nowIso
                } else ""

                val data = mapOf(
                    "schoolId"        to schoolId,
                    "session"         to session,
                    "planId"          to docId,
                    "version"         to curVersion + 1,

                    "className"       to input.className,
                    "section"         to input.section,
                    "classSection"    to classSection,
                    "subject"         to input.subject,
                    "subjectCode"     to "",                // populated from subjectAssignment elsewhere
                    "teacherId"       to teacherId,
                    "teacherName"     to teacherName,

                    "date"            to input.date,
                    "dayOfWeek"       to dayOfWeek,
                    "periodIndex"     to input.periodIndex,
                    "periodNumber"    to (input.periodIndex + 1),

                    "topicId"         to input.topicId,
                    "topicTitle"      to topicTitle,
                    "curriculumDocId" to curId,

                    "notes"           to input.notes,
                    "status"          to input.status,
                    "rescheduledTo"   to input.rescheduledTo,
                    "completedAt"     to completedAt,

                    "createdAt"       to createdAt,
                    "createdByUid"    to createdByUid,
                    "createdByName"   to createdByNm,
                    "updatedAt"       to nowIso,
                    "updatedByUid"    to teacherId,
                    "updatedByName"   to teacherName
                )
                txn.set(docRef, data)

                LessonPlanDoc(
                    id = docId,
                    schoolId = schoolId, session = session, planId = docId,
                    version = curVersion + 1,
                    className = input.className, section = input.section,
                    classSection = classSection, subject = input.subject,
                    teacherId = teacherId, teacherName = teacherName,
                    date = input.date, dayOfWeek = dayOfWeek,
                    periodIndex = input.periodIndex, periodNumber = input.periodIndex + 1,
                    topicId = input.topicId, topicTitle = topicTitle,
                    curriculumDocId = curId,
                    notes = input.notes, status = input.status,
                    rescheduledTo = input.rescheduledTo, completedAt = completedAt,
                    createdAt = createdAt, createdByUid = createdByUid, createdByName = createdByNm,
                    updatedAt = nowIso, updatedByUid = teacherId, updatedByName = teacherName
                )
            }.await()

            // best-effort audit (do not fail the save)
            try {
                writeAuditLog(
                    schoolId = schoolId, session = session,
                    action = if (saved.version == 1L) "create" else "update",
                    entityId = docId, status = input.status, topicId = input.topicId,
                    actorUid = teacherId, actorName = teacherName
                )
            } catch (e: Exception) {
                Log.w(TAG, "audit write failed (non-fatal): ${e.message}")
            }

            Result.success(saved)
        } catch (e: LessonPlanConflictException) {
            Result.failure(e)
        } catch (e: LessonPlanValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "saveLessonPlan failed", e)
            Result.failure(e)
        }
    }

    // ── Internals ───────────────────────────────────────────────────────

    /** Read a single timetable cell — needed for the period-match guard. */
    private suspend fun resolveTimetableCell(
        schoolId: String, session: String,
        className: String, section: String, day: String, periodIndex: Int,
    ): TimetableEntry? {
        if (day.isBlank()) return null
        // TT-F1. This used to build the id as
        //     "${schoolId}_${session}_${className}/${section}_$day"
        // — with a SLASH, and with the raw class/section the caller supplied.
        // The slash makes it an invalid document reference rather than a
        // missing one: Firestore reads it as a path, document() throws, and the
        // catch below (correctly) reports a failed read. So the period-match
        // guard could not succeed for ANY teacher, and every lesson-plan save
        // ended in "The timetable for this day could not be read". The id is
        // built by the shared helper now, which also normalises "8"/"A" into
        // the canonical form the projection writes.
        val docId = Constants.timetableDocId(schoolId, session, className, section, day)
        return try {
            val snap = firestore.collection(Constants.Firestore.TIMETABLES).document(docId).get().await()
            if (!snap.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val periods = snap.get("periods") as? List<Map<String, Any?>> ?: return null
            val match = periods.firstOrNull { ((it["periodNumber"] as? Number)?.toInt() ?: 0) - 1 == periodIndex }
                ?: return null
            TimetableEntry(
                day = day,
                periodNumber = (match["periodNumber"] as? Number)?.toInt() ?: 0,
                subject = match["subject"] as? String ?: "",
                teacher = match["teacher"] as? String ?: "",
                teacherId = match["teacherId"] as? String ?: "",
                startTime = match["startTime"] as? String ?: "",
                endTime = match["endTime"] as? String ?: "",
                room = match["room"] as? String ?: "",
                className = snap.getString("className") ?: className,
                section = snap.getString("section") ?: section,
                type = match["type"] as? String ?: "class",
                isBreak = (match["type"] as? String)?.lowercase() in listOf("break", "lunch")
            )
        } catch (e: Exception) {
            // null here means the day has no such period, and the caller says
            // so to the teacher: "No timetable period found". A failed read is
            // not that, and telling a teacher their timetable is unset while it
            // sits there intact sends them to fix nothing.
            Log.w(TAG, "resolveTimetableCell failed for $docId: ${e.message}")
            throw LessonPlanValidationException(
                "The timetable for this day could not be read, so the period could " +
                "not be checked. Try again in a moment."
            )
        }
    }

    /** All of one teacher's plans for one date (single-equality query). */
    private suspend fun fetchPlansForTeacherDate(teacherId: String, date: String): List<LessonPlanDoc> {
        val schoolId = tokenManager.schoolId.firstOrNull().orEmpty()
        val session  = tokenManager.session.firstOrNull().orEmpty()
        return try {
            val docs = firestoreService.queryDocumentsAs<LessonPlanDoc>(Constants.Firestore.LESSON_PLANS) { ref ->
                ref.whereEqualTo("teacherId", teacherId)
            }
            docs.filter { it.schoolId == schoolId && it.session == session && it.date == date }
        } catch (e: Exception) {
            // NOT emptyList(). The day view draws an empty list as periods
            // nobody has planned, and the quick actions on those rows send no
            // expected version — so a swallowed read here ends with a teacher
            // tapping 'completed' on a plan they cannot see and overwriting
            // their own notes with nothing.
            Log.w(TAG, "fetchPlansForTeacherDate failed: ${e.message}")
            throw Exception(
                "Today's plans could not be loaded, so the day is not being shown " +
                "as empty. Try again in a moment.", e
            )
        }
    }

    /** Best-effort audit log write — same shape as `Audit_log_service`. */
    private suspend fun writeAuditLog(
        schoolId: String, session: String,
        action: String, entityId: String, status: String, topicId: String,
        actorUid: String, actorName: String,
    ) {
        val ts = isoNow()
        val compact = ts.replace(Regex("[^0-9T]"), "").substring(0, 15)
        val rand = SecureRandom().let { sr ->
            val bytes = ByteArray(4); sr.nextBytes(bytes); bytes.joinToString("") { "%02x".format(it) }
        }
        val logId = "${schoolId}_${compact}_$rand"
        val doc = mapOf(
            "logId" to logId, "schoolId" to schoolId, "session" to session,
            "ts" to ts, "createdAt" to ts,
            "action" to action, "entityType" to "lessonPlan", "entityId" to entityId,
            "actor" to mapOf("uid" to actorUid, "name" to actorName, "role" to "Teacher"),
            "before" to null,
            "after" to mapOf("status" to status, "topicId" to topicId),
            "metadata" to mapOf("source" to "teacher_app")
        )
        firestoreService.setDocument(Constants.Firestore.ACADEMIC_AUDIT_LOG, logId, doc)
    }

    private fun planDocId(schoolId: String, session: String, teacherId: String, date: String, periodIndex: Int): String {
        val tid = teacherId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return "${schoolId}_${session}_${tid}_${date}_P$periodIndex"
    }

    private fun curriculumDocId(schoolId: String, session: String, classSection: String, subject: String): String {
        val cs = classSection.replace(' ', '_').replace('/', '_')
        val sub = subject.replace(' ', '_').replace('/', '_')
        return "${schoolId}_${session}_${cs}_$sub"
    }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private fun dayOfWeekFromIso(iso: String): String {
        return try {
            val parts = iso.split("-")
            val cal = Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                Calendar.SUNDAY -> "Sunday"
                else -> ""
            }
        } catch (_: Exception) { "" }
    }
}
