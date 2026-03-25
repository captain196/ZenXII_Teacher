package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.AssetDoc
import com.schoolsync.teacher.data.model.firestore.InventoryDoc
import com.schoolsync.teacher.data.model.firestore.POItemDoc
import com.schoolsync.teacher.data.model.firestore.SurveyDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for inventory and asset management from the teacher side.
 * Includes surveys, events, asset tracking, inventory, and purchase requests.
 *
 * Collections used:
 * - surveys: school surveys
 * - surveyResponses: individual survey responses
 * - events: school events
 * - assets: school asset register
 * - inventory: consumable inventory items
 * - purchaseRequests: purchase order requests from staff
 */
@Singleton
class InventoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Surveys ─────────────────────────────────────────────────────────────

    /**
     * Fetch active surveys for the current school.
     */
    suspend fun getSurveys(): Result<List<SurveyDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val surveys = firestoreService.queryDocumentsAs<SurveyDoc>(
                Constants.Firestore.SURVEYS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "active")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(surveys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a survey response. Document ID: {surveyId}_{userId} for idempotent writes.
     */
    suspend fun submitSurveyResponse(
        surveyId: String,
        answers: Map<String, String>
    ): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        val docId = "${surveyId}_${teacherId}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "surveyId" to surveyId,
            "respondentId" to teacherId,
            "respondentName" to teacherName,
            "respondentRole" to "teacher",
            "answers" to answers,
            "submittedAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.SURVEY_RESPONSES,
                docId,
                data
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch school events from Firestore events collection.
     * Returns raw maps as events may have flexible schemas.
     */
    suspend fun getEvents(): Result<List<Map<String, Any?>>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val snapshot = firestoreService.queryDocuments(
                Constants.Firestore.EVENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("eventDate", Query.Direction.ASCENDING)
                    .limit(50)
            }
            val events = snapshot.documents.mapNotNull { doc ->
                doc.data?.plus("id" to doc.id)
            }
            Result.success(events)
        } catch (e: Exception) {
            // Collection may not exist yet
            Result.success(emptyList())
        }
    }

    // ── Assets ──────────────────────────────────────────────────────────────

    /**
     * Fetch all tracked assets for the current school.
     * Teacher-only: for viewing school asset register.
     */
    suspend fun getAssets(): Result<List<AssetDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val assets = firestoreService.queryDocumentsAs<AssetDoc>(
                Constants.Firestore.ASSETS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("assetName")
            }
            Result.success(assets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Inventory ───────────────────────────────────────────────────────────

    /**
     * Fetch consumable inventory items for the current school.
     * Teacher-only: for viewing stock levels and requesting supplies.
     */
    suspend fun getInventory(): Result<List<InventoryDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val items = firestoreService.queryDocumentsAs<InventoryDoc>(
                Constants.Firestore.INVENTORY
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("itemName")
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Purchase Requests ───────────────────────────────────────────────────

    /**
     * Create a purchase request for inventory items.
     * Returns the generated purchase request document ID.
     *
     * Teacher-only operation.
     *
     * @param items list of line items with quantity, description, estimated cost
     * @param vendorId preferred vendor ID (if known)
     */
    suspend fun createPurchaseRequest(
        items: List<POItemDoc>,
        vendorId: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        val docId = "${schoolCode}_PO_${System.currentTimeMillis()}"
        val itemsMaps = items.map { item ->
            mapOf(
                "description" to item.description,
                "qty" to item.qty,
                "unitPrice" to item.unitPrice,
                "total" to item.total
            )
        }

        val data = mapOf(
            "schoolId" to schoolCode,
            "items" to itemsMaps,
            "vendorId" to vendorId,
            "requestedBy" to teacherId,
            "requestedByName" to teacherName,
            "status" to "pending",
            "grandTotal" to items.sumOf { it.total },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.PURCHASE_REQUESTS,
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
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }
}
