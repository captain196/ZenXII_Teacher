package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.EventDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
                    .limit(300)
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Live variant of [getEvents]: emits the event list on every snapshot so
     * newly-published events appear without a manual refresh. Same schoolId
     * filter, orderBy and limit as the one-shot read. A terminal listener error
     * is surfaced as Result.failure (the collector should re-subscribe).
     */
    fun observeEvents(): Flow<Result<List<EventDoc>>> = flow {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
        if (schoolId == null) {
            emit(Result.failure(Exception("School id not available")))
            return@flow
        }
        emitAll(
            firestoreService.observeDocumentsAs<EventDoc>("events") { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .orderBy("startDate", Query.Direction.DESCENDING)
                    .limit(300)
            }.map { list -> Result.success(list) }
        )
    }.catch { e -> emit(Result.failure(e)) }
}
