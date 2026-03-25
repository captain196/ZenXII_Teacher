package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class GeoFenceDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val type: String = "school_zone",
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val radius: Int = 200,
    val onEnterNotify: List<String> = emptyList(),
    val onExitNotify: List<String> = emptyList(),
    val onEnterMessage: String = "",
    val onExitMessage: String = "",
    val active: Boolean = true
)
