package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * FuelLogDoc — Firestore fuel-log record, written from both the PHP
 * admin panel (F1) and the Teacher-app driver flow (F9 Refinement 3 —
 * driver-submitted fuel logs are restricted to the driver's currently
 * assigned vehicle; enforced by Firestore rule).
 *
 * Field-name mapping to PHP schema (snake_case on Firestore, camelCase
 * on this data class via @PropertyName):
 *   vehicleId         → vehicle_id
 *   litres            → litres
 *   pricePerLitre     → price_per_litre
 *   totalAmount       → total_amount
 *   odometer          → odometer_km
 *   station           → station
 *   notes             → notes
 *   loggedByStaffId   → logged_by_staff_id
 */
data class FuelLogDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",

    @get:PropertyName("vehicle_id") @set:PropertyName("vehicle_id")
    var vehicleId: String = "",

    val litres: Double = 0.0,

    @get:PropertyName("price_per_litre") @set:PropertyName("price_per_litre")
    var pricePerLitre: Double = 0.0,

    @get:PropertyName("total_amount") @set:PropertyName("total_amount")
    var totalAmount: Double = 0.0,

    @get:PropertyName("odometer_km") @set:PropertyName("odometer_km")
    var odometerKm: Double = 0.0,

    val station: String = "",
    val notes: String = "",

    @get:PropertyName("logged_by_staff_id") @set:PropertyName("logged_by_staff_id")
    var loggedByStaffId: String = "",

    @get:PropertyName("logged_by_role") @set:PropertyName("logged_by_role")
    var loggedByRole: String = "driver",

    @get:PropertyName("fuel_date") @set:PropertyName("fuel_date")
    var fuelDate: String = "",

    @get:PropertyName("created_at") @set:PropertyName("created_at")
    var createdAt: Any? = null
)
