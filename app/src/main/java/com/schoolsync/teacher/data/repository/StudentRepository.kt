package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads student rosters from Firebase.
 *
 * Student list path: Schools/{schoolCode}/{session}/{class}/{section}/Students/
 *   -> Map<studentId, Boolean>
 * Student profile: Users/Parents/{parentDbKey}/{studentId}/
 */
@Singleton
class StudentRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Get all students for a given class/section.
     * 1. Read student ID list from class enrollment node.
     * 2. For each student ID, read their profile.
     */
    suspend fun getStudentsForClass(
        className: String,
        section: String
    ): Result<List<StudentInfo>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))

            // Read from Students/List — admin panel stores {studentId: "Name"} here
            val enrollmentPath =
                "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/${Constants.classKey(className)}/${Constants.sectionKey(section)}/${Constants.Firebase.STUDENT_LIST}"
            val snapshot = firebaseService.readSnapshot(enrollmentPath)

            val students = mutableListOf<StudentInfo>()
            for (child in snapshot.children) {
                val studentId = child.key ?: continue

                // Handle both formats:
                // 1. String: "Yuvraj Singh" (old format)
                // 2. Object: {Name: "Yuvraj Singh", Roll_no: "1", Gender: "Male"} (new format)
                var listName = ""
                var listRollNo = ""
                try {
                    // Try as string first
                    listName = child.getValue(String::class.java) ?: ""
                } catch (_: Exception) {
                    // It's an object — read fields
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val map = child.value as? Map<String, Any?>
                        if (map != null) {
                            listName = (map["Name"] ?: map["name"] ?: "").toString()
                            listRollNo = (map["Roll_no"] ?: map["roll_no"] ?: map["rollNo"] ?: "").toString()
                        }
                    } catch (_: Exception) { }
                }

                // Try to read full student profile for more details
                try {
                    val profilePath =
                        "${Constants.Firebase.USERS_PARENTS}/$schoolCode/$studentId"
                    val info = firebaseService.readValue<StudentInfo>(profilePath)
                    if (info != null) {
                        students.add(
                            info.copy(
                                studentId = studentId,
                                name = info.name.ifBlank { listName },
                                rollNo = info.rollNo.ifBlank { listRollNo },
                                className = className,
                                section = section
                            )
                        )
                    } else {
                        students.add(
                            StudentInfo(
                                studentId = studentId,
                                name = listName,
                                rollNo = listRollNo,
                                className = className,
                                section = section
                            )
                        )
                    }
                } catch (_: Exception) {
                    students.add(
                        StudentInfo(
                            studentId = studentId,
                            name = listName,
                            rollNo = listRollNo,
                            className = className,
                            section = section
                        )
                    )
                }
            }

            Result.success(students.sortedBy { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
