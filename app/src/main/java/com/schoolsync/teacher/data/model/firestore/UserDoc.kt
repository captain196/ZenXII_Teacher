package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class UserDoc(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val schoolId: String? = "",
    val profilePic: String = "",
    val status: String = "",
    val lastLoginAt: Timestamp? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
