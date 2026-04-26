package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SchoolDoc(
    @DocumentId
    val schoolId: String = "",
    val name: String = "",
    val schoolCode: String = "",
    val city: String = "",
    val email: String = "",
    val phone: String = "",
    val logoUrl: String = "",
    val status: String = "",
    val currentSession: String = "",
    val subscription: SubscriptionInfo = SubscriptionInfo(),
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)

data class SubscriptionInfo(
    val plan: String = "",
    val status: String = "",
    val expiryDate: String = ""
)
