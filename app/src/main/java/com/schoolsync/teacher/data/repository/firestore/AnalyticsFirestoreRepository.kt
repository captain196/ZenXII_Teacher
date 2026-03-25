package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.AuditLogDoc
import com.schoolsync.teacher.data.model.firestore.DashboardDoc
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
 * Repository for teacher-side analytics dashboards and audit logs.
 *
 * Collections used:
 * - dashboards: pre-computed dashboard summaries per user
 *   Doc ID pattern: {schoolId}_teacher_{teacherId}
 * - auditLogs: school-wide audit trail (admin-level teachers only)
 */
@Singleton
class AnalyticsFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the pre-computed dashboard document for the current teacher.
     * Document ID pattern: {schoolId}_teacher_{teacherId}
     */
    suspend fun getDashboard(): Result<DashboardDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        return try {
            val docId = "${schoolCode}_teacher_${teacherId}"
            val doc = firestoreService.getDocumentAs<DashboardDoc>(
                Constants.Firestore.DASHBOARDS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe the dashboard document in real time.
     * Reacts to school code and teacher ID changes via [flatMapLatest].
     * Emits null when identifiers are unavailable or the document does not exist.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDashboard(): Flow<DashboardDoc?> {
        return tokenManager.schoolCode
            .map { it?.takeIf { code -> code.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    tokenManager.userId.flatMapLatest { teacherId ->
                        if (teacherId.isNullOrBlank()) {
                            flowOf(null)
                        } else {
                            val docId = "${schoolCode}_teacher_${teacherId}"
                            firestoreService.observeDocumentAs<DashboardDoc>(
                                Constants.Firestore.DASHBOARDS,
                                docId
                            )
                        }
                    }
                }
            }
    }

    /**
     * Fetch recent audit log entries for the current school.
     * Only admin-level teachers should have access; access control is enforced
     * via Firestore security rules.
     *
     * @param limit maximum number of entries to fetch (default 100)
     */
    suspend fun getAuditLog(limit: Int = 100): Result<List<AuditLogDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val logs = firestoreService.queryDocumentsAs<AuditLogDoc>(
                Constants.Firestore.AUDIT_LOGS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
            }
            Result.success(logs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
