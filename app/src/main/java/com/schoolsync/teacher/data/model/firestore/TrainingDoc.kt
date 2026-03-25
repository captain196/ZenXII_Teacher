package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

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
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
