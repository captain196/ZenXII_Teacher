package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.RouteDoc
import com.schoolsync.teacher.data.model.firestore.SosAlertDoc
import com.schoolsync.teacher.data.model.firestore.StudentRouteDoc
import com.schoolsync.teacher.data.model.firestore.VehicleDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for transport-related data from the teacher side.
 * Supports reading routes, vehicles, live GPS tracking, SOS alerts, and triggering SOS.
 *
 * Collections used (Firestore):
 * - studentRoutes: per-student route assignment
 * - routes: route definitions with stops
 * - vehicles: vehicle metadata
 * - sosAlerts: emergency alerts from transport staff
 *
 * RTDB paths used:
 * - /VehicleLive/{schoolId}/{vehicleId}: real-time GPS location of vehicles
 * - /SOSAlerts/{schoolId}/{alertId}: instant SOS delivery for real-time listeners
 */
@Singleton
class TransportFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val firebaseService: FirebaseService,
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
     * Observe real-time GPS location of a vehicle from RTDB.
     * Path: /VehicleLive/{schoolId}/{vehicleId}
     * Emits a Map containing lat, lng, speed, heading, updatedAt, etc.
     * Emits null when no live data is available.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVehicleLive(vehicleId: String): Flow<Map<String, Any?>?> {
        return tokenManager.schoolCode
            .map { it?.takeIf { code -> code.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    val path = "VehicleLive/$schoolCode/$vehicleId"
                    firebaseService.listen(path).map { snapshot ->
                        @Suppress("UNCHECKED_CAST")
                        if (snapshot.exists()) {
                            snapshot.value as? Map<String, Any?>
                        } else {
                            null
                        }
                    }
                }
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
     * Creates a Firestore document for persistence and writes to RTDB for instant delivery.
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
            // Write to Firestore for persistence
            firestoreService.setDocument(
                Constants.Firestore.SOS_ALERTS,
                docId,
                data
            )

            // Write to RTDB for instant real-time delivery
            val rtdbData = mapOf(
                "schoolId" to schoolCode,
                "vehicleId" to vehicleId,
                "routeId" to routeId,
                "lat" to lat,
                "lng" to lng,
                "message" to message,
                "triggeredBy" to teacherId,
                "triggeredByName" to teacherName,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            )
            firebaseService.setValue("SOSAlerts/$schoolCode/$docId", rtdbData)

            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }
}
