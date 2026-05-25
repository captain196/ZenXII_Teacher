package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class SchoolDoc(
    @DocumentId
    val schoolId: String = "",
    val name: String = "",
    val schoolCode: String = "",
    val city: String = "",
    val email: String = "",
    val phone: String = "",
    val logoUrl: String = "",
    val status: String = "",
    val currentSession: String = "",
    val subscription: SubscriptionInfo = SubscriptionInfo(),
    /**
     * 2026-05-15 Phase 1 — board configuration propagation.
     *
     * Admin writes `board_config` (snake_case) to the school doc; this field
     * mirrors that payload so consumers (e.g. MarksScreen) can validate
     * marks input against the configured grading pattern instead of
     * hard-coding marks-out-of-100. Nullable + default `null` for backward
     * compatibility with older school docs that never had board_config
     * written. Deserialised via @PropertyName because the Firestore key
     * is snake_case and would otherwise collide with Kotlin's camelCase
     * field-mapping default.
     */
    @get:PropertyName("board_config")
    @set:PropertyName("board_config")
    var board: BoardConfig? = null,
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)

data class SubscriptionInfo(
    val plan: String = "",
    val status: String = "",
    val expiryDate: String = ""
)

/**
 * Mirror of Admin `board_config` map written by School_config.php save_board.
 * Every field nullable / defaulted so partial server-side configurations
 * deserialise cleanly without throwing.
 */
data class BoardConfig(
    val type: String = "",
    @get:PropertyName("grading_pattern")
    @set:PropertyName("grading_pattern")
    var gradingPattern: String = "",
    @get:PropertyName("grade_scale")
    @set:PropertyName("grade_scale")
    var gradeScale: List<GradeScaleEntry> = emptyList(),
    @get:PropertyName("passing_marks")
    @set:PropertyName("passing_marks")
    var passingMarks: Int = 0
)

data class GradeScaleEntry(
    val grade: String = "",
    @get:PropertyName("min_pct")
    @set:PropertyName("min_pct")
    var minPct: Double = 0.0,
    @get:PropertyName("max_pct")
    @set:PropertyName("max_pct")
    var maxPct: Double = 0.0
)
