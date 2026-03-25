package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SosAlertDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val triggeredBy: String = "",
    val triggeredByRole: String = "",
    val vehicleId: String = "",
    val routeId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val alertType: String = "emergency",
    val message: String = "",
    val status: String = "active",         // active, responded, resolved
    val respondedBy: String = "",
    @ServerTimestamp
    val resolvedAt: Timestamp? = null,
    val notifiedParents: Int = 0,
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
