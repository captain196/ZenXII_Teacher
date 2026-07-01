package com.schoolsync.teacher.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API service for admin panel endpoints.
 *
 * Phase 1 (2026-05): the teacher app now writes attendance through
 * the PHP backend (`/attendance/save`) instead of writing Firestore
 * directly. The backend enforces the time-stage gate, holiday/admission
 * validation, and audit logging.
 *
 * Phase 2: lock state + correction request submission are also
 * routed through here.
 */
interface ApiService {

    // ── Phase 0 — bridge to FCM push (kept for compatibility) ────
    @FormUrlEncoded
    @POST("attendance/teacher_notify")
    suspend fun teacherNotify(
        @Field("student_id") studentId: String,
        @Field("mark") mark: String,
        @Field("class") className: String,
        @Field("section") section: String,
        @Field("day") day: Int,
        @Field("month") month: String = ""
    ): Response<Map<String, Any>>

    // ── Phase 1 — smart save ─────────────────────────────────────
    /**
     * Default-Present save. Server expands the active roster and writes
     * Present for every student NOT in absent / leave / late.
     *
     * `absent`, `leave`, `late` are JSON-encoded strings sent as form fields.
     *   absent: ["STU0001","STU0007"]
     *   leave:  ["STU0012"]
     *   late:   [{"studentId":"STU0019","lateMinutes":12}]
     *
     * Date is server-resolved — DO NOT pass it.
     */
    @FormUrlEncoded
    @POST("attendance/save")
    suspend fun saveAttendance(
        @Field("class") className: String,
        @Field("section") section: String,
        @Field("absent") absentJson: String = "[]",
        @Field("leave") leaveJson: String = "[]",
        @Field("late") lateJson: String = "[]",
        @Field("reason") reason: String = ""
    ): Response<Map<String, Any>>

    // ── Phase 2 — lock state ─────────────────────────────────────
    @GET("attendance/lock")
    suspend fun getLock(
        @Query("class") className: String,
        @Query("section") section: String,
        @Query("date") date: String = ""
    ): Response<Map<String, Any>>

    // ── Phase 2 — correction submit ──────────────────────────────
    /**
     * `requestedMark` is a JSON-encoded {status, late?, lateMinutes?}.
     * Server enforces reason ≥10 chars and class-ownership.
     */
    @FormUrlEncoded
    @POST("attendance/correction/submit")
    suspend fun submitCorrection(
        @Field("studentId") studentId: String,
        @Field("date") date: String,
        @Field("requestedMark") requestedMarkJson: String,
        @Field("reason") reason: String
    ): Response<Map<String, Any>>

    // ── Phase 2 — list teacher's own corrections ─────────────────
    @GET("attendance/correction/list")
    suspend fun listCorrections(
        @Query("status") status: String = "pending",
        @Query("date") date: String = "",
        @Query("limit") limit: Int = 25,
        @Query("cursor") cursor: Long = 0
    ): Response<Map<String, Any>>

    // ── GPS Staff Self-Attendance (Firestore-backed; Bearer auth) ────
    /**
     * Record a GPS staff punch. The SERVER is the sole authority — it
     * verifies the token (uid == staffId), runs the geofence/window/leave/
     * holiday/lock checks, decides Present/Late/Rejected, and persists via
     * Staff_attendance_writer. The app only sends raw GPS + direction.
     */
    @POST("staff_attendance/punch")
    suspend fun staffPunch(@Body body: StaffPunchRequest): Response<Map<String, Any>>

    /** Read the authenticated staff member's own attendance (today + month + 30-day history). */
    @GET("staff_attendance/me")
    suspend fun staffMe(): Response<Map<String, Any>>
}

/**
 * JSON body for [ApiService.staffPunch]. Serialized by Gson; the PHP backend
 * reads it from the request body. `staffId` is intentionally ABSENT — the
 * server derives identity from the Firebase token, never from the client.
 */
data class StaffPunchRequest(
    val direction: String,            // "in" | "out"
    val lat: Double?,
    val lng: Double?,
    val accuracy: Float?,
    val mock: Boolean,
    val clientPunchId: String,        // idempotency / correlation key
    val clientCapturedAt: String?,    // ISO-8601 device capture time (audit only)
    val device: Map<String, String>? = null,
)
