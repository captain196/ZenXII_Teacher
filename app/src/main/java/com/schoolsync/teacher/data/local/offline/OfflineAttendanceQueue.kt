package com.schoolsync.teacher.data.local.offline

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C1 — the durable offline attendance queue façade.
 *
 * Owns enqueue (+ same-section supersession), crash recovery and flush
 * scheduling. Every entry point is a no-op while [OfflineFeatureFlag] is off, so
 * the feature ships dormant and is turned on in exactly one reviewed place.
 *
 * The queue NEVER writes attendance itself and never talks to Firestore — it
 * only persists intent and asks WorkManager to replay it through the normal
 * authenticated `attendance/save` endpoint. Server authority is untouched.
 */
@Singleton
class OfflineAttendanceQueue @Inject constructor(
    private val dao: PendingMarkDao,
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "OfflineAttQueue"
        const val UNIQUE_WORK = "attendance-flush"
        private const val BACKOFF_SECONDS = 30L
    }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    fun observeAll(): Flow<List<PendingMark>> = dao.observeAll()

    /**
     * Persist one section-wide attendance submission for later sync and kick the
     * flush worker. Returns the queued row id (UUID), or null when the feature is
     * off (caller should then treat the operation as not-queued).
     *
     * Supersession: an earlier still-pending row for the SAME
     * session+class+section+date is dropped so only the teacher's latest intent
     * for that section/day is submitted. Different students are not a concern —
     * this is a whole-section save — and different dates/sessions have different
     * collapse keys and are never touched.
     */
    suspend fun enqueueSectionSave(
        schoolId: String,
        session: String,
        className: String,
        section: String,
        dateIso: String,
        absentJson: String,
        leaveJson: String,
        lateJson: String,
        reason: String
    ): String? {
        if (!OfflineFeatureFlag.isEnabled()) return null

        val now = System.currentTimeMillis()
        val row = PendingMark(
            id = UUID.randomUUID().toString(),
            schoolId = schoolId,
            session = session,
            className = className,
            section = section,
            dateIso = dateIso,
            absentJson = absentJson,
            leaveJson = leaveJson,
            lateJson = lateJson,
            reason = reason,
            createdAt = now,
            attempts = 0,
            state = QueueState.PENDING,
            lastError = "",
            updatedAt = now
        )
        if (!row.isStructurallyValid()) {
            Log.w(TAG, "refusing to enqueue a structurally invalid mark")
            return null
        }
        dao.upsert(row)
        val dropped = dao.supersede(row.id, session, className, section, dateIso)
        if (dropped > 0) Log.d(TAG, "superseded $dropped older queued mark(s) for ${row.collapseKey()}")
        scheduleFlush()
        return row.id
    }

    /**
     * Call on app start. Resets any row stranded in SENDING by a killed process
     * back to PENDING (safe: the server write is merge-idempotent and the same
     * UUID is reused), then schedules a flush if anything is waiting.
     */
    suspend fun recoverOnStart() {
        if (!OfflineFeatureFlag.isEnabled()) return
        val recovered = dao.recoverStuckSending(System.currentTimeMillis())
        if (recovered > 0) Log.d(TAG, "recovered $recovered stuck SENDING row(s)")
        if (dao.sendable().isNotEmpty()) scheduleFlush()
    }

    /** Call when connectivity returns. */
    suspend fun onConnectivityRestored() {
        if (!OfflineFeatureFlag.isEnabled()) return
        if (dao.sendable().isNotEmpty()) scheduleFlush()
    }

    /** One unique, network-constrained, exponentially-backed-off flush worker. */
    fun scheduleFlush() {
        if (!OfflineFeatureFlag.isEnabled()) return
        val req = OneTimeWorkRequestBuilder<AttendanceFlushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(UNIQUE_WORK)
            .build()
        // KEEP → a flush already scheduled/running is not duplicated. There is
        // never more than one attendance flush worker.
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, req)
    }

    /** Teacher dismisses the terminal-failure rows after reading them. */
    suspend fun clearTerminalFailures(): Int {
        return dao.clearTerminal()
    }
}
