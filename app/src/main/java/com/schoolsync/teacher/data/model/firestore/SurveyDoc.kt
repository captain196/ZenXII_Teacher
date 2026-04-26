package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SurveyDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val description: String = "",
    val targetRoles: List<String> = emptyList(),
    val targetClasses: List<String> = emptyList(),
    val questions: List<SurveyQuestionDoc> = emptyList(),
    val anonymous: Boolean = false,
    val deadline: String = "",
    val totalResponses: Int = 0,
    val status: String = "draft",          // draft, active, closed
    val createdAt: Any? = null
)

data class SurveyQuestionDoc(
    val id: String = "",
    val type: String = "text",             // text, mcq, rating, checkbox
    val text: String = "",
    val scale: Int = 5,
    val options: List<String> = emptyList(),
    val maxLength: Int = 500
)
