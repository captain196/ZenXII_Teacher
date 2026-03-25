package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class AdmissionConfigDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val status: String = "draft",          // draft, open, closed
    val openDate: String = "",
    val closeDate: String = "",
    val classesOpen: List<String> = emptyList(),
    val seatMatrix: Map<String, SeatInfoDoc> = emptyMap(),
    val applicationFee: Double = 0.0,
    val requiredDocuments: List<String> = emptyList(),
    val stages: List<String> = emptyList(),
    val selectionCriteria: String = ""
)

data class SeatInfoDoc(
    val total: Int = 0,
    val filled: Int = 0,
    val waitlisted: Int = 0,
    val available: Int = 0
)
