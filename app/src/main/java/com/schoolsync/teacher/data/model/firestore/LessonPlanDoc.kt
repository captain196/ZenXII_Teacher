package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Lesson plan document — Phase 6/7 Academic Planner.
 *
 * Collection: `lessonPlans`
 * Doc ID:     "{schoolId}_{session}_{teacherId}_{date}_P{periodIndex}"
 *             (composite key encodes the teacher×date×period uniqueness invariant)
 *
 * Mirrors the shape written by the admin PHP service `Lesson_plan_service`
 * so admin and teacher see identical data. Teacher app writes here directly
 * (matches the existing direct-Firestore pattern for homework/attendance),
 * preserving the Phase 6 doc shape including `version` for optimistic
 * concurrency.
 */
data class LessonPlanDoc(
    @DocumentId
    val id: String = "",

    val schoolId: String = "",
    val session: String = "",
    val planId: String = "",
    val version: Long = 0,

    // Class / subject / teacher snapshot
    val className: String = "",
    val section: String = "",
    val classSection: String = "",     // "Class 8th/Section A"
    val subject: String = "",
    val subjectCode: String = "",
    val teacherId: String = "",
    val teacherName: String = "",

    // When
    val date: String = "",             // "YYYY-MM-DD"
    val dayOfWeek: String = "",        // "Monday" — derived server-side, never trusted from client
    val periodIndex: Int = 0,          // 0-based
    val periodNumber: Int = 0,         // 1-based (for display)

    // Topic linkage (denormalised — title snapshot included so teachers
    // see the topic even if curriculum is renamed/deleted later)
    val topicId: String = "",
    val topicTitle: String = "",
    val curriculumDocId: String = "",

    // Mutable per-plan fields
    val notes: String = "",
    val status: String = "planned",    // planned | completed | skipped | rescheduled
    val rescheduledTo: String = "",    // "YYYY-MM-DD_P{N}" when status=rescheduled
    val completedAt: String = "",

    // Audit trail
    val createdAt: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val updatedAt: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = ""
)
