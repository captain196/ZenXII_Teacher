package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class VendorDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val gstin: String = "",
    val rating: Double = 0.0,
    val status: String = "active"          // active, inactive, blacklisted
)
