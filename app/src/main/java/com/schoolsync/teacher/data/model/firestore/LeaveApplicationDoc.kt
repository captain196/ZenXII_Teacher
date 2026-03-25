package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class LeaveApplicationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val applicantType: String = "",  // "staff" or "student"
    val applicantId: String = "",
    val applicantName: String = "",
    val sectionKey: String = "",     // for students
    val leaveType: String = "",      // "SL", "CL", "EL", "ML", "LWP"
    val startDate: String = "",
    val endDate: String = "",
    val numberOfDays: Int = 0,
    val reason: String = "",
    val attachments: List<String> = emptyList(),
    val status: String = "pending",  // "pending", "approved", "rejected", "cancelled"
    @ServerTimestamp
    val appliedAt: Timestamp? = null,
    val approvedBy: String = "",
    @ServerTimestamp
    val approvedAt: Timestamp? = null,
    val remarks: String = ""
)
