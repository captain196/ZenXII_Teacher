package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.remote.ApiService
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 + 2 — HTTP-only attendance writer.
 *
 * Writes go through the PHP backend so the time-stage gate, holiday +
 * admission validation, and audit log all apply consistently. Reads
 * stay on Firestore (the existing AttendanceFirestoreRepository) for
 * the today-listener and roster fetch.
 *
 * Result models are kept light: callers get `Result<…>` containing
 * either a typed success payload or an [AttendanceApiError] that the
 * UI maps to user copy.
 */
@Singleton
class AttendanceApiRepository @Inject constructor(
    private val api: ApiService
) {
    companion object { private const val TAG = "AttendanceApiRepo" }

    // ── Public surface ───────────────────────────────────────────

    suspend fun save(
        className: String,
        section: String,
        absent: List<String>,
        leave: List<String>,
        late: List<LateMark>,
        reason: String = ""
    ): Result<SaveResult> = runCatching {
        val resp = api.saveAttendance(
            className   = className,
            section     = section,
            absentJson  = JSONArray(absent).toString(),
            leaveJson   = JSONArray(leave).toString(),
            lateJson    = JSONArray(late.map {
                JSONObject(mapOf("studentId" to it.studentId, "lateMinutes" to it.lateMinutes))
            }).toString(),
            reason      = reason
        )
        if (!resp.isSuccessful) throw resp.toError()
        val body = resp.body() ?: throw AttendanceApiError.Network("Empty body")
        SaveResult(
            updated     = (body["updated"]  as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            rejected    = (body["rejected"] as? List<*>)?.mapNotNull {
                val m = it as? Map<*, *> ?: return@mapNotNull null
                Rejection(
                    studentId = m["studentId"] as? String ?: "",
                    reason    = m["reason"]    as? String ?: ""
                )
            } ?: emptyList(),
            stage       = body["stage"] as? String ?: "UNKNOWN",
            date        = body["date"]  as? String ?: "",
            rosterSize  = (body["rosterSize"] as? Number)?.toInt() ?: 0
        )
    }.recoverCatching { e ->
        if (e is AttendanceApiError) throw e
        Log.e(TAG, "save failed", e)
        throw AttendanceApiError.Network(e.message ?: "Network error")
    }

    suspend fun fetchLock(
        className: String,
        section: String,
        date: String = ""
    ): Result<LockState> = runCatching {
        val resp = api.getLock(className, section, date)
        if (!resp.isSuccessful) throw resp.toError()
        val body = resp.body() ?: throw AttendanceApiError.Network("Empty body")
        val lock = body["lock"] as? Map<*, *> ?: emptyMap<String, Any>()
        LockState(
            stage      = body["stage"] as? String ?: "UNKNOWN",
            date       = body["date"]  as? String ?: date,
            locked     = lock["locked"] as? Boolean ?: false,
            lockedBy   = lock["lockedBy"]   as? String,
            lockedAt   = lock["lockedAt"]   as? String,
            unlockedAt = lock["unlockedAt"] as? String
        )
    }.recoverCatching { e ->
        if (e is AttendanceApiError) throw e
        Log.e(TAG, "fetchLock failed", e)
        throw AttendanceApiError.Network(e.message ?: "Network error")
    }

    suspend fun submitCorrection(
        studentId: String,
        date: String,
        requestedStatus: String,    // "P" | "A" | "L"
        requestedLate: Boolean,
        requestedLateMinutes: Int,
        reason: String
    ): Result<String> = runCatching {
        val mark = JSONObject(mapOf(
            "status"      to requestedStatus,
            "late"        to requestedLate,
            "lateMinutes" to requestedLateMinutes.coerceIn(0, 180)
        )).toString()
        val resp = api.submitCorrection(studentId, date, mark, reason)
        if (!resp.isSuccessful) throw resp.toError()
        val body = resp.body() ?: throw AttendanceApiError.Network("Empty body")
        body["requestId"] as? String ?: throw AttendanceApiError.Network("No requestId in response")
    }.recoverCatching { e ->
        if (e is AttendanceApiError) throw e
        Log.e(TAG, "submitCorrection failed", e)
        throw AttendanceApiError.Network(e.message ?: "Network error")
    }

    /** Pull the teacher's own pending requests (server applies requestedBy=self filter). */
    suspend fun listMyPending(): Result<List<PendingCorrection>> = runCatching {
        val resp = api.listCorrections(status = "pending")
        if (!resp.isSuccessful) throw resp.toError()
        val body = resp.body() ?: throw AttendanceApiError.Network("Empty body")
        val rows = body["requests"] as? List<*> ?: emptyList<Any>()
        rows.mapNotNull {
            val m = it as? Map<*, *> ?: return@mapNotNull null
            PendingCorrection(
                requestId   = m["requestId"]   as? String ?: "",
                studentId   = m["studentId"]   as? String ?: "",
                studentName = m["studentName"] as? String ?: "",
                date        = m["date"]        as? String ?: "",
                reason      = m["reason"]      as? String ?: ""
            )
        }
    }.recoverCatching { e ->
        if (e is AttendanceApiError) throw e
        Log.e(TAG, "listMyPending failed", e)
        throw AttendanceApiError.Network(e.message ?: "Network error")
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun retrofit2.Response<Map<String, Any>>.toError(): AttendanceApiError {
        val msg = errorBody()?.string()?.let { runCatching {
            JSONObject(it).optString("message", "")
        }.getOrNull() } ?: ""
        return when (code()) {
            400 -> AttendanceApiError.ReasonRequired(msg.ifBlank { "Reason required (≥10 chars)" })
            403 -> AttendanceApiError.Forbidden(msg.ifBlank { "Not authorized for this class." })
            404 -> AttendanceApiError.NotFound(msg.ifBlank { "Not found." })
            409 -> AttendanceApiError.Conflict(msg.ifBlank { "Conflict." })
            422 -> AttendanceApiError.Holiday(msg.ifBlank { "Cannot mark on a holiday." })
            423 -> AttendanceApiError.Locked(msg.ifBlank { "Attendance is locked." })
            else -> AttendanceApiError.Network(msg.ifBlank { "Network error (${code()})" })
        }
    }
}

// ── Models ───────────────────────────────────────────────────────

data class LateMark(val studentId: String, val lateMinutes: Int)

data class SaveResult(
    val updated: List<String>,
    val rejected: List<Rejection>,
    val stage: String,
    val date: String,
    val rosterSize: Int
)

data class Rejection(val studentId: String, val reason: String)

data class LockState(
    val stage: String,
    val date: String,
    val locked: Boolean,
    val lockedBy: String?,
    val lockedAt: String?,
    val unlockedAt: String?
)

data class PendingCorrection(
    val requestId: String,
    val studentId: String,
    val studentName: String,
    val date: String,
    val reason: String
)

sealed class AttendanceApiError(message: String) : Exception(message) {
    class ReasonRequired(msg: String) : AttendanceApiError(msg)
    class Forbidden(msg: String)      : AttendanceApiError(msg)
    class NotFound(msg: String)       : AttendanceApiError(msg)
    class Conflict(msg: String)       : AttendanceApiError(msg)
    class Holiday(msg: String)        : AttendanceApiError(msg)
    class Locked(msg: String)         : AttendanceApiError(msg)
    class Network(msg: String)        : AttendanceApiError(msg)
}
