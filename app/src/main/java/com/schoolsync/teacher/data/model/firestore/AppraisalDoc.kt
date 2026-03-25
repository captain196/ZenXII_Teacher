package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class AppraisalDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val department: String = "",
    val kras: List<KraDoc> = emptyList(),
    val overallSelfRating: Double = 0.0,
    val overallManagerRating: Double = 0.0,
    val overallRating: Double = 0.0,
    val status: String = "draft",          // draft, self_submitted, finalized
    @ServerTimestamp
    val selfSubmittedAt: Timestamp? = null,
    @ServerTimestamp
    val finalizedAt: Timestamp? = null
)

data class KraDoc(
    val id: String = "",
    val area: String = "",
    val weight: Double = 0.0,
    val target: String = "",
    val selfScore: Double = 0.0,
    val managerScore: Double = 0.0
)
