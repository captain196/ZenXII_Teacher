package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

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
    // F9 (2026-07-07) — severity picker (Low/Medium/High/Critical); default
    // 'High' per operator Refinement 1 (bias toward operational safety —
    // driver may explicitly lower). Priority routing lives in the PHP
    // Transport_notifier::SOS_SEVERITY_TO_PRIORITY matrix.
    val severity: String = "High",
    val message: String = "",
    val status: String = "active",         // active, responded, resolved
    val respondedBy: String = "",
    val resolvedAt: Any? = null,
    val notifiedParents: Int = 0,
    val createdAt: Any? = null
)
