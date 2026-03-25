package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SurveyResponseDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val surveyId: String = "",
    val userId: String = "",
    val answers: Map<String, String> = emptyMap(),
    @ServerTimestamp
    val submittedAt: Timestamp? = null
)
