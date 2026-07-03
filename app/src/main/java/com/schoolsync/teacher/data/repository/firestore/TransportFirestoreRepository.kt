package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.RouteDoc
import com.schoolsync.teacher.data.model.firestore.SosAlertDoc
import com.schoolsync.teacher.data.model.firestore.StudentRouteDoc
import com.schoolsync.teacher.data.model.firestore.VehicleDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
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
     * @return The generated alert document ID.
     */
    suspend fun triggerSos(
        vehicleId: String,
        routeId: String,
        lat: Double,
        lng: Double,
        message: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "vehicleId" to vehicleId,
            "routeId" to routeId,
            "lat" to lat,
            "lng" to lng,
            "message" to message,
            "triggeredBy" to teacherId,
            "triggeredByName" to teacherName,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.SOS_ALERTS,
                docId,
                data
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

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
