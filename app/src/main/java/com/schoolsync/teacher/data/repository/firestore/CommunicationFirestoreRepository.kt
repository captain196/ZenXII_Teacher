package com.schoolsync.teacher.data.repository.firestore

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.util.toEpochMillisOrNull
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.CircularDoc
import com.schoolsync.teacher.data.model.firestore.CircularReadDoc
import com.schoolsync.teacher.data.model.firestore.NotificationDoc
import com.schoolsync.teacher.data.model.firestore.PtmBookingDoc
import com.schoolsync.teacher.data.model.firestore.PtmConfigDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for communication features: circulars, notifications, and PTM scheduling.
 * Teacher-specific additions: create circulars and view read receipts.
 *
 * Collections used:
 * - circulars: school-wide announcements and circulars
 * - circularReads: per-user read receipts for circulars
 * - notifications: per-user notification documents
 * - ptmConfig: parent-teacher meeting configuration
 * - ptmBookings: individual PTM slot bookings
 */
@Singleton
class CommunicationFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    companion object {
        private const val TAG = "CommRepo"

        /**
         * Legacy `targetRoles`/`targetType` tokens (case-insensitive) that denote
         * a teacher/staff audience. Used only for legacy docs with no
         * `audienceKeys`.
         */
        private val TEACHER_ROLE_TOKENS = setOf(
            "teacher", "teachers", "staff", "teaching staff", "teaching",
            "non-teaching staff", "non-teaching", "all staff", "employee", "employees"
        )

        /**
         * Legacy `targetType` values that denote a broadcast reaching staff
         * (case-insensitive). Anything else that is class/parent/student-scoped
         * must NOT surface to a teacher.
         */
        private val BROADCAST_TARGET_TYPES = setOf(
            "", "all", "everyone", "all staff", "all students & staff",
            "all students and staff", "students & staff", "students and staff",
            "school", "school-wide", "schoolwide"
        )
    }

    // ── Circulars ──────────────────────────────────────────────────────────

    /**
     * Canonical audience keys the current teacher matches: school-wide,
     * staff/teacher role, and their OWN user key (for individually-targeted
     * staff notices).
     */
    private fun teacherAudienceKeys(userId: String?): Set<String> {
        val keys = mutableSetOf("all", "role:teacher", "role:staff")
        userId?.takeIf { it.isNotBlank() }?.let { keys.add("user:$it") }
        return keys
    }

    private suspend fun teacherAudienceKeys(): Set<String> = teacherAudienceKeys(getUserId())

    /**
     * Audience gate.
     *
     * Canonical path: when `audienceKeys` is present, a teacher sees the doc iff
     * their key set intersects it.
     *
     * Legacy path (no `audienceKeys`): consult the legacy targeting fields in a
     * strict order so a doc explicitly aimed at parents / other cohorts is NOT
     * leaked to teachers (confidentiality hardening — mirrors the Parent app fix):
     *   1. `targetRoles` non-empty  → visible iff a teacher/staff role is listed.
     *   2. `targetClasses` non-empty → class-scoped (students/parents of those
     *      classes). The teacher's class set is not available in this layer, so we
     *      stay conservative and do NOT expose it.
     *   3. otherwise → visible only if `targetType` is a broadcast (or explicitly
     *      a teacher/staff audience).
     */
    private fun matchesTeacherAudience(c: CircularDoc, myKeys: Set<String>): Boolean {
        if (c.audienceKeys.isNotEmpty()) return c.audienceKeys.any { it in myKeys }

        val roles = c.targetRoles.filter { it.isNotBlank() }
        if (roles.isNotEmpty()) {
            return roles.any { it.trim().lowercase() in TEACHER_ROLE_TOKENS }
        }
        val classes = c.targetClasses.filter { it.isNotBlank() }
        if (classes.isNotEmpty()) {
            // Class-scoped legacy doc → conservative: not visible to teachers.
            return false
        }
        val tt = c.targetType.trim().lowercase()
        return tt in BROADCAST_TARGET_TYPES || tt in TEACHER_ROLE_TOKENS
    }

    /** Hide circulars whose expiry has passed (expiresAt is optional). */
    private fun notExpired(c: CircularDoc): Boolean =
        c.expiresAt.toEpochMillisOrNull()?.let { it > System.currentTimeMillis() } ?: true

    /**
     * Fetch sent circulars AND notices for the current school, merged and
     * ordered by most recent first. Admin Notice Board posts to the `notices`
     * collection; HR auto-posts + Circulars module posts to `circulars`.
     * Both collections share the CircularDoc shape (admin dual-emits on write).
     */
    suspend fun getCirculars(limit: Int = 50): Result<List<CircularDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val circulars = firestoreService.queryDocumentsAs<CircularDoc>(
                Constants.Firestore.CIRCULARS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "sent")
                    .orderBy("sentAt", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
            }
            val notices = try {
                firestoreService.queryDocumentsAs<CircularDoc>(
                    Constants.Firestore.NOTICES_FS
                ) { ref ->
                    ref.whereEqualTo("schoolId", schoolCode)
                        .whereEqualTo("status", "sent")
                        .orderBy("sentAt", Query.Direction.DESCENDING)
                        .limit(limit.toLong())
                }
            } catch (e: Exception) {
                // Notices collection is optional — if the index isn't deployed yet
                // or the query fails, fall through with circulars only. Log it so a
                // missing composite index doesn't silently vanish Notice Board posts.
                Log.e(TAG, "notices query failed (check composite index schoolId+status+sentAt)", e)
                emptyList()
            }
            val myKeys = teacherAudienceKeys()
            val merged = (circulars + notices)
                .filter { matchesTeacherAudience(it, myKeys) && notExpired(it) }
                .sortedByDescending { it.sentAt.toEpochMillisOrNull() ?: 0L }
                .take(limit)
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The set of circular/notice IDs the current teacher has already opened
     * (from `circularReads`). Empty on any failure — read-state is a display
     * nicety, never a hard dependency.
     */
    suspend fun getReadCircularIds(): Set<String> {
        val userId = getUserId() ?: return emptySet()
        return try {
            firestoreService.queryDocumentsAs<CircularReadDoc>(
                Constants.Firestore.CIRCULAR_READS
            ) { ref -> ref.whereEqualTo("userId", userId) }
                .mapNotNull { it.circularId.takeIf { c -> c.isNotBlank() } }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Mark a circular as read by the current teacher.
     * Document ID is "{circularId}_{userId}" for idempotent writes.
     */
    suspend fun markCircularRead(circularId: String): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        val userName = getUserName()

        val docId = "${circularId}_${userId}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "circularId" to circularId,
            "userId" to userId,
            "userName" to userName,
            "role" to "teacher",
            "readAt" to FieldValue.serverTimestamp(),
            "acknowledged" to true
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.CIRCULAR_READS,
                docId,
                data
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe circulars AND notices in real time for the current school,
     * merged into a single stream ordered by most recent first. Reacts to
     * school code changes via [flatMapLatest]. If the notices composite
     * index isn't available yet, the notices stream emits empty and the
     * combined output degrades to circulars-only.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCirculars(): Flow<List<CircularDoc>> {
        // Key on schoolId (the value queries actually filter on — see getSchoolCode)
        // AND userId, so the stream re-subscribes on account switch and the audience
        // keys include the teacher's own "user:<id>" key for individually-targeted
        // staff notices.
        return combine(
            tokenManager.schoolId.map { it?.takeIf { code -> code.isNotBlank() } },
            tokenManager.userId
        ) { schoolCode, userId -> schoolCode to userId }
            .flatMapLatest { (schoolCode, userId) ->
                if (schoolCode == null) {
                    flowOf(emptyList())
                } else {
                    val myKeys = teacherAudienceKeys(userId)

                    val circulars = firestoreService.observeQuery(
                        Constants.Firestore.CIRCULARS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("status", "sent")
                            .orderBy("sentAt", Query.Direction.DESCENDING)
                            .limit(50)
                    }.map { it.toObjects(CircularDoc::class.java) }

                    val notices = firestoreService.observeQuery(
                        Constants.Firestore.NOTICES_FS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("status", "sent")
                            .orderBy("sentAt", Query.Direction.DESCENDING)
                            .limit(50)
                    }.map { it.toObjects(CircularDoc::class.java) }
                        .catch { e ->
                            // index missing → degrade to circulars-only, but LOG so the
                            // silent-vanish failure is observable.
                            Log.e(TAG, "notices listener failed (check composite index schoolId+status+sentAt)", e)
                            emit(emptyList())
                        }

                    combine(circulars, notices) { c, n ->
                        (c + n).filter { matchesTeacherAudience(it, myKeys) && notExpired(it) }
                            .sortedByDescending { it.sentAt.toEpochMillisOrNull() ?: 0L }.take(50)
                    }
                }
            }
    }

    // ── Notifications ──────────────────────────────────────────────────────

    /**
     * Fetch notifications for the current teacher, ordered by most recent first.
     */
    suspend fun getNotifications(limit: Int = 50): Result<List<NotificationDoc>> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val notifications = firestoreService.queryDocumentsAs<NotificationDoc>(
                Constants.Firestore.NOTIFICATIONS
            ) { ref ->
                ref.whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
            }
            Result.success(notifications)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a single notification as read.
     */
    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        return try {
            firestoreService.updateDocument(
                Constants.Firestore.NOTIFICATIONS,
                notifId,
                mapOf("read" to true)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the count of unread notifications for the current teacher.
     */
    suspend fun getUnreadNotificationCount(): Result<Int> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val snapshot = firestoreService.queryDocuments(
                Constants.Firestore.NOTIFICATIONS
            ) { ref ->
                ref.whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
            }
            Result.success(snapshot.size())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── PTM ────────────────────────────────────────────────────────────────

    /**
     * Fetch PTM events with booking_open status for the current school.
     */
    suspend fun getPtmEvents(): Result<List<PtmConfigDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val events = firestoreService.queryDocumentsAs<PtmConfigDoc>(
                Constants.Firestore.PTM_CONFIG
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "booking_open")
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all PTM bookings for the current teacher.
     */
    suspend fun getMyPtmBookings(): Result<List<PtmBookingDoc>> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val bookings = firestoreService.queryDocumentsAs<PtmBookingDoc>(
                Constants.Firestore.PTM_BOOKINGS
            ) { ref ->
                ref.whereEqualTo("teacherId", userId)
            }
            Result.success(bookings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }
}
