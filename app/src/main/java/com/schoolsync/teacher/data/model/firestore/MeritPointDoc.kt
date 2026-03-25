package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class MeritPointDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val date: String = "",
    val type: String = "merit",            // merit, demerit
    val points: Int = 0,
    val runningTotal: Int = 0,
    val category: String = "",
    val reason: String = "",
    val incidentId: String = "",
    val awardedBy: String = "",
    val awardedByName: String = ""
)
