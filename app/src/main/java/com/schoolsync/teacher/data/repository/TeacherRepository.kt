package com.schoolsync.teacher.data.repository

import android.util.Log
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.User
import com.schoolsync.teacher.data.model.firestore.SubjectAssignmentDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Teacher profile, assigned classes, and timetable operations.
 */
@Singleton
class TeacherRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "TeacherRepo"
    }
    /**
     * Fetch teacher profile from Firebase.
     * Path: Users/Teachers/{schoolCode}/{teacherId}/
     */
    suspend fun getTeacherProfile(): Result<User> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.TEACHERS}/$schoolCode/$teacherId"
            val user = firebaseService.readValue<User>(path)
                ?: return Result.failure(Exception("Teacher profile not found"))

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listen to teacher profile changes in real-time.
     */
    fun observeTeacherProfile(): Flow<User?> {
        return kotlinx.coroutines.flow.flow {
            val schoolCode = tokenManager.schoolCode.firstOrNull() ?: return@flow
            val teacherId = tokenManager.userId.firstOrNull() ?: return@flow
            val path = "${Constants.Firebase.TEACHERS}/$schoolCode/$teacherId"

            firebaseService.listen(path).collect { snapshot ->
                emit(snapshot.getValue(User::class.java))
            }
        }
    }

    /**
     * Fetch classes assigned to this teacher.
     * Phase 5: Reads from Firestore subjectAssignments collection (single source of truth),
     *          falls back to legacy RTDB Academic/Subject_Assignments if Firestore is empty.
     */
    suspend fun getAssignedClasses(): Result<List<ClassAssignment>> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull()
                ?: return Result.failure(Exception("School ID not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            // ── 1. Try Firestore subjectAssignments first ──
            try {
                // Query by schoolId + teacherId (schoolId required by security rules)
                // Filter session client-side to avoid 3-field composite index
                val allDocs = firestoreService.queryDocumentsAs<SubjectAssignmentDoc>(
                    Constants.Firestore.SUBJECT_ASSIGNMENTS
                ) { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("teacherId", teacherId)
                }
                // Drop archived rows — admin's deactivation cascade flips
                // archived=true on every assignment owned by an Inactive
                // staff. Without this filter a deactivated-then-reactivated
                // teacher with manually-archived legacy rows still sees them.
                val docs = allDocs.filter { it.session == session && !it.archived }

                if (docs.isNotEmpty()) {
                    // Expand each Firestore doc into one or more ClassAssignment
                    // entries. A doc with section="" is "class-wide" and means
                    // the teacher is allocated to *all* sections of that class —
                    // we resolve those by reading the sections collection so the
                    // rest of the app code (dashboard, students, attendance) can
                    // treat every assignment as a concrete (class, section) pair.
                    val assignments = mutableListOf<ClassAssignment>()
                    for (doc in docs) {
                        val ck = Constants.classKey(doc.className)
                        if (doc.section.isNotBlank()) {
                            assignments.add(
                                ClassAssignment(
                                    assignmentId = doc.id,
                                    teacherId = doc.teacherId,
                                    teacherName = doc.teacherName,
                                    className = ck,
                                    section = Constants.sectionKey(doc.section),
                                    subject = doc.subjectName.ifBlank { doc.subjectCode },
                                    classTeacher = doc.isClassTeacher
                                )
                            )
                        } else {
                            // Class-wide → expand across every section that exists
                            // for this class in the sections collection.
                            val sectionLetters = try {
                                // Query by schoolId + className (schoolId required by security rules)
                                firestoreService.queryDocumentsAs<com.schoolsync.teacher.data.model.firestore.SectionDoc>(
                                    Constants.Firestore.SECTIONS
                                ) { ref ->
                                    ref.whereEqualTo("schoolId", schoolId)
                                        .whereEqualTo("className", ck)
                                }.filter { it.session == session }
                                .map { it.section }
                            } catch (e: Exception) {
                                Log.w(TAG, "class-wide expand: sections query failed for $ck", e)
                                emptyList()
                            }
                            if (sectionLetters.isEmpty()) {
                                // Fall back to a single placeholder so the user still
                                // sees the class even if no sections exist yet.
                                assignments.add(
                                    ClassAssignment(
                                        assignmentId = doc.id,
                                        teacherId = doc.teacherId,
                                        teacherName = doc.teacherName,
                                        className = ck,
                                        section = "",
                                        subject = doc.subjectName.ifBlank { doc.subjectCode },
                                        classTeacher = doc.isClassTeacher
                                    )
                                )
                            } else {
                                for (sec in sectionLetters) {
                                    assignments.add(
                                        ClassAssignment(
                                            assignmentId = "${doc.id}_$sec",
                                            teacherId = doc.teacherId,
                                            teacherName = doc.teacherName,
                                            className = ck,
                                            section = Constants.sectionKey(sec),
                                            subject = doc.subjectName.ifBlank { doc.subjectCode },
                                            classTeacher = doc.isClassTeacher
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Log.d(TAG, "getAssignedClasses: Loaded ${assignments.size} from Firestore (after class-wide expand)")
                    return Result.success(assignments)
                }
                Log.d(TAG, "getAssignedClasses: Firestore empty, falling back to RTDB")
            } catch (e: Exception) {
                Log.w(TAG, "getAssignedClasses: Firestore failed, falling back to RTDB", e)
            }

            // ── 2. RTDB fallback (legacy path) ──
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/${Constants.Firebase.SUBJECT_ASSIGNMENTS}"
            val snapshot = firebaseService.readSnapshot(path)

            val assignments = mutableListOf<ClassAssignment>()
            for (classChild in snapshot.children) {
                val classKey = classChild.key ?: continue
                for (subjectChild in classChild.children) {
                    val subjectCode = subjectChild.key ?: continue
                    val data = subjectChild.value as? Map<*, *> ?: continue
                    val tId = (data["teacher_id"] as? String) ?: ""
                    if (tId != teacherId) continue
                    assignments.add(
                        ClassAssignment(
                            assignmentId = "${classKey}_${subjectCode}",
                            teacherId = tId,
                            teacherName = (data["teacher_name"] as? String) ?: "",
                            className = Constants.classKey(classKey),
                            section = "",  // RTDB legacy is class-wide
                            subject = (data["name"] as? String) ?: subjectCode,
                            classTeacher = false
                        )
                    )
                }
            }
            Log.d(TAG, "getAssignedClasses: Loaded ${assignments.size} from RTDB fallback")
            Result.success(assignments)
        } catch (e: Exception) {
            Log.e(TAG, "getAssignedClasses failed", e)
            Result.failure(e)
        }
    }

    /**
     * Listen to assigned classes in real-time.
     * Phase 5: Uses Firestore subjectAssignments listener.
     */
    fun observeAssignedClasses(): Flow<List<ClassAssignment>> {
        return kotlinx.coroutines.flow.flow {
            val schoolId = tokenManager.schoolId.firstOrNull() ?: return@flow
            val session = tokenManager.session.firstOrNull() ?: return@flow
            val teacherId = tokenManager.userId.firstOrNull() ?: return@flow

            // Query by schoolId + teacherId; filter session client-side (mirrors
            // getAssignedClasses — avoids a 3-field composite index).
            firestoreService.observeQuery(Constants.Firestore.SUBJECT_ASSIGNMENTS) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("teacherId", teacherId)
            }.collect { snapshot ->
                val assignments = mutableListOf<ClassAssignment>()
                for (docSnap in snapshot.documents) {
                    val obj = docSnap.toObject(SubjectAssignmentDoc::class.java) ?: continue
                    if (obj.session != session || obj.archived) continue
                    val ck = Constants.classKey(obj.className)

                    if (obj.section.isNotBlank()) {
                        assignments.add(
                            ClassAssignment(
                                assignmentId = docSnap.id,
                                teacherId = obj.teacherId,
                                teacherName = obj.teacherName,
                                className = ck,
                                section = Constants.sectionKey(obj.section),
                                subject = obj.subjectName.ifBlank { obj.subjectCode },
                                classTeacher = obj.isClassTeacher
                            )
                        )
                    } else {
                        // Class-wide (section="") → expand across every section
                        // of the class, same as the one-shot getAssignedClasses.
                        val sectionLetters = try {
                            firestoreService.queryDocumentsAs<com.schoolsync.teacher.data.model.firestore.SectionDoc>(
                                Constants.Firestore.SECTIONS
                            ) { ref ->
                                ref.whereEqualTo("schoolId", schoolId)
                                    .whereEqualTo("className", ck)
                            }.filter { it.session == session }.map { it.section }
                        } catch (e: Exception) {
                            emptyList()
                        }
                        if (sectionLetters.isEmpty()) {
                            assignments.add(
                                ClassAssignment(
                                    assignmentId = docSnap.id,
                                    teacherId = obj.teacherId,
                                    teacherName = obj.teacherName,
                                    className = ck,
                                    section = "",
                                    subject = obj.subjectName.ifBlank { obj.subjectCode },
                                    classTeacher = obj.isClassTeacher
                                )
                            )
                        } else {
                            for (sec in sectionLetters) {
                                assignments.add(
                                    ClassAssignment(
                                        assignmentId = "${docSnap.id}_$sec",
                                        teacherId = obj.teacherId,
                                        teacherName = obj.teacherName,
                                        className = ck,
                                        section = Constants.sectionKey(sec),
                                        subject = obj.subjectName.ifBlank { obj.subjectCode },
                                        classTeacher = obj.isClassTeacher
                                    )
                                )
                            }
                        }
                    }
                }
                emit(assignments)
            }
        }
    }

    /**
     * (removed) RTDB Time_table reads — superseded by TimetableFirestoreRepository
     * in Phase C-1 (2026-04-25). Callers go through TimetableFirestoreRepository.
     */

}
