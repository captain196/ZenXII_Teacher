package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class HomeworkDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val sectionKey: String = "",     // "9th_A"
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val dueDate: String = "",        // "2026-03-28"
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val status: String = "active",   // "active", "closed"
    val submissionCount: Int = 0,
    val totalStudents: Int = 0,
    val attachments: List<String> = emptyList()
)
