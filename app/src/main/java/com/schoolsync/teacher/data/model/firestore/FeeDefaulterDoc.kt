package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a fee defaulter record for a student with outstanding dues.
 *
 * Collection: `feeDefaulters`
 * Doc ID: `{schoolId}_{session}_{studentId}`
 */
data class FeeDefaulterDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val totalDues: Double = 0.0,
    val unpaidMonths: List<String> = emptyList(),
    val overdueMonths: List<String> = emptyList(),
    val examBlocked: Boolean = false,
    val resultWithheld: Boolean = false,
    val lastPaymentDate: String = "",
    val flaggedAt: String = ""
)
