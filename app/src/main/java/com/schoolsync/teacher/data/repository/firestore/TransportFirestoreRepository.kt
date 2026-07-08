package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.ClientMetadata
import com.schoolsync.teacher.data.model.firestore.FuelLogDoc
import com.schoolsync.teacher.data.model.firestore.RouteDoc
import com.schoolsync.teacher.data.model.firestore.SosAlertDoc
import com.schoolsync.teacher.data.model.firestore.StaffDoc
import com.schoolsync.teacher.data.model.firestore.StudentRouteDoc
import com.schoolsync.teacher.data.model.firestore.TransportAttendanceDoc
import com.schoolsync.teacher.data.model.firestore.TripLogDoc
import com.schoolsync.teacher.data.model.firestore.VehicleDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for transport-related data from the teacher side.
 * Supports reading routes, vehicles, SOS alerts, and triggering SOS.
 *
 * Collections used (Firestore — the only datastore):
 * - studentRoutes: per-student route assignment
 * - routes: route definitions with stops
 * - vehicles: vehicle metadata
 * - sosAlerts: emergency alerts from transport staff
 *
 * Phase 1 Logical Change 4A (2026-07-03): the legacy RTDB touchpoints
 * (observeVehicleLive → /VehicleLive, and the SOSAlerts RTDB mirror in
 * triggerSos) were removed — both were dead code and violated the absolute
 * "RTDB does not exist" rule. SOS now persists to Firestore only.
 */
@Singleton
class TransportFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the route assignment for a specific student.
     * Query: schoolId + studentId.
     */
    suspend fun getStudentRoute(studentId: String): Result<StudentRouteDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docs = firestoreService.queryDocumentsAs<StudentRouteDoc>(
                Constants.Firestore.STUDENT_ROUTES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .limit(1)
            }
            Result.success(docs.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a single route document by its ID.
     */
    suspend fun getRoute(routeId: String): Result<RouteDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<RouteDoc>(
                Constants.Firestore.ROUTES,
                routeId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all routes for the current school.
     */
    suspend fun getRoutes(): Result<List<RouteDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val routes = firestoreService.queryDocumentsAs<RouteDoc>(
                Constants.Firestore.ROUTES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("routeName")
            }
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a single vehicle document by its ID.
     */
    suspend fun getVehicle(vehicleId: String): Result<VehicleDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<VehicleDoc>(
                Constants.Firestore.VEHICLES,
                vehicleId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all vehicles for the current school.
     */
    suspend fun getVehicles(): Result<List<VehicleDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val vehicles = firestoreService.queryDocumentsAs<VehicleDoc>(
                Constants.Firestore.VEHICLES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
            }
            Result.success(vehicles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch active SOS alerts for the current school, ordered by most recent first.
     */
    suspend fun getSosAlerts(): Result<List<SosAlertDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val alerts = firestoreService.queryDocumentsAs<SosAlertDoc>(
                Constants.Firestore.SOS_ALERTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("active", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Trigger an SOS alert from the teacher/transport staff side.
     * Persists a Firestore document (the only datastore).
     *
     * F9 (2026-07-07) — severity picker (Low/Medium/High/Critical); default
     * 'High' per Refinement 1 (bias toward operational safety). Priority
     * routing lives server-side in Transport_notifier::SOS_SEVERITY_TO_PRIORITY.
     * Client diagnostic metadata (Refinement additional) attached via
     * ClientMetadata.asMap() — not authz-participating, retained for
     * troubleshooting.
     *
     * @return The generated alert document ID.
     */
    suspend fun triggerSos(
        vehicleId: String,
        routeId: String,
        alertType: String = "emergency",
        severity: String = "High",
        lat: Double,
        lng: Double,
        message: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        if (severity !in ALLOWED_SEVERITIES) {
            return Result.failure(IllegalArgumentException("Invalid severity: $severity"))
        }
        if (alertType !in ALLOWED_ALERT_TYPES) {
            return Result.failure(IllegalArgumentException("Invalid alertType: $alertType"))
        }

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = buildMap<String, Any> {
            put("schoolId", schoolCode)
            put("vehicleId", vehicleId)
            put("routeId", routeId)
            put("alertType", alertType)
            put("severity", severity)
            put("lat", lat)
            put("lng", lng)
            put("message", message)
            put("triggeredBy", teacherId)
            put("triggeredByRole", "driver")
            put("triggeredByName", teacherName)
            put("active", true)
            put("status", "active")
            put("createdAt", FieldValue.serverTimestamp())
            putAll(ClientMetadata.asMap())
        }

        return try {
            firestoreService.setDocument(Constants.Firestore.SOS_ALERTS, docId, data)
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  F9 (2026-07-07) — Driver identity + trip lifecycle + attendance +
    //  fuel writes. All operational writes carry ClientMetadata for
    //  diagnostics (Refinement additional). Every write goes DIRECTLY to
    //  Firestore; discipline enforced by firestore.rules driver-scope
    //  helpers (driverIs, isDriverOfTrip, hasClientMetadata).
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Fetch the currently signed-in staff's driver info. Returns null if
     * the staff doc doesn't have flags.can_drive_transport==true or doesn't
     * exist at all (non-driver caller).
     */
    suspend fun getMyDriverInfo(): Result<StaffDoc?> {
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        return try {
            val doc = firestoreService.getDocumentAs<StaffDoc>(Constants.Firestore.STAFF, teacherId)
            if (doc == null) return Result.success(null)
            val canDrive = (doc.flags?.get("can_drive_transport") as? Boolean) ?: false
            Result.success(if (canDrive) doc else null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Find the vehicle assigned to the signed-in driver (queries
     * vehicles where staff_id == myUserId). Returns null if none.
     */
    suspend fun getMyAssignedVehicle(): Result<VehicleDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        return try {
            val docs = firestoreService.queryDocumentsAs<VehicleDoc>(Constants.Firestore.VEHICLES) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("staff_id", teacherId)
                    .limit(1)
            }
            Result.success(docs.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List today's active trips (Ready + InProgress) assigned to the
     * signed-in driver. Backed by the F9 composite index
     * (schoolId, driver_staff_id, status, date DESC).
     */
    suspend fun getMyActiveTrips(dateIso: String = todayIso()): Result<List<TripLogDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        return try {
            val docs = firestoreService.queryDocumentsAs<TripLogDoc>(Constants.Firestore.TRIP_LOGS) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("driver_staff_id", teacherId)
                    .whereEqualTo("date", dateIso)
                    .whereIn("status", listOf("Ready", "InProgress"))
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(50)
            }
            Result.success(docs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Start a Ready trip (Refinement 2 — assigned driver is the normal
     * path; admin override is a separate PHP-admin flow, fully audited).
     * Firestore rule allows only field-level updates on the trip.
     */
    suspend fun startMyTrip(tripId: String): Result<Unit> {
        val update = mapOf(
            "status" to "InProgress",
            "actual_start_at" to nowIso(),
            "updated_at" to nowIso()
        ) + ClientMetadata.asMap()
        return try {
            firestoreService.updateDocument(Constants.Firestore.TRIP_LOGS, tripId, update)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a checkpoint as reached. Requires the current trip doc so we
     * can mutate the checkpoints[] array (Firestore has no partial-array
     * update primitive that preserves surrounding fields — whole array
     * write is the safe path).
     */
    suspend fun markCheckpointReached(tripId: String, stopId: String): Result<Unit> {
        return updateCheckpointState(tripId, stopId, "Reached", null)
    }

    suspend fun markCheckpointSkipped(tripId: String, stopId: String, reason: String): Result<Unit> {
        return updateCheckpointState(tripId, stopId, "Skipped", reason)
    }

    private suspend fun updateCheckpointState(
        tripId: String,
        stopId: String,
        newState: String,
        reason: String?
    ): Result<Unit> {
        return try {
            val trip = firestoreService.getDocumentAs<TripLogDoc>(Constants.Firestore.TRIP_LOGS, tripId)
                ?: return Result.failure(Exception("Trip not found: $tripId"))
            val checkpoints = trip.checkpoints.map { cp ->
                if ((cp["stop_id"] as? String) == stopId) {
                    val mutable = cp.toMutableMap()
                    mutable["checkpoint_state"] = newState
                    when (newState) {
                        "Reached" -> mutable["reached_at"] = nowIso()
                        "Skipped" -> {
                            mutable["skipped_at"] = nowIso()
                            mutable["skip_reason"] = reason ?: ""
                        }
                    }
                    mutable.toMap()
                } else cp
            }
            val reachedOrSkipped = checkpoints.count {
                it["checkpoint_state"] in listOf("Reached", "Skipped")
            }
            val progressPct = if (checkpoints.isNotEmpty())
                (reachedOrSkipped.toDouble() / checkpoints.size * 100).toInt() else 0
            val nextPending = checkpoints.firstOrNull { it["checkpoint_state"] == "Pending" }
                ?.let { it["stop_id"] as? String } ?: ""
            val update = mapOf<String, Any?>(
                "checkpoints" to checkpoints,
                "route_progress_pct" to progressPct,
                "next_checkpoint_id" to nextPending,
                "updated_at" to nowIso()
            ) + ClientMetadata.asMap()
            firestoreService.updateDocument(Constants.Firestore.TRIP_LOGS, tripId, update)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Write a boarding event to transportAttendance. Deterministic doc-id → idempotent. */
    suspend fun markStudentBoarded(
        tripId: String,
        studentId: String,
        actualStopId: String? = null,
        notes: String = ""
    ): Result<String> = writeBoardingEvent(tripId, studentId, "boarded_pickup", actualStopId, notes)

    suspend fun markStudentDropped(
        tripId: String,
        studentId: String,
        actualStopId: String? = null,
        notes: String = ""
    ): Result<String> = writeBoardingEvent(tripId, studentId, "dropped_home", actualStopId, notes)

    suspend fun markStudentNoShow(
        tripId: String,
        studentId: String,
        notes: String = ""
    ): Result<String> = writeBoardingEvent(tripId, studentId, "no_show", null, notes)

    private suspend fun writeBoardingEvent(
        tripId: String,
        studentId: String,
        eventType: String,
        actualStopId: String?,
        notes: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val docId = "${schoolCode}_${tripId}_${studentId}_${eventType}"
        // Look up the trip for direction/route/gps_session_id inheritance
        val trip = firestoreService.getDocumentAs<TripLogDoc>(Constants.Firestore.TRIP_LOGS, tripId)
            ?: return Result.failure(Exception("Trip not found: $tripId"))
        val data = buildMap<String, Any> {
            put("id", docId)
            put("schoolId", schoolCode)
            put("tripId", tripId)
            put("studentId", studentId)
            put("route_id", trip.routeId)
            put("session", "")   // server-side createTrip sets session; leave blank here to avoid drift
            put("trip_date", trip.date)
            put("trip_direction", trip.tripDirection)
            put("actual_stop_id", actualStopId ?: "")
            put("event_type", eventType)
            put("event_at", FieldValue.serverTimestamp())
            put("marked_by_staff_id", teacherId)
            put("marked_by_role", "driver")   // F9-4 gate
            put("notes", notes)
            put("gps_session_id", trip.gpsSessionId)
            put("created_at", FieldValue.serverTimestamp())
            putAll(ClientMetadata.asMap())
        }
        return try {
            firestoreService.setDocument(Constants.Firestore.TRANSPORT_ATTENDANCE, docId, data)
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Complete a trip. Updates only the driver-mutable fields; rules
     * enforce field-level discipline.
     */
    suspend fun completeMyTrip(
        tripId: String,
        endOdometer: Double,
        distance: Double,
        fuelUsed: Double,
        notes: String = ""
    ): Result<Unit> {
        val update = mapOf<String, Any?>(
            "status" to "Completed",
            "actual_end_at" to nowIso(),
            "endTime" to nowIso(),
            "endOdometer" to endOdometer,
            "distance" to distance,
            "distance_km" to distance,
            "fuelUsed" to fuelUsed,
            "notes" to notes,
            "updated_at" to nowIso()
        ) + ClientMetadata.asMap()
        return try {
            firestoreService.updateDocument(Constants.Firestore.TRIP_LOGS, tripId, update)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a fuel log. Refinement 3: rules restrict driver writes to
     * the driver's currently assigned vehicle. Passing a foreign vehicle
     * id will be rejected at the Firestore-rule layer with a clean
     * PermissionDenied signal.
     */
    suspend fun submitFuelLog(
        vehicleId: String,
        litres: Double,
        pricePerLitre: Double,
        totalAmount: Double,
        odometerKm: Double,
        station: String,
        notes: String = ""
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val docId = "${schoolCode}_FUEL_${System.currentTimeMillis()}_${teacherId}"
        val data = buildMap<String, Any> {
            put("schoolId", schoolCode)
            put("vehicle_id", vehicleId)
            put("litres", litres)
            put("price_per_litre", pricePerLitre)
            put("total_amount", totalAmount)
            put("odometer_km", odometerKm)
            put("station", station)
            put("notes", notes)
            put("logged_by_staff_id", teacherId)
            put("logged_by_role", "driver")
            put("fuel_date", todayIso())
            put("created_at", FieldValue.serverTimestamp())
            putAll(ClientMetadata.asMap())
        }
        return try {
            firestoreService.setDocument(Constants.Firestore.FUEL_LOGS, docId, data)
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private companion object {
        val ALLOWED_SEVERITIES = setOf("Low", "Medium", "High", "Critical")
        val ALLOWED_ALERT_TYPES = setOf("emergency", "breakdown", "accident", "medical", "other")
    }

    private fun todayIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date())
    }

    private fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date())
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }
}
