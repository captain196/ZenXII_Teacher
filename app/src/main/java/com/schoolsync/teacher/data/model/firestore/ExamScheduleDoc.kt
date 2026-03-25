package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents the exam schedule for a specific class-section.
 *
 * Collection: `examSchedule`
 * Doc ID: `{schoolId}_{examId}_{className}_{section}`
 */
data class ExamScheduleDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examId: String = "",
    val className: String = "",
    val section: String = "",
    val subjects: List<ExamSubjectScheduleDoc> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null
)

/**
 * Per-subject schedule entry embedded within [ExamScheduleDoc].
 */
data class ExamSubjectScheduleDoc(
    val subjectName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val maxTheory: Double = 0.0,
    val maxPractical: Double = 0.0,
    val maxTotal: Double = 0.0,
    val room: String = ""
)
