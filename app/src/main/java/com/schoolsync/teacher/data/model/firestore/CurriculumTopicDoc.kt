package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Curriculum topic — lives in the subcollection
 *   curriculum/{parentDocId}/topics/{topicId}
 * where parentDocId = "{schoolId}_{session}_{classSectionSlug}_{subject}".
 *
 * Used by the lesson planner edit sheet to populate the topic dropdown.
 * Read-only from the teacher app — admins manage topic CRUD.
 */
data class CurriculumTopicDoc(
    @DocumentId
    val id: String = "",
    val topicId: String = "",
    val parentDocId: String = "",
    val title: String = "",
    val chapter: String = "",
    val estPeriods: Int = 0,
    val status: String = "not_started",
    val sortOrder: Int = 0
)

/**
 * Curriculum parent doc — used to enumerate topicIds[] before fetching
 * each topic. Mirrors what `Curriculum_service::getCurriculum` returns.
 */
data class CurriculumDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val classSection: String = "",
    val subject: String = "",
    val topicsModel: String = "array",        // "array" | "subcollection"
    val topicIds: List<String> = emptyList(),
    val version: Long = 0
)
