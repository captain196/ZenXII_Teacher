package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.LeaveBalance
import com.schoolsync.teacher.data.model.LeaveRequest
import com.schoolsync.teacher.data.model.LeaveStatus
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Leave management for teachers.
 * Records path: Schools/{schoolCode}/HR/Staff_Leave/Records/{teacherId}/
 * Balance path: Schools/{schoolCode}/HR/Staff_Leave/Balance/{teacherId}/
 */
@Singleton
class LeaveRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    suspend fun getLeaveBalance(): Result<List<LeaveBalance>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.STAFF_LEAVE_BALANCE}/$teacherId"
            val snapshot = firebaseService.readSnapshot(path)

            val balances = mutableListOf<LeaveBalance>()
            for (child in snapshot.children) {
                val balance = child.getValue(LeaveBalance::class.java)
                if (balance != null) {
                    balances.add(balance.copy(leaveType = child.key ?: balance.leaveType))
                }
            }

            Result.success(balances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLeaveHistory(): Result<List<LeaveRequest>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.STAFF_LEAVE_RECORDS}/$teacherId"
            val snapshot = firebaseService.readSnapshot(path)

            val requests = mutableListOf<LeaveRequest>()
            for (child in snapshot.children) {
                val request = child.getValue(LeaveRequest::class.java)
                if (request != null) {
                    requests.add(request.copy(leaveId = child.key ?: ""))
                }
            }

            Result.success(requests.sortedByDescending { it.appliedOn })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitLeaveRequest(request: LeaveRequest): Result<Unit> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.STAFF_LEAVE_RECORDS}/$teacherId"
            firebaseService.pushValue(path, request.toFirebaseMap())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
