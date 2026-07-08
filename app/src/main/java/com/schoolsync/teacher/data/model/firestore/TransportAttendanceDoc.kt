package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * TransportAttendanceDoc — per-student boarding event on a trip.
 *
 * F7 established the schema; F9 (2026-07-07) opens the driver-write
 * path from the Teacher app. Doc-id is deterministic:
 *   {schoolId}_{tripId}_{studentId}_{event_type}
 * Repeat driver taps merge into the same doc rather than duplicating
 * (F7-4 gate preserved).
 *
 * event_type ∈ {boarded_pickup, dropped_home, no_show, alternate_pickup}
 * marked_by_role ∈ {'admin', 'driver'}  (F9-4 verifier gate)
 *
 * GPS-native fields (event_lat, event_lng, event_at_gps,
 * geofence_variance_m, geofence_verified) stay NULL at F9 — populated
 * by the future dedicated GPS phase without schema redesign.
 */
data class TransportAttendanceDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val tripId: String = "",
    val studentId: String = "",

    @get:PropertyName("route_id") @set:PropertyName("route_id")
    var routeId: String = "",

    val session: String = "",

    @get:PropertyName("trip_date") @set:PropertyName("trip_date")
    var tripDate: String = "",

    @get:PropertyName("trip_direction") @set:PropertyName("trip_direction")
    var tripDirection: String = "",

    @get:PropertyName("planned_stop_id") @set:PropertyName("planned_stop_id")
    var plannedStopId: String = "",

    @get:PropertyName("actual_stop_id") @set:PropertyName("actual_stop_id")
    var actualStopId: String = "",

    @get:PropertyName("event_type") @set:PropertyName("event_type")
    var eventType: String = "",

    @get:PropertyName("event_at") @set:PropertyName("event_at")
    var eventAt: Any? = null,

    @get:PropertyName("marked_by_staff_id") @set:PropertyName("marked_by_staff_id")
    var markedByStaffId: String = "",

    @get:PropertyName("marked_by_role") @set:PropertyName("marked_by_role")
    var markedByRole: String = "driver",

    val notes: String = "",

    // GPS-native reserved fields (NULL at F9; populated by future GPS phase)
    @get:PropertyName("gps_session_id") @set:PropertyName("gps_session_id")
    var gpsSessionId: String = "",

    @get:PropertyName("correlation_id") @set:PropertyName("correlation_id")
    var correlationId: String = "",

    @get:PropertyName("event_lat") @set:PropertyName("event_lat")
    var eventLat: Any? = null,

    @get:PropertyName("event_lng") @set:PropertyName("event_lng")
    var eventLng: Any? = null
)
