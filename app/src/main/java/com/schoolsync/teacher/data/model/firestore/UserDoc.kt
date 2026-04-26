package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

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
    val lastLoginAt: Any? = null,
    val createdAt: Any? = null
)
