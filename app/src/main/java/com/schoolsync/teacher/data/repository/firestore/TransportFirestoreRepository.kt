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
import com.schoolsync.teacher.data.model.firestore.TripLogDoc
import com.schoolsync.teacher.data.model.firestore.VehicleDoc
import java.util.UUID
import com.schoolsync.teacher.data.model.ScanOutcome
import com.schoolsync.teacher.data.model.TripRoster
import com.schoolsync.teacher.data.remote.ApiService
import com.schoolsync.teacher.util.Constants
import retrofit2.Response
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
    private val tokenManager: TokenManager,
    // D-8: trip STATE transitions go through the canonical PHP lifecycle.
    // Everything else in this repository stays Firestore-direct — only the
    // lifecycle moved, because only the lifecycle carries business rules.
    private val apiService: ApiService
) {

    /**
     * Fetch the route assignment for a specific student.
     * Query: schoolId + studentId.
     */
    /**
     * TRN-SESS-B (2026-08-15) — the CURRENT transport assignment, converging on
     * the backend's canonical definition.
     *
     * This used to filter on schoolId + studentId only, with limit(1) and no
     * ordering. An unordered limit(1) returns an ARBITRARY matching row, so a
     * student carrying a prior-session or deactivated studentRoutes document
     * could resolve to the wrong route and stop.
     *
     * The backend has always been stricter (Transport_service.php:6914-6921):
     *     [studentId == X, status == 'Active']  then  session == sessionYear
     * The session comes from the existing getSession() path used by the
     * Attendance, CampusLife, Exam, Fee and Gallery repositories — no new
     * session source, no second abstraction.
     *
     * FAIL CLOSED on a blank session: the session-less query would span every
     * session, and a driver resolving the wrong stop is a safety problem, not a
     * cosmetic one.
     */
    suspend fun getStudentRoute(studentId: String): Result<StudentRouteDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not set"))

        return try {
            val docs = firestoreService.queryDocumentsAs<StudentRouteDoc>(
                Constants.Firestore.STUDENT_ROUTES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("status", "Active")
                    .whereEqualTo("session", session)
                    .limit(1)
            }
            Result.success(docs.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Canonical current academic session, identical to the accessor used by
     * AttendanceFirestoreRepository:340, CampusLifeFirestoreRepository:447,
     * ExamFirestoreRepository:299 and FeeFirestoreRepository:429. Ultimately
     * sourced from schools/{schoolId}.currentSession (AuthRepository:107-114).
     */
    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
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
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            // Staff document ids are school-prefixed: staff/{schoolId}_{userId}
            // — the same construction AuthRepository.readStaffProfile() uses.
            // Reading bare `staff/STA0001` finds nothing, and a missing document
            // is indistinguishable here from "not a driver", so every real
            // driver was shown "transport-driving not enabled in HR" no matter
            // what the flag actually said.
            val doc = firestoreService.getDocumentAs<StaffDoc>(
                Constants.Firestore.STAFF,
                "${schoolCode}_$teacherId"
            )
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
     * Start a Ready trip through the CANONICAL server lifecycle (D-8).
     *
     * This used to write `tripLogs.status` directly to Firestore, which
     * bypassed `Transport_service::startTrip()` and therefore skipped driver
     * readiness re-validation, the CAS transition guard, the audit row, the
     * "bus started" parent notification, vehicle route denormalisation, and
     * GPS session creation. One lifecycle now serves both the panel and the
     * app; the Firestore rule no longer permits a client status write at all.
     *
     * The server rejects a trip that is not assigned to the authenticated
     * driver, so no driver id is sent. A 422 carries a real business reason
     * (unfit driver, suspended route, wrong state) that the UI must show
     * verbatim rather than replace with a generic failure.
     */
    suspend fun startMyTrip(tripId: String): Result<Unit> = runCatching {
        val res = apiService.startTrip(tripId)
        if (!res.isSuccessful) throw IllegalStateException(res.serverMessage("Could not start the trip."))
        Unit
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
            ) + ClientMetadata.required()   // required(), NOT asMap() — see below.
            // The tripLogs update rule pins the writable set with
            // `affectedKeys().hasOnly([...])`, and asMap()'s two diagnostic
            // extras — device_sdk / device_model — are in NEITHER the pre-D-8
            // allowlist nor the D-8 one. Sending them makes Firestore reject the
            // whole update with permission-denied, so checkpoint marking fails
            // for every driver. This is the same trap ClientMetadata.required()
            // was introduced for on vehicleLivePositions; tripLogs is the only
            // other hasOnly-guarded write in the ruleset and had been missed.
            //
            // The creates elsewhere in this file (transportAttendance, fuelLogs,
            // sosAlerts) keep asMap(): their rules require metadata presence but
            // do NOT constrain the key set, so the extras remain useful there.
            firestoreService.updateDocument(Constants.Firestore.TRIP_LOGS, tripId, update)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── F14 QR attendance — server-authorized (2026-08-16) ─────────────
    //
    // WHAT WAS HERE BEFORE, AND WHY IT HAD TO GO.
    // `markStudentBoarded/Dropped/NoShow` wrote straight into
    // `transportAttendance` from the device. That bypassed
    // Transport_qr_attendance_service entirely, so a boarding was recorded with
    // NO eligibility check (studentRoutes status, effective dates, session,
    // service days), NO state-transition validation, NO wrong-bus detection and
    // NO QR signature — the device simply asserted `marked_by_role: "driver"`
    // about itself.
    //
    // The Firestore rule has since WITHDRAWN the driver client-write arm for
    // exactly that reason, and its own comment carries the warning: deployed
    // ahead of this app change, "the current app's manual tap-to-board starts
    // failing PERMISSION_DENIED — and, being a Firestore write, it would fail
    // silently in the UI." This is that app change.
    //
    // Boarding now requires the SIGNED QR token, deliberately. A conductor
    // cannot hand-board a child, because that is the hole F14 exists to close;
    // a child without a scannable card is handled through
    // [resolveStudentException], which records the real outcome (absent, took
    // another bus, verified some other way) instead of a fake boarding.

    /**
     * Board a student by scanning their ID-card QR.
     *
     * The token is passed through untouched — decoding, signature verification
     * and eligibility all happen server-side. [clientEventId] makes a retry
     * idempotent: the same tap twice records one event.
     *
     * @return the server's own outcome so the UI can distinguish a fresh
     *         boarding from a duplicate scan or a needs-confirmation case.
     */
    suspend fun scanStudentQr(
        tripId: String,
        rawQr: String,
        confirmAlternate: Boolean = false,
        clientEventId: String = UUID.randomUUID().toString(),
    ): Result<ScanOutcome> = runCatching {
        val res = apiService.scanStudentQr(
            tripId = tripId,
            qr = rawQr,
            confirmAlternate = if (confirmAlternate) "1" else "",
            clientEventId = clientEventId,
            clientEventAt = isoNow(),
        )
        val body = res.body().orEmpty()
        val status = (body["status"] as? String).orEmpty()
        // A 422 with status=needs_confirmation is NOT a failure — it is the
        // wrong-bus / alternate-transport prompt, and the conductor has to see
        // it. Only a genuine error throws.
        if (status == "error" || (!res.isSuccessful && status.isBlank())) {
            throw IllegalStateException(res.serverMessage("Could not record the scan."))
        }
        ScanOutcome(
            status      = status,
            message     = (body["message"] as? String).orEmpty(),
            studentId   = (body["student_id"] as? String).orEmpty(),
            studentName = (body["student_name"] as? String).orEmpty(),
        )
    }

    /**
     * Everyone still aboard has reached school.
     *
     * The bulk alighting the service already models — geofence-verified server
     * side, so it cannot be used to close a trip while the bus is elsewhere.
     */
    suspend fun markAllAlightedAtSchool(
        tripId: String,
        clientEventId: String = UUID.randomUUID().toString(),
    ): Result<Unit> = runCatching {
        val res = apiService.markAllAlightedAtSchool(tripId, clientEventId)
        if (!res.isSuccessful) throw IllegalStateException(
            res.serverMessage("Could not record arrival at school.")
        )
        Unit
    }

    /**
     * Record why a rostered student did not board.
     *
     * `resolution` is the service's own vocabulary — absent, alternate_bus,
     * verified_other. These are what clear a completion blocker; the conductor
     * is accounting for a child, not inventing a boarding.
     */
    suspend fun resolveStudentException(
        tripId: String,
        studentId: String,
        resolution: String,
        reason: String = "",
        clientEventId: String = UUID.randomUUID().toString(),
    ): Result<Unit> = runCatching {
        val res = apiService.resolveException(tripId, studentId, resolution, reason, clientEventId)
        if (!res.isSuccessful) throw IllegalStateException(
            res.serverMessage("Could not record the resolution.")
        )
        Unit
    }

    /**
     * The live roster: who is expected, who is aboard, and what still blocks
     * completion.
     *
     * Counts are computed server-side by the one implementation the admin panel
     * also reads, so the number on the conductor's phone and the number in the
     * office cannot drift.
     */
    suspend fun tripRoster(tripId: String): Result<TripRoster> = runCatching {
        val res = apiService.tripRoster(tripId)
        if (!res.isSuccessful) throw IllegalStateException(
            res.serverMessage("Could not load the student list.")
        )
        TripRoster.from(res.body().orEmpty())
    }

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            .format(java.util.Date())

    /**
     * Complete a trip through the CANONICAL server lifecycle (D-8).
     *
     * Was a direct Firestore write; see [startMyTrip] for why that had to go.
     * Completion additionally disarms the F13 tracking kill switch and tears
     * down the live-position session, so a parent can no longer see the bus
     * once the children are off it — that teardown only happens on this path.
     *
     * A 422 is a real guard (for example a checkpoint still Pending) and its
     * message must reach the driver unchanged.
     */
    suspend fun completeMyTrip(
        tripId: String,
        endOdometer: Double,
        distance: Double,
        fuelUsed: Double,
        notes: String = ""
    ): Result<Unit> = runCatching {
        val res = apiService.completeTrip(tripId, endOdometer, distance, fuelUsed, notes)
        if (!res.isSuccessful) throw IllegalStateException(res.serverMessage("Could not complete the trip."))
        Unit
    }

    /**
     * Pull the server's own `message` out of a non-2xx response so the driver
     * sees the actual reason — "Driver licence expired", "Route is suspended",
     * "This trip is not assigned to you" — instead of a generic failure. These
     * messages are the entire point of routing through the canonical service.
     */
    private fun Response<Map<String, Any>>.serverMessage(fallback: String): String {
        val fromBody = (body()?.get("message") as? String)?.takeIf { it.isNotBlank() }
        if (fromBody != null) return fromBody
        val raw = try { errorBody()?.string() } catch (_: Exception) { null }
        val fromError = raw
            ?.let { Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.getOrNull(1) }
            ?.takeIf { it.isNotBlank() }
        return fromError ?: "$fallback (HTTP ${code()})"
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
