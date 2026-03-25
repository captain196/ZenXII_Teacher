package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SubmissionDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val homeworkId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val sectionKey: String = "",
    val status: String = "pending",  // "pending", "submitted", "reviewed", "incomplete"
    val remark: String = "",
    @ServerTimestamp
    val submittedAt: Timestamp? = null,
    val reviewedBy: String = "",
    @ServerTimestamp
    val reviewedAt: Timestamp? = null,
    val files: List<String> = emptyList(),
    val text: String = "",
    val score: Int = -1,             // -1 = not graded
    val maxMarks: Int = 0
)
