package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class RecruitmentDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val department: String = "",
    val roleKey: String = "",
    val qualification: String = "",
    val experience: String = "",
    val salaryRange: String = "",
    val vacancies: Int = 0,
    val jobDescription: String = "",
    val status: String = "open",           // open, closed, on_hold
    val postedDate: String = "",
    val closingDate: String = "",
    val totalApplications: Int = 0,
    val shortlisted: Int = 0,
    val selected: Int = 0,
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
