package com.schoolsync.teacher.data.model

/**
 * Leave request model for teachers.
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
    /** No-arg constructor retained for callers that build an empty instance. */
    constructor() : this(leaveId = "")
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
