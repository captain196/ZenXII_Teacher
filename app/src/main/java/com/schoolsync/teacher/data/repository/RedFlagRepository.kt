package com.schoolsync.teacher.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.StudentFlag
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the Student Red Flag system.
 *
 * Firestore canonical (Phase B migration 2026-04-25):
 *   collection: studentFlags
 *   document ID: {schoolId}_{flagId}
 *
 * Replaces the RTDB path `StudentFlags/{schoolCode}/{studentId}/{flagId}`.
 * No RTDB writes; reads are Firestore-only via real-time snapshot listeners.
 */
@Singleton
class RedFlagRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val firebaseAuth: FirebaseAuth,
    private val tokenManager: TokenManager
) {

    private suspend fun resolveSchoolId(): String? =
        tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun resolveSchoolCode(): String? =
        tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun docIdFor(schoolId: String, flagId: String): String = "${schoolId}_${flagId}"

    /** Match admin's flagId format: RF_YYYYMMDD_HHMMSS_xxxxxx */
    private fun newFlagId(): String {
        val c = Calendar.getInstance()
        val ymd = "%04d%02d%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
        val hms = "%02d%02d%02d".format(
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        return "RF_${ymd}_${hms}_$suffix"
    }

    private fun snapshotToFlag(doc: DocumentSnapshot): StudentFlag {
        val d = doc.data ?: emptyMap<String, Any?>()
        return StudentFlag(
            flagId        = (d["flagId"] as? String) ?: doc.id,
            studentId     = (d["studentId"] as? String).orEmpty(),
            studentName   = (d["studentName"] as? String).orEmpty(),
            rollNo        = (d["rollNo"] as? String).orEmpty(),
            fatherName    = (d["fatherName"] as? String).orEmpty(),
            className     = (d["className"] as? String).orEmpty(),
            section       = (d["section"] as? String).orEmpty(),
            type          = (d["type"] as? String).orEmpty().lowercase(),
            message       = (d["message"] as? String).orEmpty(),
            subject       = (d["subject"] as? String).orEmpty(),
            teacherId     = (d["teacherId"] as? String).orEmpty(),
            teacherName   = (d["teacherName"] as? String).orEmpty(),
            severity      = (d["severity"] as? String).orEmpty().lowercase().ifEmpty { "low" },
            createdAtMs   = (d["createdAtMs"] as? Number)?.toLong() ?: 0L,
            createdByRole = (d["createdByRole"] as? String).orEmpty().lowercase().ifEmpty { "teacher" },
            status        = (d["status"] as? String).orEmpty().lowercase().ifEmpty { "active" },
            resolvedAtMs  = (d["resolvedAtMs"] as? Number)?.toLong(),
            resolvedBy    = d["resolvedBy"] as? String,
            deletedAtMs   = (d["deletedAtMs"] as? Number)?.toLong(),
            deletedBy     = d["deletedBy"] as? String,
            hwId          = d["hwId"] as? String
        )
    }

    /**
     * Create a new flag. The caller MUST populate denorm fields (studentId,
     * studentName, rollNo, fatherName, className, section) on the [flag] —
     * they're required for Admin RBAC and dashboard filtering. We validate
     * here as a hard guard so a future caller can't silently regress.
     *
     * Returns the generated flagId on success, or a Result.failure with a
     * descriptive message if validation fails.
     */
    suspend fun createFlag(flag: StudentFlag): Result<String> {
        // ── Validation ────────────────────────────────────────────────
        if (flag.studentId.isBlank()) {
            return Result.failure(IllegalArgumentException("studentId is required"))
        }
        if (flag.studentName.isBlank()) {
            return Result.failure(IllegalArgumentException("studentName is required"))
        }
        if (flag.className.isBlank() || flag.section.isBlank()) {
            return Result.failure(IllegalArgumentException("className and section are required"))
        }
        if (flag.message.isBlank()) {
            return Result.failure(IllegalArgumentException("message is required"))
        }
        val type = flag.type.lowercase().ifBlank { "behavior" }
        if (type !in ALLOWED_TYPES) {
            return Result.failure(IllegalArgumentException("Invalid type: $type"))
        }
        val severity = flag.severity.lowercase().ifBlank { "low" }
        if (severity !in ALLOWED_SEVERITIES) {
            return Result.failure(IllegalArgumentException("Invalid severity: $severity"))
        }

        return try {
            val schoolId   = resolveSchoolId()
                ?: return Result.failure(Exception("School ID not available"))
            val schoolCode = resolveSchoolCode() ?: schoolId
            val session    = tokenManager.session.firstOrNull().orEmpty()
            val teacherUid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(Exception("Not authenticated"))

            val flagId = newFlagId()
            val docId  = docIdFor(schoolId, flagId)
            val nowMs  = System.currentTimeMillis()

            val data: Map<String, Any?> = mapOf(
                "flagId"       to flagId,
                "schoolId"     to schoolId,
                "schoolCode"   to schoolCode,
                "session"      to session,
                "studentId"    to flag.studentId,
                "studentName"  to flag.studentName,
                "rollNo"       to flag.rollNo,
                "fatherName"   to flag.fatherName,
                "className"    to flag.className,
                "section"      to flag.section,
                "type"         to type,
                "severity"     to severity,
                "status"       to "active",
                "message"      to flag.message,
                "subject"      to flag.subject,
                "teacherId"    to teacherUid,
                "teacherName"  to flag.teacherName,
                "createdAtMs"   to nowMs,
                "createdAt"     to firestoreService.serverTimestamp(),
                "updatedAt"     to firestoreService.serverTimestamp(),
                "createdByRole" to "teacher",
                "resolvedAtMs"  to null,
                "resolvedBy"    to null,
                "deletedAtMs"   to null,
                "deletedBy"     to null,
                "hwId"          to flag.hwId
            )

            // Loud log so the parent-side mismatch (studentId not matching
            // parent's user.userId) can be diagnosed via:
            //   adb logcat -s RedFlagRepo:*
            Log.i(TAG, "createFlag write -> docId=$docId, studentId=${flag.studentId}, " +
                "schoolId=$schoolId, teacherUid=$teacherUid, className='${flag.className}', " +
                "section='${flag.section}'")

            firestoreService.setDocument(Constants.Firestore.STUDENT_FLAGS, docId, data)

            // CRITICAL: Firestore's set() success callback fires when the
            // write commits to the LOCAL cache, not when the server confirms.
            // A rules-rejected write (e.g. missing role/school_id auth claim)
            // still returns "success" but the doc never reaches the server.
            // Force a Source.SERVER read to confirm the write actually
            // persisted — if rules deny the read, we'll get a clear
            // PERMISSION_DENIED rather than a silent black hole.
            val verified = withTimeoutOrNull(8_000) {
                runCatching {
                    firestoreService.firestore
                        .collection(Constants.Firestore.STUDENT_FLAGS)
                        .document(docId)
                        .get(Source.SERVER)
                        .await()
                        .exists()
                }.getOrElse { e ->
                    Log.e(TAG, "Server-side verify failed for $flagId: ${e.message}", e)
                    return@withTimeoutOrNull false
                }
            } ?: false

            if (!verified) {
                // The write committed to the local Firestore cache before
                // hitting the server. Without cleanup, subsequent reads
                // would return this ghost doc and the teacher would see a
                // phantom flag that doesn't exist on the server. Queue a
                // local delete to evict it. (The server-side delete also
                // gets queued and fails silently — no doc to delete there.)
                runCatching {
                    firestoreService.firestore
                        .collection(Constants.Firestore.STUDENT_FLAGS)
                        .document(docId)
                        .delete()
                }.onFailure {
                    Log.w(TAG, "Cache cleanup of $docId failed: ${it.message}")
                }

                return Result.failure(
                    Exception(
                        "Write didn't reach Firestore. Likely a rules rejection " +
                        "(check that your auth token has role='Teacher' and school_id " +
                        "claims) or a network issue. Run: adb logcat | findstr Firestore"
                    )
                )
            }

            Result.success(flagId)
        } catch (e: Exception) {
            Log.e(TAG, "createFlag failed", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve an existing flag (status -> "resolved").
     *
     * Note: Firestore rules enforce that teachers can only resolve flags
     * they themselves created (request.auth.uid == resource.data.teacherId).
     * Admin roles can resolve any flag in their school.
     */
    suspend fun resolveFlag(flagId: String): Result<Unit> {
        return try {
            val schoolId   = resolveSchoolId()
                ?: return Result.failure(Exception("School ID not available"))
            val teacherUid = firebaseAuth.currentUser?.uid
                ?: tokenManager.userId.firstOrNull().orEmpty()
            val nowMs      = System.currentTimeMillis()

            val docId = docIdFor(schoolId, flagId)
            firestoreService.updateDocument(
                Constants.Firestore.STUDENT_FLAGS,
                docId,
                mapOf(
                    "status"       to "resolved",
                    "resolvedAtMs" to nowMs,
                    "resolvedBy"   to teacherUid,
                    "updatedAt"    to firestoreService.serverTimestamp()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Soft-delete a flag (status -> "deleted"). Hard delete is admin-only
     * via the web panel; mobile teachers use this to undo their own
     * mistakes while preserving the audit trail.
     *
     * Firestore rule will reject the update if:
     *   - the flag's createdByRole is 'admin'
     *   - or the caller's uid != the flag's teacherId
     */
    suspend fun softDeleteFlag(flagId: String): Result<Unit> {
        return try {
            val schoolId   = resolveSchoolId()
                ?: return Result.failure(Exception("School ID not available"))
            val teacherUid = firebaseAuth.currentUser?.uid
                ?: tokenManager.userId.firstOrNull().orEmpty()
            val nowMs      = System.currentTimeMillis()

            val docId = docIdFor(schoolId, flagId)
            firestoreService.updateDocument(
                Constants.Firestore.STUDENT_FLAGS,
                docId,
                mapOf(
                    "status"      to "deleted",
                    "deletedAtMs" to nowMs,
                    "deletedBy"   to teacherUid,
                    "updatedAt"   to firestoreService.serverTimestamp()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "softDeleteFlag failed for $flagId", e)
            Result.failure(e)
        }
    }

    /**
     * Get all flags for a specific student (one-shot).
     * Server-first read so we don't return ghost flags from rejected writes.
     */
    suspend fun getFlagsForStudent(studentId: String): Result<List<StudentFlag>> {
        return try {
            val schoolId = resolveSchoolId()
                ?: return Result.failure(Exception("School ID not available"))

            val ref = firestoreService.firestore
                .collection(Constants.Firestore.STUDENT_FLAGS)
            val query = ref.whereEqualTo("schoolId", schoolId)
                .whereEqualTo("studentId", studentId)
                .orderBy("createdAtMs", Query.Direction.DESCENDING)

            val snap = try {
                query.get(Source.SERVER).await()
            } catch (e: Exception) {
                Log.w(TAG, "Server fetch failed for student $studentId, falling back to cache: ${e.message}")
                query.get(Source.CACHE).await()
            }
            // Hide soft-deleted flags.
            val flags = snap.documents
                .filter { (it.getString("status") ?: "") != "deleted" }
                .map { snapshotToFlag(it) }
            Result.success(flags)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get flags for all students in a class. Returns map of studentId -> flags.
     * Single query per school instead of one per student.
     *
     * Uses Source.SERVER so we bypass Firestore's local cache. Cache
     * entries from previously rejected writes (rules denial) would
     * otherwise show up as ghost flags here. Falls back to cache only
     * when the server is unreachable, so offline users still see the
     * last known state.
     */
    suspend fun getFlagsForClass(
        students: List<StudentInfo>
    ): Result<Map<String, List<StudentFlag>>> {
        return try {
            val schoolId = resolveSchoolId()
                ?: return Result.failure(Exception("School ID not available"))
            val ids = students.map { it.studentId }.toHashSet()
            if (ids.isEmpty()) return Result.success(emptyMap())

            val ref = firestoreService.firestore
                .collection(Constants.Firestore.STUDENT_FLAGS)
            val query = ref.whereEqualTo("schoolId", schoolId)
                .orderBy("createdAtMs", Query.Direction.DESCENDING)

            val snap = try {
                query.get(Source.SERVER).await()
            } catch (e: Exception) {
                Log.w(TAG, "Server fetch failed, falling back to cache: ${e.message}")
                query.get(Source.CACHE).await()
            }

            val grouped = mutableMapOf<String, MutableList<StudentFlag>>()
            for (doc in snap.documents) {
                val sid = (doc.getString("studentId") ?: continue)
                if (sid !in ids) continue
                // Hide soft-deleted flags from the teacher list.
                if ((doc.getString("status") ?: "") == "deleted") continue
                grouped.getOrPut(sid) { mutableListOf() }.add(snapshotToFlag(doc))
            }
            Result.success(grouped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe flags for a student in real-time via Firestore snapshot listener.
     *
     * Filters out docs with pending local writes — the listener emits cached
     * pending writes alongside server-confirmed ones, which would surface
     * rules-rejected ghost flags that never make it past the cache.
     * Legitimate just-created flags appear after the server confirms (~ms).
     */
    fun observeFlagsForStudent(studentId: String): Flow<List<StudentFlag>> = flow {
        val schoolId = resolveSchoolId()
        if (schoolId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            firestoreService.observeQuery(Constants.Firestore.STUDENT_FLAGS) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("createdAtMs", Query.Direction.DESCENDING)
            }.map { snap ->
                snap.documents
                    .filterNot { it.metadata.hasPendingWrites() }
                    .filter { (it.getString("status") ?: "") != "deleted" }
                    .map { snapshotToFlag(it) }
            }
        )
    }

    /**
     * Count active flags for a given student.
     */
    suspend fun getActiveFlagCount(studentId: String): Int {
        return try {
            getFlagsForStudent(studentId).getOrNull()
                ?.count { it.status == "active" } ?: 0
        } catch (_: Exception) { 0 }
    }

    /**
     * Count total active flags across the given students. One query covers
     * the whole class, then we filter by the supplied student set in memory.
     *
     * Server-first read — the dashboard count badge would otherwise include
     * ghost flags from rejected writes still sitting in the local cache.
     */
    suspend fun getTotalActiveFlagCount(students: List<StudentInfo>): Int {
        return try {
            val schoolId = resolveSchoolId() ?: return 0
            val ids = students.map { it.studentId }.toHashSet()
            if (ids.isEmpty()) return 0

            val ref = firestoreService.firestore
                .collection(Constants.Firestore.STUDENT_FLAGS)
            val query = ref.whereEqualTo("schoolId", schoolId)
                .whereEqualTo("status", "active")

            val snap = try {
                query.get(Source.SERVER).await()
            } catch (e: Exception) {
                Log.w(TAG, "Server count failed, falling back to cache: ${e.message}")
                query.get(Source.CACHE).await()
            }
            snap.documents.count { (it.getString("studentId") ?: "") in ids }
        } catch (_: Exception) { 0 }
    }

    companion object {
        private const val TAG = "RedFlagRepo"
        private val ALLOWED_TYPES      = setOf("homework", "behavior", "performance")
        private val ALLOWED_SEVERITIES = setOf("low", "medium", "high")
    }
}
