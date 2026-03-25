package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class StudentDoc(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val schoolId: String = "",
    val className: String = "",
    val section: String = "",
    val rollNo: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val dob: String = "",
    val gender: String = "",
    val admissionDate: String = "",
    val parentDbKey: String = "",
    val profilePic: String = "",
    val status: String = "",
    val session: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)
