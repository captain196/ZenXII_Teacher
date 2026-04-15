package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.AppraisalDoc
import com.schoolsync.teacher.data.model.firestore.KraDoc
import com.schoolsync.teacher.data.model.firestore.RecruitmentDoc
import com.schoolsync.teacher.data.model.firestore.SalarySlipDoc
import com.schoolsync.teacher.data.model.firestore.TrainingDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for teacher-side HR self-service: salary slips, appraisals, training, and recruitment.
 *
 * Collections used:
 * - salarySlips: monthly salary slip documents per staff member
 * - appraisals: annual performance appraisals per staff member
 * - trainingSessions: school-wide training/workshop events
 * - trainingRegistrations: individual registration for training sessions
 * - recruitmentOpenings: open job positions at the school
 */
@Singleton
class HRFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Salary ──────────────────────────────────────────────────────────────

    /**
     * Fetch salary slips for the current teacher, ordered by most recent first.
     * Query: schoolId + staffId matches current teacher.
     */
    suspend fun getMySalarySlips(): Result<List<SalarySlipDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        return try {
            val slips = firestoreService.queryDocumentsAs<SalarySlipDoc>(
                Constants.Firestore.SALARY_SLIPS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("staffId", teacherId)
                    .whereEqualTo("type", "payslip")
                    .orderBy("monthKey", Query.Direction.DESCENDING)
            }
            Result.success(slips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Appraisals ──────────────────────────────────────────────────────────

    /**
     * Fetch the current appraisal document for the teacher.
     * Document ID pattern: {schoolId}_{session}_{staffId}
     */
    suspend fun getMyAppraisal(): Result<AppraisalDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        return try {
            val docId = "${schoolCode}_${session}_${teacherId}"
            val doc = firestoreService.getDocumentAs<AppraisalDoc>(
                Constants.Firestore.APPRAISALS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a self-review for an existing appraisal cycle.
     * Updates the appraisal document with the teacher's KRA self-assessments.
     */
    suspend fun submitSelfReview(
        appraisalId: String,
        kras: List<KraDoc>
    ): Result<Unit> {
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))

        val krasMaps = kras.map { kra ->
            mapOf(
                "id" to kra.id,
                "area" to kra.area,
                "weight" to kra.weight,
                "target" to kra.target,
                "selfScore" to kra.selfScore,
                "managerScore" to kra.managerScore
            )
        }

        return try {
            firestoreService.updateDocument(
                Constants.Firestore.APPRAISALS,
                appraisalId,
                mapOf(
                    "selfReview" to krasMaps,
                    "selfReviewSubmittedBy" to teacherId,
                    "selfReviewSubmittedAt" to FieldValue.serverTimestamp(),
                    "status" to "self_review_submitted"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Training ────────────────────────────────────────────────────────────

    /**
     * Fetch training sessions available for staff at the current school.
     */
    suspend fun getTrainingSessions(): Result<List<TrainingDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val sessions = firestoreService.queryDocumentsAs<TrainingDoc>(
                Constants.Firestore.TRAININGS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "published")
                    .orderBy("startDate", Query.Direction.DESCENDING)
            }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register the current teacher for a training session.
     * Document ID pattern: {trainingId}_{teacherId} for idempotent registration.
     */
    suspend fun registerForTraining(trainingId: String): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = getTeacherId()
            ?: return Result.failure(Exception("Teacher ID not available"))
        val teacherName = getTeacherName()

        val docId = "${trainingId}_${teacherId}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "trainingId" to trainingId,
            "staffId" to teacherId,
            "staffName" to teacherName,
            "status" to "registered",
            "registeredAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.TRAINING_REGISTRATIONS,
                docId,
                data
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Recruitment ─────────────────────────────────────────────────────────

    /**
     * Fetch open recruitment positions at the school.
     */
    suspend fun getRecruitmentOpenings(): Result<List<RecruitmentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val openings = firestoreService.queryDocumentsAs<RecruitmentDoc>(
                Constants.Firestore.RECRUITMENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "open")
                    .orderBy("postedAt", Query.Direction.DESCENDING)
            }
            Result.success(openings)
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

    private suspend fun getTeacherId(): String? {
        return tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getTeacherName(): String {
        return tokenManager.userName.firstOrNull() ?: ""
    }
}
