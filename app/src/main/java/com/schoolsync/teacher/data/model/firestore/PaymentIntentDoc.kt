package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a payment intent created when a parent initiates an online fee payment.
 *
 * Collection: `paymentIntents`
 * Doc ID: auto-generated.
 */
data class PaymentIntentDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val amount: Double = 0.0,
    val feeMonths: List<String> = emptyList(),
    val status: String = "requested",  // "requested", "pending", "completed", "failed"
    val gatewayOrderId: String = "",
    val gatewayPaymentId: String = "",
    val createdAt: Any? = null,
    val completedAt: Any? = null,
    val receiptId: String = ""
)
