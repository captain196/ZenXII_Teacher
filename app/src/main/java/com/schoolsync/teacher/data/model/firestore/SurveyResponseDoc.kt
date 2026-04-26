package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SurveyResponseDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val surveyId: String = "",
    val userId: String = "",
    val answers: Map<String, String> = emptyMap(),
    val submittedAt: Any? = null
)
