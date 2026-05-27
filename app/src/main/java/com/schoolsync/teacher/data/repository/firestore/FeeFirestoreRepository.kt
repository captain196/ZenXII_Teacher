package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.FeeCarryForwardDoc
import com.schoolsync.teacher.data.model.firestore.FeeDemandDoc
import com.schoolsync.teacher.data.model.firestore.FeeDefaulterDoc
import com.schoolsync.teacher.data.model.firestore.FeeReceiptDoc
import com.schoolsync.teacher.data.model.firestore.FeeStructureDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading fee-related data from Firestore (teacher-side, read-only).
 *
 * Teachers can view fee structures, class defaulter lists, and individual
 * student fee statuses to support academic decisions (exam blocking, etc.).
 *
 * Collections used:
 * - feeStructures: class/section fee breakdown per session
 * - feeDemands: monthly fee demands per student
 * - feeDefaulters: defaulter flags per student
 */
@Singleton
class FeeFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the fee structure for a specific class and section.
     * Doc ID pattern: `{schoolId}_{session}_{className}_{section}`
     */
    suspend fun getFeeStructure(
        className: String,
        section: String
    ): Result<FeeStructureDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val docId = "${schoolCode}_${session}_${Constants.classKey(className)}_${Constants.sectionKey(section)}"

        return try {
            val doc = firestoreService.getDocumentAs<FeeStructureDoc>(
                Constants.Firestore.FEE_STRUCTURES,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all fee defaulters for a specific class and section in the current session.
     * Query: schoolId + session, then filter by className + section.
     */
    suspend fun getClassDefaulters(
        className: String,
        section: String
    ): Result<List<FeeDefaulterDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val defaulters = firestoreService.queryDocumentsAs<FeeDefaulterDoc>(
                Constants.Firestore.FEE_DEFAULTERS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", Constants.classKey(className))
                    .whereEqualTo("section", Constants.sectionKey(section))
            }
            Result.success(defaulters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all fee demands for a specific student in the current session.
     */
    suspend fun getStudentFeeStatus(studentId: String): Result<List<FeeDemandDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val demands = firestoreService.queryDocumentsAs<FeeDemandDoc>(
                Constants.Firestore.FEE_DEMANDS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("studentId", studentId)
                    // SW4-companion-C (2026-05-27): exclude archived demand
                    // docs from one-shot fee-status reads. Same rationale
                    // + composite-index requirement as the live observer
                    // observeStudentFeeDemands below.
                    .whereNotEqualTo("status", "archived")
                // No orderBy: Firestore drops docs missing the indexed field,
                // and alphabetical month order is meaningless anyway. The VM
                // re-sorts by academic order (April → March, Yearly last).
            }
            Result.success(demands)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the defaulter status for a specific student.
     * Doc ID pattern: `{schoolId}_{session}_{studentId}`
     */
    suspend fun getStudentDefaulterStatus(studentId: String): Result<FeeDefaulterDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val docId = "${schoolCode}_${session}_${studentId}"

        return try {
            val doc = firestoreService.getDocumentAs<FeeDefaulterDoc>(
                Constants.Firestore.FEE_DEFAULTERS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all payment receipts for a specific class and section in the
     * current session. Used by the teacher fee overview to compute
     * `totalCollected`.
     */
    suspend fun getClassReceipts(
        className: String,
        section: String
    ): Result<List<FeeReceiptDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val receipts = firestoreService.queryDocumentsAs<FeeReceiptDoc>(
                Constants.Firestore.FEE_RECEIPTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", Constants.classKey(className))
                    .whereEqualTo("section", Constants.sectionKey(section))
            }
            Result.success(receipts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch carry-forward dues for students in a class/section.
     */
    suspend fun getClassCarryForward(
        className: String,
        section: String
    ): Result<List<FeeCarryForwardDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        return try {
            val docs = firestoreService.queryDocumentsAs<FeeCarryForwardDoc>(
                Constants.Firestore.FEE_CARRY_FORWARD
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
            }
            Result.success(docs)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ─────────────────────────────────────────────────────────────────
    // Real-time listeners — push-based reads that auto-update the UI
    // when admin/parent writes change the underlying Firestore docs.
    // Used by StudentsViewModel and FeesTeacherViewModel to keep the
    // teacher app in sync with parent payments without manual refresh.
    // ─────────────────────────────────────────────────────────────────

    /**
     * Observe the defaulter status for a single student.
     * Doc ID: `{schoolId}_{session}_{studentId}` (canonical).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStudentDefaulterStatus(studentId: String): Flow<FeeDefaulterDoc?> {
        return tokenManagerPair()
            .flatMapLatest { pair ->
                if (pair == null) flowOf(null)
                else {
                    val (schoolCode, session) = pair
                    val docId = "${schoolCode}_${session}_${studentId}"
                    firestoreService.observeDocument(
                        Constants.Firestore.FEE_DEFAULTERS, docId
                    ).map { snap -> snap?.toObject(FeeDefaulterDoc::class.java) }
                }
            }
    }

    /**
     * Observe all fee demands for a student (drives the per-student
     * monthly paid/unpaid chips on StudentsScreen — without this the
     * `monthFee` map on the student doc stays stale until the next
     * roster reload).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStudentFeeDemands(studentId: String): Flow<List<FeeDemandDoc>> {
        return tokenManagerPair()
            .flatMapLatest { pair ->
                if (pair == null) flowOf(emptyList())
                else {
                    val (schoolCode, session) = pair
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_DEMANDS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("session", session)
                            .whereEqualTo("studentId", studentId)
                            // SW4-companion-C (2026-05-27): exclude archived
                            // demand docs from the per-student fee-chip
                            // derivation. Archived demands represent
                            // historical state (promotion/reverse-promotion
                            // cycles, consolidation) and must not falsify
                            // the `monthDemands.all { status == "paid" }`
                            // derivation used by StudentsViewModel. Mirrors
                            // admin SW4-companion-A (fetch_months) and
                            // SW4-companion-D (defaulter aggregation), plus
                            // parent SW4-companion-B (Parent FeeFirestore-
                            // Repository).
                            //
                            // Composite index required (auto-prompted by
                            // Firestore on first query if missing):
                            //   feeDemands(schoolId ASC, session ASC,
                            //   studentId ASC, status ASC).
                            .whereNotEqualTo("status", "archived")
                    }.map { snap ->
                        snap.documents.mapNotNull {
                            try { it.toObject(FeeDemandDoc::class.java) } catch (_: Exception) { null }
                        }
                    }
                }
            }
            .onStart { emit(emptyList()) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observeStudentFeeDemands failed — falling back to empty", e)
                emit(emptyList())
            }
    }

    /**
     * Observe all fee defaulters for a class/section. Drives the
     * "X of Y students paid" summary on FeesTeacherScreen so it
     * recomputes the moment any parent in the class pays.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeClassDefaulters(
        className: String,
        section: String
    ): Flow<List<FeeDefaulterDoc>> {
        return tokenManagerPair()
            .flatMapLatest { pair ->
                if (pair == null) flowOf(emptyList())
                else {
                    val (schoolCode, session) = pair
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_DEFAULTERS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("session", session)
                            .whereEqualTo("className", Constants.classKey(className))
                            .whereEqualTo("section", Constants.sectionKey(section))
                    }.map { snap ->
                        snap.documents.mapNotNull {
                            try { it.toObject(FeeDefaulterDoc::class.java) } catch (_: Exception) { null }
                        }
                    }
                }
            }
            .onStart { emit(emptyList()) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observeClassDefaulters failed — falling back to empty", e)
                emit(emptyList())
            }
    }

    /**
     * Observe all fee demands for a class/section. Drives the monthly
     * breakdown row on the class summary card (Apr 28/30 · May 25/30…).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeClassFeeDemands(
        className: String,
        section: String
    ): Flow<List<FeeDemandDoc>> {
        return tokenManagerPair()
            .flatMapLatest { pair ->
                if (pair == null) flowOf(emptyList())
                else {
                    val (schoolCode, session) = pair
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_DEMANDS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("session", session)
                            .whereEqualTo("className", Constants.classKey(className))
                            .whereEqualTo("section", Constants.sectionKey(section))
                            // SW4-companion-C (2026-05-27): exclude archived
                            // demand docs from the class monthly breakdown.
                            // Archived Class 9 orphans (333 across the
                            // cohort) would otherwise be summed into the
                            // "X paid of Y" per-month rollup that drives
                            // the class summary card. Mirrors the
                            // observeStudentFeeDemands filter above. Same
                            // composite-index requirement.
                            .whereNotEqualTo("status", "archived")
                    }.map { snap ->
                        snap.documents.mapNotNull {
                            try { it.toObject(FeeDemandDoc::class.java) } catch (_: Exception) { null }
                        }
                    }
                }
            }
            .onStart { emit(emptyList()) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observeClassFeeDemands failed — falling back to empty", e)
                emit(emptyList())
            }
    }

    /**
     * Observe the feeReminderLog collection for a given set of studentIds
     * (typically the teacher's class). Returns a map of studentId →
     * latest-reminder timestamp (ISO string), so the DefaulterCard can
     * show a "Reminded Xh ago" badge without a second round-trip.
     *
     * Returns all reminders for the school+session and filters by the
     * caller's student list client-side; Firestore's whereIn supports at
     * most 30 values, so for a large class we'd blow the limit. The full
     * read is modest (one write per reminder, usually <= few hundred per
     * session).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReminderLog(): Flow<Map<String, String>> {
        return tokenManagerPair()
            .flatMapLatest { pair ->
                if (pair == null) flowOf(emptyMap())
                else {
                    val (schoolCode, session) = pair
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_REMINDER_LOG
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("session", session)
                    }.map { snap ->
                        val latest = mutableMapOf<String, String>()
                        snap.documents.forEach { d ->
                            val sid  = d.getString("student_id") ?: return@forEach
                            val sent = d.getString("sent_date")
                                    ?: d.getString("deliveredAt")
                                    ?: return@forEach
                            val prev = latest[sid]
                            if (prev == null || sent > prev) latest[sid] = sent
                        }
                        latest
                    }
                }
            }
            .onStart { emit(emptyMap()) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observeReminderLog failed — falling back to empty", e)
                emit(emptyMap())
            }
    }

    private fun tokenManagerSchool(): Flow<String?> =
        tokenManager.schoolId.map { sid -> sid?.takeIf { it.isNotBlank() } }

    private fun tokenManagerPair(): Flow<Pair<String, String>?> =
        combine(tokenManager.schoolId, tokenManager.session) { sid, sess ->
            val s = sid?.takeIf { it.isNotBlank() }
            val y = sess?.takeIf { it.isNotBlank() }
            if (s != null && y != null) Pair(s, y) else null
        }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
