package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * TripLogDoc — Firestore trip record.
 *
 * F9 (2026-07-07) — additive fields land here matching what F7/F8 write
 * on the PHP side. The Kotlin base fields (id, schoolId, vehicleId,
 * date, tripType, startTime, endTime, startOdometer, endOdometer,
 * distance, fuelUsed, stopsCompleted, alerts) stay unchanged so any
 * pre-existing consumer keeps working.
 *
 * Field-name mapping to PHP additive schema:
 *   driverStaffId → driver_staff_id
 *   tripDirection → trip_direction  (Pickup|Drop)
 *   actualStartAt → actual_start_at
 *   actualEndAt   → actual_end_at
 *   rosterStudentIds → roster_student_ids
 *   checkpoints   → checkpoints[]
 *   tripKind      → trip_kind  (Regular|Event)
 *   eventId       → event_id  (F8 linkage; null for Regular trips)
 *   gpsSessionId  → gps_session_id  (GPSSESS_{tripId})
 *   status        → status  (Ready|InProgress|Completed|Cancelled)
 */
data class TripLogDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val vehicleId: String = "",
    val date: String = "",
    val tripType: String = "morning",      // morning, afternoon
    val startTime: String = "",
    val endTime: String = "",
    val startOdometer: Double = 0.0,
    val endOdometer: Double = 0.0,
    val distance: Double = 0.0,
    val fuelUsed: Double = 0.0,
    val stopsCompleted: Int = 0,
    val alerts: List<String> = emptyList(),

    // ── F9 additive fields (map to PHP additive schema) ─────────────────
    @get:com.google.firebase.firestore.PropertyName("driver_staff_id")
    @set:com.google.firebase.firestore.PropertyName("driver_staff_id")
    var driverStaffId: String = "",

    @get:com.google.firebase.firestore.PropertyName("trip_direction")
    @set:com.google.firebase.firestore.PropertyName("trip_direction")
    var tripDirection: String = "",

    @get:com.google.firebase.firestore.PropertyName("route_id")
    @set:com.google.firebase.firestore.PropertyName("route_id")
    var routeId: String = "",

    @get:com.google.firebase.firestore.PropertyName("actual_start_at")
    @set:com.google.firebase.firestore.PropertyName("actual_start_at")
    var actualStartAt: Any? = null,

    @get:com.google.firebase.firestore.PropertyName("actual_end_at")
    @set:com.google.firebase.firestore.PropertyName("actual_end_at")
    var actualEndAt: Any? = null,

    @get:com.google.firebase.firestore.PropertyName("roster_student_ids")
    @set:com.google.firebase.firestore.PropertyName("roster_student_ids")
    var rosterStudentIds: List<String> = emptyList(),

    var checkpoints: List<Map<String, Any?>> = emptyList(),

    @get:com.google.firebase.firestore.PropertyName("trip_kind")
    @set:com.google.firebase.firestore.PropertyName("trip_kind")
    var tripKind: String = "Regular",

    @get:com.google.firebase.firestore.PropertyName("event_id")
    @set:com.google.firebase.firestore.PropertyName("event_id")
    var eventId: String = "",

    @get:com.google.firebase.firestore.PropertyName("gps_session_id")
    @set:com.google.firebase.firestore.PropertyName("gps_session_id")
    var gpsSessionId: String = "",

    var status: String = ""
)
