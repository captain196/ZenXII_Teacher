package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class CircularReadDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val circularId: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String = "",
    @ServerTimestamp
    val readAt: Timestamp? = null,
    val acknowledged: Boolean = false
)
