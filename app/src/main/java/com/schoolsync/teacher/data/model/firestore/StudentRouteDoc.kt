package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class StudentRouteDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val routeId: String = "",
    val routeName: String = "",
    val stopId: String = "",
    val stopName: String = "",
    val vehicleId: String = "",
    val vehicleNo: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val conductorName: String = "",
    val pickupTime: String = "",
    val dropTime: String = "",
    val monthlyFee: Double = 0.0
)
