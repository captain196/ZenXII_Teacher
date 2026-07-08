package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * VehicleDoc.
 *
 * F9 (2026-07-07) — schema alignment (Refinement 4). The PHP server-side
 * canonical driver field is `staff_id`; the legacy Kotlin `driverId`
 * field was mislabeled and never matched actual writes. New `staffId`
 * (@PropertyName mapping to `staff_id`) is the driver identity that
 * `TransportFirestoreRepository.getMyAssignedVehicle()` filters on.
 * `driverId` retained for backwards-compat with any existing UI reader
 * — writers no longer populate it.
 */
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

    // Legacy (kept for existing readers; F9 writers do not populate this)
    val driverId: String = "",

    // F9 canonical driver reference — mapped to server-side `staff_id`
    @get:com.google.firebase.firestore.PropertyName("staff_id")
    @set:com.google.firebase.firestore.PropertyName("staff_id")
    var staffId: String = "",

    val driverName: String = "",
    val driverPhone: String = "",
    val insuranceExpiry: String = "",
    val pucExpiry: String = "",
    val fitnessExpiry: String = "",
    val permitExpiry: String = "",
    val gpsDeviceId: String = "",
    val status: String = "active",
    val updatedAt: Any? = null
)
