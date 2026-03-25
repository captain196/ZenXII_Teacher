package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class VehicleDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val registrationNo: String = "",
    val type: String = "bus",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val capacity: Int = 0,
    val color: String = "",
    val currentRouteId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val insuranceExpiry: String = "",
    val pucExpiry: String = "",
    val fitnessExpiry: String = "",
    val permitExpiry: String = "",
    val gpsDeviceId: String = "",
    val status: String = "active",
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
