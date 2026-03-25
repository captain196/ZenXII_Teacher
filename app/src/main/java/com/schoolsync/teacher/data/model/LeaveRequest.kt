package com.schoolsync.teacher.data.model

import com.google.firebase.database.ServerValue

/**
 * Leave request model for teachers.
 * Path: Schools/{schoolCode}/HR/Staff_Leave/Records/{teacherId}/
 */
data class LeaveRequest(
    val leaveId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val leaveType: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val reason: String = "",
    val status: LeaveStatus = LeaveStatus.PENDING,
    val appliedOn: Long = 0L,
    val approvedBy: String = "",
    val approvedOn: Long = 0L,
    val remarks: String = "",
    val numberOfDays: Int = 0
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(leaveId = "")

    fun toFirebaseMap(): Map<String, Any> {
        return mapOf(
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "leaveType" to leaveType,
            "startDate" to startDate,
            "endDate" to endDate,
            "reason" to reason,
            "status" to status.value,
            "appliedOn" to ServerValue.TIMESTAMP,
            "numberOfDays" to numberOfDays
        )
    }
}

enum class LeaveStatus(val value: String, val label: String) {
    PENDING("pending", "Pending"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun fromValue(value: String?): LeaveStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}

/**
 * Leave balance summary per type.
 */
data class LeaveBalance(
    val leaveType: String = "",
    val total: Int = 0,
    val used: Int = 0,
    val remaining: Int = 0
) {
    constructor() : this(leaveType = "")
}
