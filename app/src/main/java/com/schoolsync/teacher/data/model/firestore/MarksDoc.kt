package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a single student's marks for one subject in one exam.
 *
 * Collection: `marks`
 * Doc ID: `{schoolId}_{examId}_{sectionKey}_{subject}_{studentId}`
 */
data class MarksDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examId: String = "",
    val sectionKey: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val theory: Double = 0.0,
    val practical: Double = 0.0,
    val total: Double = 0.0,
    val absent: Boolean = false,
    val savedBy: String = "",
    @ServerTimestamp
    val savedAt: Timestamp? = null,
    val status: String = "draft"         // "draft", "submitted", "verified", "locked"
)
