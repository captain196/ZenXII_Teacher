package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.CircularDoc
import com.schoolsync.teacher.data.model.firestore.CircularReadDoc
import com.schoolsync.teacher.data.model.firestore.NotificationDoc
import com.schoolsync.teacher.data.model.firestore.PtmBookingDoc
import com.schoolsync.teacher.data.model.firestore.PtmConfigDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

    // ── Circulars ──────────────────────────────────────────────────────────

    /**
     * Fetch sent circulars for the current school, ordered by most recent first.
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
            Result.success(circulars)
        } catch (e: Exception) {
            Result.failure(e)
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
     * Create a new circular. Returns the generated circular document ID.
     * Teacher-only operation.
     */
    suspend fun createCircular(
        title: String,
        body: String,
        category: String,
        priority: String,
        targetType: String,
        targetClasses: List<String>,
        targetRoles: List<String>,
        attachmentUrl: String,
        channels: List<String>
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        val userName = getUserName()

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "title" to title,
            "body" to body,
            "author" to userName,
            "authorId" to userId,
            "category" to category,
            "priority" to priority,
            "targetType" to targetType,
            "targetClasses" to targetClasses,
            "targetRoles" to targetRoles,
            "attachmentUrl" to attachmentUrl,
            "requireAcknowledgement" to false,
            "totalRecipients" to 0,
            "readCount" to 0,
            "channels" to channels,
            "status" to "sent",
            "sentAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.CIRCULARS,
                docId,
                data
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get read receipts for a specific circular.
     * Teacher-only operation for tracking who has read the circular.
     */
    suspend fun getCircularReadReceipts(circularId: String): Result<List<CircularReadDoc>> {
        return try {
            val receipts = firestoreService.queryDocumentsAs<CircularReadDoc>(
                Constants.Firestore.CIRCULAR_READS
            ) { ref ->
                ref.whereEqualTo("circularId", circularId)
            }
            Result.success(receipts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe circulars in real time for the current school.
     * Reacts to school code changes via [flatMapLatest].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCirculars(): Flow<List<CircularDoc>> {
        return tokenManager.schoolCode
            .map { it?.takeIf { code -> code.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(emptyList())
                } else {
                    firestoreService.observeQuery(
                        Constants.Firestore.CIRCULARS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("status", "sent")
                            .orderBy("sentAt", Query.Direction.DESCENDING)
                            .limit(50)
                    }.map { snapshot ->
                        snapshot.toObjects(CircularDoc::class.java)
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
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
