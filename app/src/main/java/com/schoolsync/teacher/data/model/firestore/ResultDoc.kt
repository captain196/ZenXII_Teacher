package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

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
    // NOTE: admin (Exam_result_store::buildResultDoc) writes totalMarks/percentage
    // as null for a fully-absent student. Firebase maps a null number onto the
    // Double default (0.0) here — acceptable for display, but a future result
    // screen that must distinguish "absent" from "scored 0" should read [absent].
    val totalMarks: Double = 0.0,
    val maxMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "",
    val rank: Int = 0,
    val passFail: String = "",           // "Pass" or "Fail"
    val absent: Boolean = false,         // overall absent (admin buildResultDoc)
    val absentSubjects: List<String> = emptyList(),
    val computedAt: Any? = null
)

/**
 * Per-subject result entry embedded within [ResultDoc.subjects].
 * Mirrors admin `buildResultDoc` subjects[]: {total, maxMarks, percentage, grade,
 * passFail, absent}.
 */
data class SubjectResultDoc(
    val total: Double = 0.0,
    val maxMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "",
    val passFail: String = "",
    val absent: Boolean = false
)
