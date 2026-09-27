package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

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
    val createdAt: Any? = null
)

/**
 * Per-subject schedule entry embedded within [ExamScheduleDoc].
 */
data class ExamSubjectScheduleDoc(
    val subjectName: String = "",
    // Phase 0.2B (G-09 propagation): canonical subjects.subjectCode, resolved
    // server-side in Exam.php from the subject master at schedule-save time —
    // never trusted from any client. Empty string on schedules saved before
    // this field existed, or if the subject name no longer resolves against
    // the current subject master (e.g. renamed/removed) — callers must treat
    // empty the same as absent, never fall back to subjectName for
    // authorization purposes.
    val subjectCode: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val maxTheory: Double = 0.0,
    val maxPractical: Double = 0.0,
    val maxTotal: Double = 0.0,
    val room: String = ""
)
