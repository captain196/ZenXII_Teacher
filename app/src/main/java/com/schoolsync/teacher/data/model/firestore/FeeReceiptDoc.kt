package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a fee payment receipt for a student.
 *
 * Collection: `feeReceipts`
 * Doc ID: auto-generated or receipt number.
 */
data class FeeReceiptDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val receiptNo: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val amount: Double = 0.0,
    val paymentMode: String = "",  // "Cash", "Online", "Cheque"
    val feeMonths: List<String> = emptyList(),
    val feeBreakdown: Map<String, Double> = emptyMap(),
    val remarks: String = "",
    val collectedBy: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
