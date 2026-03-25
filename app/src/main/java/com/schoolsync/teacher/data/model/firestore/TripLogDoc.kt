package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class TripLogDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val vehicleId: String = "",
    val date: String = "",
    val tripType: String = "morning",      // morning, afternoon
    val startTime: String = "",
    val endTime: String = "",
    val startOdometer: Double = 0.0,
    val endOdometer: Double = 0.0,
    val distance: Double = 0.0,
    val fuelUsed: Double = 0.0,
    val stopsCompleted: Int = 0,
    val alerts: List<String> = emptyList()
)
