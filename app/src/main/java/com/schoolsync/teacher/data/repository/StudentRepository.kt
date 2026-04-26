package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.model.firestore.StudentDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Student roster repository — Phase 2: Firestore-first with RTDB fallback.
 *
 * PRIMARY:  Firestore → students collection WHERE schoolId + className + section + status
 * FALLBACK: RTDB → Schools/{school}/{session}/{class}/{section}/Students/List
 *
 * The fallback will be removed in Phase 3 once Firestore reads are validated in production.
 */
@Singleton
class StudentRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "StudentRepository"
    }

    /**
     * Get all active students for a class/section.
     * Tries Firestore first, falls back to RTDB roster on failure.
     */
    suspend fun getStudentsForClass(
        className: String,
        section: String
    ): Result<List<StudentInfo>> {
        // Try Firestore first
        val firestoreResult = getStudentsFromFirestore(className, section)
        if (firestoreResult.isSuccess) {
            val students = firestoreResult.getOrDefault(emptyList())
            if (students.isNotEmpty()) {
                Log.d(TAG, "Firestore: ${students.size} students for $className/$section")
                return firestoreResult
            }
            // Empty Firestore result — could mean no students OR data not synced yet
            // Fall through to RTDB to check
            Log.d(TAG, "Firestore empty for $className/$section, checking RTDB fallback")
        } else {
            Log.w(TAG, "Firestore query failed for $className/$section, using RTDB fallback",
                firestoreResult.exceptionOrNull())
        }

        // Fallback to RTDB roster
        val rtdbResult = getStudentsFromRtdb(className, section)
        if (rtdbResult.isSuccess) {
            val rtdbStudents = rtdbResult.getOrDefault(emptyList())
            Log.d(TAG, "RTDB fallback: ${rtdbStudents.size} students for $className/$section")
        }
        return rtdbResult
    }

    /**
     * PRIMARY: Firestore query for active students in a class/section.
     * Uses indexed fields: schoolId + className + section + status.
     */
    private suspend fun getStudentsFromFirestore(
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
                    .whereEqualTo("status", "Active")
            }

            val students = docs.map { doc ->
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

            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FALLBACK: RTDB roster read.
     * Path: Schools/{schoolCode}/{session}/{class}/{section}/Students/List
     *
     * Will be removed in Phase 3.
     */
    private suspend fun getStudentsFromRtdb(
        className: String,
        section: String
    ): Result<List<StudentInfo>> {
        return try {
            // schoolCode now stores schoolId — used for Schools/{schoolId}/... paths
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            // parentDbKey is the login code — used for Users/Parents/... paths
            val parentDbKey = tokenManager.parentDbKey.firstOrNull() ?: schoolCode
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))

            val enrollmentPath =
                "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/${Constants.classKey(className)}/${Constants.sectionKey(section)}/${Constants.Firebase.STUDENT_LIST}"
            val snapshot = firebaseService.readSnapshot(enrollmentPath)

            val students = mutableListOf<StudentInfo>()
            for (child in snapshot.children) {
                val studentId = child.key ?: continue

                // Handle both string ("Name") and object ({Name, Roll_no}) formats
                var listName = ""
                var listRollNo = ""
                try {
                    listName = child.getValue(String::class.java) ?: ""
                } catch (_: Exception) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val map = child.value as? Map<String, Any?>
                        if (map != null) {
                            listName = (map["Name"] ?: map["name"] ?: "").toString()
                            listRollNo = (map["Roll_no"] ?: map["roll_no"] ?: map["rollNo"] ?: "").toString()
                        }
                    } catch (_: Exception) { }
                }

                // Try full profile for more details — use parentDbKey (login code),
                // NOT schoolCode (schoolId), since student profiles live at
                // Users/Parents/{parentDbKey}/{studentId}.
                try {
                    val profilePath = "${Constants.Firebase.USERS_PARENTS}/$parentDbKey/$studentId"
                    val info = firebaseService.readValue<StudentInfo>(profilePath)
                    if (info != null) {
                        students.add(info.copy(
                            studentId = studentId,
                            name = info.name.ifBlank { listName },
                            rollNo = info.rollNo.ifBlank { listRollNo },
                            className = className,
                            section = section
                        ))
                    } else {
                        students.add(StudentInfo(
                            studentId = studentId, name = listName,
                            rollNo = listRollNo, className = className, section = section
                        ))
                    }
                } catch (_: Exception) {
                    students.add(StudentInfo(
                        studentId = studentId, name = listName,
                        rollNo = listRollNo, className = className, section = section
                    ))
                }
            }

            Result.success(students.sortedBy { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
