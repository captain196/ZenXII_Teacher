package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class RouteDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val routeNo: String = "",
    val name: String = "",
    val type: String = "pickup",           // pickup, drop
    val vehicleId: String = "",
    val driverId: String = "",
    val attendantId: String = "",
    val stops: List<RouteStopDoc> = emptyList(),
    val totalStudents: Int = 0,
    val totalDistance: Double = 0.0,
    val estimatedDuration: Int = 0,
    val active: Boolean = true,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

data class RouteStopDoc(
    val stopId: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val time: String = "",
    val order: Int = 0
)
