package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.AdmissionApplicationDoc
import com.schoolsync.teacher.data.model.firestore.AdmissionConfigDoc
import com.schoolsync.teacher.data.model.firestore.AdmissionMeritListDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for admission-related features from the teacher side (view-only).
 * Teachers can view admission configuration, applications per class, and merit lists.
 *
 * Collections used:
 * - admissionConfig: school-level admission settings
 * - admissionApplications: individual admission applications
 * - admissionMeritLists: generated merit lists per class
 */
@Singleton
class AdmissionFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the admission configuration for the current school.
     * Document ID: {schoolId}_{session}
     */
    suspend fun getAdmissionConfig(): Result<AdmissionConfigDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val docId = "${schoolCode}_${session}"
            val doc = firestoreService.getDocumentAs<AdmissionConfigDoc>(
                Constants.Firestore.ADMISSION_CONFIG,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch admission applications for a specific class.
     * Query: schoolId + applyingForClass, ordered by submission date.
     */
    suspend fun getApplications(classKey: String): Result<List<AdmissionApplicationDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val applications = firestoreService.queryDocumentsAs<AdmissionApplicationDoc>(
                Constants.Firestore.ADMISSION_APPLICATIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("applyingForClass", classKey)
                    .orderBy("submittedAt", Query.Direction.DESCENDING)
            }
            Result.success(applications)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the merit list for a specific class.
     * Document ID pattern: {schoolId}_{session}_{classKey}
     */
    suspend fun getMeritList(classKey: String): Result<AdmissionMeritListDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val docId = "${schoolCode}_${session}_${classKey}"
            val doc = firestoreService.getDocumentAs<AdmissionMeritListDoc>(
                Constants.Firestore.ADMISSION_MERIT_LISTS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
