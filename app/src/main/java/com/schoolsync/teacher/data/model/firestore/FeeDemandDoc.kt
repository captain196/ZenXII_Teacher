package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a monthly fee demand raised against a student.
 *
 * Collection: `feeDemands`
 * Doc ID: auto-generated or `{schoolId}_{session}_{studentId}_{month}`
 */
data class FeeDemandDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val sectionKey: String = "",
    val month: String = "",
    val demandId: String = "",
    val feeItems: Map<String, Double> = emptyMap(),
    val grossAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val fineAmount: Double = 0.0,
    val netAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val status: String = "unpaid",  // "paid", "partial", "overdue", "unpaid"
    val createdAt: String = "",
    val updatedAt: String = ""
)
