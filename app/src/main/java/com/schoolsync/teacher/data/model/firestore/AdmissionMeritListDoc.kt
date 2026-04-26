package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class AdmissionMeritListDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val classKey: String = "",
    val rankedApplicants: List<RankedApplicantDoc> = emptyList(),
    val computedAt: Any? = null
)

data class RankedApplicantDoc(
    val rank: Int = 0,
    val appId: String = "",
    val name: String = "",
    val score: Double = 0.0,
    val status: String = "pending"
)
