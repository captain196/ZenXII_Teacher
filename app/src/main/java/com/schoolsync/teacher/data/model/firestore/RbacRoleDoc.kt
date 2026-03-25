package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class RbacRoleDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val roleName: String = "",
    val modules: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val maxUsers: Int = 0
)
