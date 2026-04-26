package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class PurchaseOrderDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val poNumber: String = "",
    val vendorId: String = "",
    val vendorName: String = "",
    val items: List<POItemDoc> = emptyList(),
    val subtotal: Double = 0.0,
    val gst: Double = 0.0,
    val grandTotal: Double = 0.0,
    val status: String = "draft",          // draft, approved, ordered, received, cancelled
    val requestedBy: String = "",
    val approvedBy: String = "",
    val paymentStatus: String = "pending", // pending, partial, paid
    val createdAt: Any? = null
)

data class POItemDoc(
    val description: String = "",
    val qty: Int = 0,
    val unitPrice: Double = 0.0,
    val total: Double = 0.0
)
