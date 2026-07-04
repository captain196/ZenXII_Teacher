package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.remote.ApiService
import com.schoolsync.teacher.data.remote.StaffPunchRequest
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StaffAttendanceRepository — the ONLY backend-communication surface for GPS
 * staff attendance (Phase 12).
 *
 * Strict boundaries:
 *   - Talks to the Firestore-backed PHP endpoints staff_attendance/punch and
 *     staff_attendance/me via the shared [ApiService]. Bearer auth is attached
 *     automatically by the existing AuthInterceptor — NO token handling here.
 *   - Takes already-acquired coordinates as parameters: it has NO GPS logic
 *     and NO UI logic. The ViewModel supplies the fix; the server validates.
 *   - Maps every failure (network / auth / timeout / server validation) into a
 *     typed [StaffAttendanceError]. No attendance business rules live here —
 *     the backend is the sole authority.
 */
@Singleton
class StaffAttendanceRepository @Inject constructor(
    private val api: ApiService,
) {
    companion object { private const val TAG = "StaffAttendanceRepo" }

    /** Submit a punch. `direction` = "in" | "out". Coordinates come from the location layer. */
    suspend fun punch(
        direction: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        mock: Boolean,
        clientPunchId: String,
        clientCapturedAt: String?,
        device: Map<String, String>? = null,
    ): Result<PunchResult> = runCatching {
        val resp = api.staffPunch(
            StaffPunchRequest(
                direction = direction,
                lat = lat,
                lng = lng,
                accuracy = accuracy,
                mock = mock,
                clientPunchId = clientPunchId,
                clientCapturedAt = clientCapturedAt,
                device = device,
            )
        )
        if (!resp.isSuccessful) throw resp.toError()
        val body = resp.body() ?: throw StaffAttendanceError.Network("Empty response")
        PunchResult(
            direction = body["direction"] as? String ?: direction,
            status = body["status"] as? String,                 // P|T|null
            mark = body["mark"] as? String,
            lateMinutes = (body["lateMinutes"] as? Number)?.toInt() ?: 0,
            distanceMeters = (body["distanceMeters"] as? Number)?.toDouble(),
            checkInAt = body["checkInAt"] as? String,
            message = body["message"] as? String ?: "",
        )
    }.recoverCatching { e -> throw e.asStaffError("punch") }

    /** Read the staff member's own attendance (today + month summary + 30-day history). */
    suspend fun me(): Result<MyAttendance> = runCatching {
        val resp = api.staffMe()
        if (!resp.isSuccessful) throw resp.toError()
        val body = resp.body() ?: throw StaffAttendanceError.Network("Empty response")

        val todayMap = body["today"] as? Map<*, *> ?: emptyMap<String, Any>()
        val monthMap = body["month"] as? Map<*, *> ?: emptyMap<String, Any>()
        val historyRaw = body["history"] as? List<*> ?: emptyList<Any>()

        MyAttendance(
            staffId = body["staffId"] as? String ?: "",
            today = TodayAttendance(
                date = todayMap["date"] as? String ?: "",
                status = todayMap["status"] as? String ?: "V",
                checkInAt = todayMap["checkInAt"] as? String,
                checkOutAt = todayMap["checkOutAt"] as? String,
                isWeeklyOff = todayMap["isWeeklyOff"] as? Boolean ?: false,
                isHoliday = todayMap["isHoliday"] as? Boolean ?: false,
                allowWorkOnOff = todayMap["allowWorkOnOff"] as? Boolean ?: false,
            ),
            month = MonthSummary(
                monthKey = monthMap["monthKey"] as? String ?: "",
                present = (monthMap["present"] as? Number)?.toInt() ?: 0,
                absent = (monthMap["absent"] as? Number)?.toInt() ?: 0,
                leave = (monthMap["leave"] as? Number)?.toInt() ?: 0,
                holiday = (monthMap["holiday"] as? Number)?.toInt() ?: 0,
                tardy = (monthMap["tardy"] as? Number)?.toInt() ?: 0,
                halfDay = (monthMap["halfDay"] as? Number)?.toInt() ?: 0,
                extraWorked = (monthMap["extraWorked"] as? Number)?.toInt() ?: 0,
                weeklyOff = (monthMap["weeklyOff"] as? Number)?.toInt() ?: 0,
                workingDays = (monthMap["workingDays"] as? Number)?.toInt() ?: 0,
            ),
            history = historyRaw.mapNotNull {
                val m = it as? Map<*, *> ?: return@mapNotNull null
                HistoryDay(
                    date = m["date"] as? String ?: return@mapNotNull null,
                    status = m["status"] as? String ?: "V",
                )
            },
        )
    }.recoverCatching { e -> throw e.asStaffError("me") }

    // ── error mapping ────────────────────────────────────────────

    /** Map an unsuccessful HTTP response → typed error, preserving the server message. */
    private fun Response<Map<String, Any>>.toError(): StaffAttendanceError {
        val msg = errorBody()?.string()?.let { raw ->
            runCatching { org.json.JSONObject(raw).optString("message", "") }.getOrNull()
        } ?: ""
        return when (code()) {
            401 -> StaffAttendanceError.Auth(msg.ifBlank { "Session expired. Please sign in again." })
            403 -> StaffAttendanceError.Forbidden(msg.ifBlank { "Not permitted." })
            422 -> StaffAttendanceError.Disabled(msg.ifBlank { "GPS attendance is not enabled." })
            409 -> StaffAttendanceError.Rejected(msg.ifBlank { "Attendance could not be recorded." })
            400 -> StaffAttendanceError.Rejected(msg.ifBlank { "Invalid request." })
            in 500..599 -> StaffAttendanceError.Server(msg.ifBlank { "Server error. Please retry." })
            else -> StaffAttendanceError.Network(msg.ifBlank { "Network error (${code()})" })
        }
    }

    /** Normalize transport-level throwables → typed errors; pass through already-typed ones. */
    private fun Throwable.asStaffError(op: String): StaffAttendanceError {
        if (this is StaffAttendanceError) return this
        Log.e(TAG, "$op failed", this)
        return when (this) {
            is SocketTimeoutException -> StaffAttendanceError.Timeout("Request timed out. Please retry.")
            is IOException -> StaffAttendanceError.Network("No internet connection.")
            else -> StaffAttendanceError.Network(message ?: "Unexpected error.")
        }
    }
}

// ── Result models (domain data; UI renders independently) ─────────

data class PunchResult(
    val direction: String,
    val status: String?,           // P | T | null (checkout / already)
    val mark: String?,
    val lateMinutes: Int,
    val distanceMeters: Double?,
    val checkInAt: String?,
    val message: String,
)

data class MyAttendance(
    val staffId: String,
    val today: TodayAttendance,
    val month: MonthSummary,
    val history: List<HistoryDay>,
)

data class TodayAttendance(
    val date: String,
    val status: String,            // P|T|M|A|L|H|O|W|V
    val checkInAt: String?,
    val checkOutAt: String?,
    val isWeeklyOff: Boolean = false,
    val isHoliday: Boolean = false,
    val allowWorkOnOff: Boolean = false,
)

data class MonthSummary(
    val monthKey: String,
    val present: Int,
    val absent: Int,
    val leave: Int,
    val holiday: Int,
    val tardy: Int,
    val halfDay: Int = 0,
    val extraWorked: Int = 0,
    val weeklyOff: Int = 0,
    val workingDays: Int,
)

data class HistoryDay(
    val date: String,
    val status: String,
)

// ── Typed errors (network / auth / timeout / server validation) ───

sealed class StaffAttendanceError(message: String) : Exception(message) {
    class Network(msg: String)   : StaffAttendanceError(msg)   // no connectivity / transport
    class Timeout(msg: String)   : StaffAttendanceError(msg)   // request timed out
    class Auth(msg: String)      : StaffAttendanceError(msg)   // 401 — token invalid/expired
    class Forbidden(msg: String) : StaffAttendanceError(msg)   // 403 — role/cross-school
    class Disabled(msg: String)  : StaffAttendanceError(msg)   // 422 — GPS not enabled
    class Rejected(msg: String)  : StaffAttendanceError(msg)   // 409/400 — geofence/window/leave/holiday/lock/mock
    class Server(msg: String)    : StaffAttendanceError(msg)   // 5xx — backend failure
}
