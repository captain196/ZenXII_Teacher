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
        @Field("reason") reason: String = "",
        // C1 offline date-pin: the day the teacher actually marked. Empty (the
        // online default) = server resolves today, unchanged behaviour. When
        // sent, the server writes that date and refuses a mis-dated flush.
        @Field("mark_date") markDate: String = ""
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

    // ─── Transport: canonical driver trip lifecycle (D-8, 2026-08-08) ────
    //
    // Exactly the move Phase 1 made for attendance, now for transport. The app
    // used to write `tripLogs.status` straight to Firestore, which bypassed
    // Transport_service and so skipped driver-readiness re-validation, the CAS
    // transition guard, the audit row, the "bus started" parent notification,
    // route denormalisation, and GPS session setup. These two endpoints put the
    // admin panel and the driver app on ONE lifecycle.
    //
    // `trip_id` identifies the trip; the driver is NEVER sent — the server
    // derives identity from the Firebase token and refuses any trip not
    // assigned to that driver. Same discipline as staffPunch above.

    @FormUrlEncoded
    @POST("transport_driver_api/start_trip")
    suspend fun startTrip(@Field("trip_id") tripId: String): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("transport_driver_api/complete_trip")
    suspend fun completeTrip(
        @Field("trip_id") tripId: String,
        @Field("end_odometer") endOdometer: Double,
        @Field("distance_km") distanceKm: Double,
        @Field("fuel_used") fuelUsed: Double,
        @Field("notes") notes: String,
    ): Response<Map<String, Any>>

    // ─── F14 QR attendance (2026-08-16) ────────────────────────────────
    //
    // The rest of the same migration. `transportAttendance` withdrew its driver
    // client-write arm because rules cannot verify a QR HMAC and cannot resolve
    // a transport entitlement across studentRoutes status, effective dates,
    // session and service days — so a student holding a valid card but with no
    // transport facility could be recorded. Attendance now goes through
    // Transport_qr_attendance_service, which does all of that, and the driver is
    // derived from the Firebase token exactly as the lifecycle calls above.
    //
    // `client_event_id` is the idempotency key: a retried tap, a lost response
    // or a double scan must not produce a second boarding event.

    @FormUrlEncoded
    @POST("transport_driver_api/scan")
    suspend fun scanStudentQr(
        @Field("trip_id") tripId: String,
        @Field("qr") qr: String,
        @Field("confirm_alternate") confirmAlternate: String = "",
        @Field("client_event_id") clientEventId: String = "",
        @Field("client_event_at") clientEventAt: String = "",
    ): Response<Map<String, Any>>

    @GET("transport_driver_api/trip_roster")
    suspend fun tripRoster(@Query("trip_id") tripId: String): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("transport_driver_api/mark_all_alighted_at_school")
    suspend fun markAllAlightedAtSchool(
        @Field("trip_id") tripId: String,
        @Field("client_event_id") clientEventId: String = "",
    ): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("transport_driver_api/resolve_exception")
    suspend fun resolveException(
        @Field("trip_id") tripId: String,
        @Field("student_id") studentId: String,
        @Field("resolution") resolution: String,
        @Field("reason") reason: String = "",
        @Field("client_event_id") clientEventId: String = "",
    ): Response<Map<String, Any>>
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
    val integrityToken: String? = null, // Play Integrity attestation (null when disabled)
)
