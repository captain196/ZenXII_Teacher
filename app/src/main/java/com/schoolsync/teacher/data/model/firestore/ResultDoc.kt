package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a computed result for one student in one exam.
 *
 * Collection: `results`
 * Doc ID: `{schoolId}_{examId}_{sectionKey}_{studentId}`
 */
data class ResultDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examId: String = "",
    val examName: String = "",
    val sectionKey: String = "",
    val className: String = "",
    val section: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val subjects: Map<String, SubjectResultDoc> = emptyMap(),
    val totalMarks: Double = 0.0,
    val maxMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "",
    val rank: Int = 0,
    val passFail: String = "",           // "Pass" or "Fail"
    @ServerTimestamp
    val computedAt: Timestamp? = null
)

/**
 * Per-subject result entry embedded within [ResultDoc.subjects].
 */
data class SubjectResultDoc(
    val total: Double = 0.0,
    val maxMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "",
    val absent: Boolean = false
)
