package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a teacher story in Firestore.
 * Collection: `stories`
 * Stories expire after 24 hours.
 */
data class StoryDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val teacherPic: String = "",
    val mediaUrl: String = "",
    val type: String = "image",      // "image", "video"
    val caption: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val expiresAt: Long = 0,         // Unix millis (createdAt + 24h)
    val viewCount: Int = 0
)
