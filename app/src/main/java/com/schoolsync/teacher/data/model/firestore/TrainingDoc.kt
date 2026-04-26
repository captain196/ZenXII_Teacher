package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class TrainingDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val description: String = "",
    val trainer: String = "",
    val date: String = "",
    val duration: String = "",
    val venue: String = "",
    val maxParticipants: Int = 0,
    val registeredCount: Int = 0,
    val category: String = "",
    val status: String = "upcoming",       // upcoming, ongoing, completed, cancelled
    val createdAt: Any? = null
)
