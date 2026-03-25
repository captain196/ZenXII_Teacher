package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents the fee structure for a specific class and section within a session.
 *
 * Collection: `feeStructures`
 * Doc ID: `{schoolId}_{session}_{className}_{section}`
 */
data class FeeStructureDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val feeHeads: List<FeeHeadDoc> = emptyList(),
    val totalMonthlyFee: Double = 0.0,
    val totalAnnualFee: Double = 0.0,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

/**
 * Individual fee head entry embedded within [FeeStructureDoc.feeHeads].
 */
data class FeeHeadDoc(
    val name: String = "",
    val amount: Double = 0.0,
    val frequency: String = "monthly"  // "monthly", "quarterly", "annual", "once"
)
