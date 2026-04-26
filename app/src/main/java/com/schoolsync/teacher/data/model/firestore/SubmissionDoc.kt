package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

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
    val submittedAt: Any? = null,
    val reviewedBy: String = "",
    val reviewedAt: Any? = null,
    val files: List<String> = emptyList(),
    val text: String = "",
    val score: Int = -1,             // -1 = not graded
    val maxMarks: Int = 0
)
