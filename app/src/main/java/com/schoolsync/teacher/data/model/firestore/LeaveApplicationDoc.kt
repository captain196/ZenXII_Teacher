package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class LeaveApplicationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val applicantType: String = "",  // "staff" or "student"
    val applicantId: String = "",
    val applicantName: String = "",
    val className: String = "",      // "Class 8th"
    val section: String = "",        // "Section A"
    val sectionKey: String = "",     // "Class 8th/Section A"
    val leaveType: String = "",      // "SL", "CL", "EL", "ML", "LWP"
    val startDate: String = "",
    val endDate: String = "",
    val numberOfDays: Double = 0.0,   // MEDIUM #5: Double so half-day = 0.5
    val reason: String = "",
    val attachments: List<String> = emptyList(),
    val status: String = "pending",  // "pending", "approved", "rejected", "cancelled"
    val appliedAt: Any? = null,
    val approvedBy: String = "",
    val approvedAt: Any? = null,
    val remarks: String = ""
)
