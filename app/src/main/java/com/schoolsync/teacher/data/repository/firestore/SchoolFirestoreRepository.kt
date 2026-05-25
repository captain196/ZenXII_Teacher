package com.schoolsync.teacher.data.repository.firestore

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.SchoolDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading school data from Firestore.
 * Collection: schools/{schoolCode}
 */
@Singleton
class SchoolFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the current school document from Firestore.
     * Uses the school code stored in TokenManager to identify the document.
     */
    suspend fun getSchool(): Result<SchoolDoc> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val doc = firestoreService.getDocumentAs<SchoolDoc>(
                Constants.Firestore.SCHOOLS,
                schoolCode
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("School document not found for code: $schoolCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the school document as a raw map for flexible field access.
     * Useful when consuming fields not yet modelled in [SchoolDoc].
     */
    suspend fun getSchoolConfig(): Map<String, Any?>? {
        val schoolCode = getSchoolCode() ?: return null

        return try {
            firestoreService.getDocumentMap(Constants.Firestore.SCHOOLS, schoolCode)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Observe the school document for real-time updates.
     *
     * 2026-05-15 Phase 1 — rewritten. The previous implementation wrapped
     * the one-shot `getDocumentAs` inside a Flow.map, which gave callers
     * the *illusion* of a live observation but only re-fetched when the
     * schoolCode itself changed (i.e. effectively login-time only). As a
     * result, server-side updates to `currentSession`, `board_config`,
     * `classes`, etc. were never delivered to a running app instance
     * until full app restart.
     *
     * The new implementation uses [FirestoreService.observeDocumentAs]
     * which is backed by a Firestore snapshot listener (callbackFlow +
     * awaitClose for proper listener cleanup on consumer cancellation).
     * `flatMapLatest` ensures that when the schoolCode rotates (rare,
     * but possible on re-login), the previous snapshot listener is
     * cancelled before the new one is registered.
     *
     * Additionally: every emitted snapshot mirrors `currentSession` into
     * `TokenManager.saveSession()` so downstream session-scoped queries
     * (attendance, marks, timetable) automatically pick up admin-side
     * rollovers without forcing a re-login.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchool(): Flow<SchoolDoc?> {
        return tokenManager.schoolCode
            .map { code -> code?.takeIf { it.isNotBlank() } }
            .distinctUntilChanged()
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    firestoreService.observeDocumentAs<SchoolDoc>(
                        Constants.Firestore.SCHOOLS,
                        schoolCode
                    )
                }
            }
            .onEach { doc ->
                // Best-effort: propagate currentSession into TokenManager so
                // attendance / marks / timetable repositories that read
                // `tokenManager.session` automatically follow admin rollovers.
                // Wrapped in try/catch — a propagation failure must NEVER
                // block the snapshot from reaching the UI layer.
                val current = doc?.currentSession?.trim().orEmpty()
                if (current.isNotEmpty()) {
                    try {
                        val stored = tokenManager.session.firstOrNull().orEmpty()
                        if (stored != current) {
                            Log.i(TAG, "ACC_SESSION_PROPAGATE stored=$stored fromSchoolDoc=$current")
                            tokenManager.saveSession(current)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ACC_SESSION_PROPAGATE_FAILED err=${e.message}")
                    }
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "SchoolFsRepo"
    }
}
