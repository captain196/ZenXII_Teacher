package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

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
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

data class SubscriptionInfo(
    val plan: String = "",
    val status: String = "",
    val expiryDate: String = ""
)
