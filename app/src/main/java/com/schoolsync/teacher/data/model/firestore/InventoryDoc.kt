package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class InventoryDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val category: String = "",
    val unit: String = "",
    val currentStock: Int = 0,
    val minStock: Int = 0,
    val lastRestocked: String = "",
    val location: String = "",
    val status: String = "in_stock"        // in_stock, low_stock, out_of_stock
)
