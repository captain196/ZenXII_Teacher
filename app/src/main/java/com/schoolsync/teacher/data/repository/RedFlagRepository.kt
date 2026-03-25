package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.StudentFlag
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the Student Red Flag system.
 *
 * Flag path: StudentFlags/{schoolCode}/{studentId}/{flagId}
 */
@Singleton
class RedFlagRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Build the flags base path for a student.
     */
    private fun flagsBasePath(schoolCode: String, studentId: String): String {
        return "${Constants.Firebase.STUDENT_FLAGS}/$schoolCode/$studentId"
    }

    /**
     * Create a new flag for a student. Returns the generated flagId.
     */
    suspend fun createFlag(studentId: String, flag: StudentFlag): Result<String> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = flagsBasePath(schoolCode, studentId)
            val flagId = firebaseService.pushValue(path, flag.toMap())
            Result.success(flagId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve an existing flag (update status to "resolved").
     */
    suspend fun resolveFlag(studentId: String, flagId: String): Result<Unit> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull() ?: ""

            val path = "${flagsBasePath(schoolCode, studentId)}/$flagId"
            val updates = mapOf<String, Any?>(
                "status" to "resolved",
                "resolvedAt" to System.currentTimeMillis(),
                "resolvedBy" to teacherId
            )
            firebaseService.updateChildren(path, updates)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all flags for a specific student.
     */
    suspend fun getFlagsForStudent(studentId: String): Result<List<StudentFlag>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = flagsBasePath(schoolCode, studentId)
            val snapshot = firebaseService.readSnapshot(path)

            val flags = mutableListOf<StudentFlag>()
            for (child in snapshot.children) {
                val flagId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                flags.add(StudentFlag.fromMap(flagId, data))
            }

            Result.success(flags.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get flags for all students in a class. Returns a map of studentId to flags.
     */
    suspend fun getFlagsForClass(
        students: List<StudentInfo>
    ): Result<Map<String, List<StudentFlag>>> {
        return try {
            val result = mutableMapOf<String, List<StudentFlag>>()
            for (student in students) {
                val flagsResult = getFlagsForStudent(student.studentId)
                val flags = flagsResult.getOrNull() ?: emptyList()
                if (flags.isNotEmpty()) {
                    result[student.studentId] = flags
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe flags for a student in real-time.
     */
    fun observeFlagsForStudent(studentId: String): Flow<List<StudentFlag>> = flow {
        val schoolCode = tokenManager.schoolCode.firstOrNull() ?: return@flow

        val path = flagsBasePath(schoolCode, studentId)
        firebaseService.listen(path).collect { snapshot ->
            val flags = mutableListOf<StudentFlag>()
            for (child in snapshot.children) {
                val flagId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                flags.add(StudentFlag.fromMap(flagId, data))
            }
            emit(flags.sortedByDescending { it.timestamp })
        }
    }

    /**
     * Count active flags for a given student.
     */
    suspend fun getActiveFlagCount(studentId: String): Int {
        return try {
            val result = getFlagsForStudent(studentId)
            result.getOrNull()?.count { it.status == "active" } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Count total active flags across all students.
     * Used on the dashboard for quick stats.
     */
    suspend fun getTotalActiveFlagCount(students: List<StudentInfo>): Int {
        return try {
            var count = 0
            for (student in students) {
                count += getActiveFlagCount(student.studentId)
            }
            count
        } catch (_: Exception) {
            0
        }
    }
}
