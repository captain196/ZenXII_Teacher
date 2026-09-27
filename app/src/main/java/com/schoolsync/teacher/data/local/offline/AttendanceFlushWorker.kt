package com.schoolsync.teacher.data.local.offline

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.remote.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * C1 — the single background worker that drains the offline attendance queue.
 *
 * It is a thin IO shell: all judgement lives in [FlushDecision] (pure, JVM
 * tested). It replays each queued submission through the SAME authenticated
 * `attendance/save` endpoint, so tenant, role, class ownership, session,
 * admission, working-day, lock and date rules are ALL re-enforced by the server
 * on replay. The worker can never grant authorization the server would refuse.
 */
@HiltWorker
class AttendanceFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: PendingMarkDao,
    private val api: ApiService,
    private val tokenManager: TokenManager,
    private val firebaseAuth: FirebaseAuth
) : CoroutineWorker(appContext, params) {

    companion object { private const val TAG = "AttFlushWorker" }

    override suspend fun doWork(): Result {
        if (!OfflineFeatureFlag.isEnabled()) return Result.success()

        // Recover any row stranded SENDING by a previous killed process.
        dao.recoverStuckSending(System.currentTimeMillis())

        val activeSession = runCatching { tokenManager.session.first() }.getOrNull().orEmpty()
        // K6: fail CLOSED on an unreadable active session. Without it the client
        // session-pin can't be verified, so rather than send an unverified mark and
        // lean solely on the server, hold the whole pass and retry when the session
        // is readable (it normally is once the app context is warm). Only matters
        // if there is anything to send.
        if (activeSession.isBlank()) {
            val pending = runCatching { dao.sendable() }.getOrNull().orEmpty()
            if (pending.isNotEmpty()) {
                Log.w(TAG, "K6: active session unreadable — deferring flush (fail-closed)")
                return Result.retry()
            }
            return Result.success()
        }
        // Best-effort local date for the pre-send date-pin; the server is the true
        // guard. A LOCAL SimpleDateFormat (not a shared field) keeps this safe on
        // minSdk 24/25 without depending on core-library desugaring, and avoids the
        // known SimpleDateFormat thread-safety trap since the worker can run
        // concurrent instances.
        val serverToday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        var needsRetry = false

        for (row in dao.sendable()) {
            // ── PRE-SEND guards (session-pin / date-pin / structural) ──
            val pre = FlushDecision.preSend(row, activeSession, serverToday)
            if (pre != null) {
                dao.updateState(row.id, QueueState.FAILED_TERMINAL, pre.message, row.attempts, now())
                Log.d(TAG, "pre-send terminal ${row.id}: ${pre.message}")
                continue
            }

            dao.updateState(row.id, QueueState.SENDING, "Syncing", row.attempts, now())

            val outcome = try {
                val resp = api.saveAttendance(
                    className  = row.className,
                    section    = row.section,
                    absentJson = row.absentJson,
                    leaveJson  = row.leaveJson,
                    lateJson   = row.lateJson,
                    reason     = row.reason,
                    markDate   = row.dateIso           // date-pin sent to the server
                )
                val httpCode = resp.code()
                val transportOk = resp.isSuccessful
                val appStatus: String?
                val serverMsg: String?
                var isPartial = false
                if (transportOk) {
                    val body = resp.body()
                    appStatus = body?.get("status") as? String
                    serverMsg = (body?.get("message") as? String) ?: (body?.get("error") as? String)
                    isPartial = (body?.get("partial") as? Boolean) == true
                } else {
                    appStatus = null
                    serverMsg = parseErrorMessage(resp.errorBody()?.string())
                }
                FlushDecision.mapResponse(httpCode, transportOk, appStatus, serverMsg, row.attempts, isPartial)
            } catch (e: Exception) {
                Log.w(TAG, "flush IO error for ${row.id}", e)
                FlushDecision.mapResponse(0, false, null, null, row.attempts)
            }

            when (outcome.action) {
                FlushDecision.Action.DELETE -> dao.delete(row.id)

                FlushDecision.Action.TERMINAL ->
                    dao.updateState(row.id, QueueState.FAILED_TERMINAL, outcome.message, row.attempts + 1, now())

                FlushDecision.Action.RETRY -> {
                    dao.updateState(row.id, QueueState.FAILED_RETRYABLE, outcome.message, row.attempts + 1, now())
                    needsRetry = true
                }

                FlushDecision.Action.REAUTH -> {
                    // Never drop the row. Force a token refresh and let WorkManager
                    // re-run; the AuthInterceptor attaches the fresh token next pass.
                    // Increment attempts so the REAUTH cap (FlushDecision) can
                    // eventually park a permanently-unauthorised row as TERMINAL
                    // instead of spinning forever.
                    runCatching { firebaseAuth.currentUser?.getIdToken(true)?.await() }
                    dao.updateState(row.id, QueueState.PENDING, "Signing back in…", row.attempts + 1, now())
                    needsRetry = true
                }
            }
        }

        return if (needsRetry) Result.retry() else Result.success()
    }

    private fun now() = System.currentTimeMillis()

    private fun parseErrorMessage(errBody: String?): String? {
        if (errBody.isNullOrBlank()) return null
        return runCatching {
            val j = JSONObject(errBody)
            j.optString("message", "").ifBlank { j.optString("error", "") }.ifBlank { null }
        }.getOrNull()
    }
}
