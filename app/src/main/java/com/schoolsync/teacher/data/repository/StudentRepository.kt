package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.model.firestore.StudentDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Student roster repository — Firestore-only.
 *
 * Source: Firestore `students` collection, queried by
 * `schoolId == schoolCode AND className == ck AND section == sk`,
 * with a case-insensitive `status` filter applied client-side.
 *
 * The previous RTDB fallback (`Schools/{school}/{session}/{class}/{section}/Students/List`)
 * was removed per the project's absolute NO-RTDB policy (P0c migration). Every
 * student that admin's Sis writes lands in Firestore via Entity_firestore_sync,
 * so there is no scenario where a real student exists in the RTDB roster but
 * not in the Firestore students collection. An empty Firestore result is the
 * truth — a class with no Active students, not a sync gap.
 *
 * Phase 2A (2026-05-09) — case-insensitive status filter. The previous
 * server-side `status == "Active"` filter dropped any student row whose
 * status was written as `"active"` (lowercase) — a real-world data
 * inconsistency observed at SCH_D94FE8F7AD where 9 of 12 active
 * students had lowercase status. The class-fees screen for those
 * sections rendered as empty rosters even though the students were
 * legitimately enrolled. Mirrors the admin-side `_activeStudentIds`
 * fix: load the per-section roster and filter on a normalized
 * (lowercase) status with an explicit exclusion list, so any
 * non-excluded value (Active / active / blank / unrecognized) keeps
 * the row visible. Inactive states explicitly excluded: TC, Withdrawn,
 * Inactive, Suspended.
 */
@Singleton
class StudentRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "StudentRepository"

        /**
         * Statuses that mark a student as no longer Active in the school.
         * Matched case-insensitively. Anything not in this set (including
         * "Active", "active", and blank) keeps the student visible.
         */
        private val INACTIVE_STATUSES = setOf(
            "tc", "withdrawn", "inactive", "suspended",
            // BUG-001 (2026-08-17): "deleted" was missing here too. Observed on
            // the admin panel — a student carrying status="Deleted" was offered
            // in the create-flag roster. Same gap existed on both surfaces.
            // Keep byte-identical to Red_flags.php INACTIVE_STUDENT_STATUSES.
            //
            // ONLY "deleted" is added. A production census of all 150 student
            // docs found exactly two statuses in use — "Active" (148) and
            // "Deleted" (2) — so anything else would be invented. This set is
            // read by 7 modules (attendance, homework, fees, dashboard,
            // students, red flags); widening it on speculation would silently
            // remove students from rosters across all of them.
            "deleted"
        )
    }

    /**
     * Get all active students for a class/section from Firestore.
     * Indexed query on schoolId + className + section; status filter
     * applied client-side for case-insensitivity (see class docblock).
     */
    suspend fun getStudentsForClass(
        className: String,
        section: String
    ): Result<List<StudentInfo>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val ck = Constants.classKey(className)
            val sk = Constants.sectionKey(section)

            val docs = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("className", ck)
                    .whereEqualTo("section", sk)
                // status filter applied client-side; see class docblock
            }

            val active = docs.filter { doc ->
                val st = doc.status.lowercase().trim()
                st.isEmpty() || st !in INACTIVE_STATUSES
            }

            val students = active.map { doc ->
                // Resolve the canonical student userId. The doc id is
                // `{schoolId}_{userId}` — if the userId field happens to
                // be blank, strip the prefix so we always end up with the
                // bare userId (the same value the parent app uses to
                // query its own flags). Otherwise teacher-side writes
                // would land with a prefixed studentId that the parent
                // listener couldn't match.
                val resolvedStudentId = when {
                    doc.userId.isNotBlank() -> doc.userId
                    doc.studentId.isNotBlank() -> doc.studentId
                    schoolCode.isNotBlank() && doc.id.startsWith("${schoolCode}_") ->
                        doc.id.removePrefix("${schoolCode}_")
                    else -> doc.id
                }
                StudentInfo(
                    studentId = resolvedStudentId,
                    name = doc.name,
                    fatherName = doc.fatherName,
                    motherName = doc.motherName,
                    rollNo = doc.rollNo,
                    className = doc.className,
                    section = doc.section,
                    gender = doc.gender,
                    dob = doc.dob,
                    profilePic = doc.profilePic,
                    parentDbKey = doc.parentDbKey,
                    phone = doc.phone,
                    admissionDate = doc.admissionDate,
                    email = doc.email
                )
            }.sortedBy { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE }

            Log.d(TAG, "Firestore: ${students.size} students for $className/$section")
            Result.success(students)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore query failed for $className/$section", e)
            Result.failure(e)
        }
    }
}
