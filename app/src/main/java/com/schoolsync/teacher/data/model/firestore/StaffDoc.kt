package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class StaffDoc(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",
    val schoolId: String = "",
    val department: String = "",
    val position: String = "",
    val qualification: String = "",
    val subjects: List<String> = emptyList(),
    val classesAssigned: List<String> = emptyList(),
    val gender: String = "",
    val dob: String = "",
    val joiningDate: String = "",
    val profilePic: String = "",
    val status: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
