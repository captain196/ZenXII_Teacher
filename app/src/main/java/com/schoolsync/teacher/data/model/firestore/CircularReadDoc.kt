package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class CircularReadDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val circularId: String = "",
    val userId: String = "",
    val userName: String = "",
    val role: String = "",
    val readAt: Any? = null,
    val acknowledged: Boolean = false
)
