package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class ParentDoc(
    @DocumentId
    val id: String = "",
    val parentDbKey: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val schoolId: String = "",
    val childrenIds: List<String> = emptyList(),
    val profilePic: String = "",
    val status: String = "",
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
