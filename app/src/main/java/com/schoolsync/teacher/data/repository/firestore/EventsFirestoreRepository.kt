package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.EventDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the school's events for the teacher app.
 *
 * Collection: `events`
 * Query shape mirrors the Parent app — `schoolId` filter, ordered by
 * `startDate` DESC. Writes live exclusively on the admin side.
 */
@Singleton
class EventsFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all events for the current school, newest first.
     *
     * `tokenManager.schoolId` holds the value written to the Firestore
     * `schoolId` field (same across admin/parent/teacher), so we pass it
     * through untouched.
     */
    suspend fun getEvents(): Result<List<EventDoc>> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))

        return try {
            val events = firestoreService.queryDocumentsAs<EventDoc>(
                "events"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .orderBy("startDate", Query.Direction.DESCENDING)
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
