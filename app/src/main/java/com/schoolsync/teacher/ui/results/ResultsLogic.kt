package com.schoolsync.teacher.ui.results

import com.schoolsync.teacher.data.model.firestore.ResultDoc
import com.schoolsync.teacher.data.model.firestore.SubjectResultDoc
import com.schoolsync.teacher.ui.marks.formatMark

/**
 * Pure, Android/Firebase-free results-display logic, extracted so the AB-aware
 * pass/fail rendering decision is unit-testable (RESIDUAL-6). Rendered cells are
 * derived here; the composable only draws them.
 */

/** Authoritative outcome for a result row/subject — NEVER a hardcoded threshold. */
enum class PassState { PASS, FAIL, ABSENT, NONE }

data class ResultDisplayCells(
    val totalText: String,   // "AB" or "45 / 100"
    val percentText: String, // "AB" or "45%"
    val gradeText: String,   // "—" when blank/absent, else the grade
    val passState: PassState,
    val passLabel: String    // "AB" / "Pass" / "Fail" / "—"
)

/**
 * Absent detection is authoritative and takes precedence over passFail: the admin
 * (Exam_result_store::buildResultDoc) writes null total/percentage for a fully
 * absent student, which Firebase maps onto 0.0 in [ResultDoc], so [absent] is the
 * only reliable signal at runtime. We also treat an explicit null total/percentage
 * as absent so the branch is testable hermetically. When present, the outcome is
 * driven ONLY by the stored [passFail] string — never by comparing a percentage
 * to a guessed pass mark.
 */
fun resolvePassState(passFail: String, absent: Boolean, percentage: Double?, total: Double?): PassState {
    if (absent || percentage == null || total == null) return PassState.ABSENT
    return when (passFail.trim().lowercase()) {
        "pass" -> PassState.PASS
        "fail" -> PassState.FAIL
        else -> PassState.NONE
    }
}

fun buildResultCells(
    passFail: String,
    absent: Boolean,
    percentage: Double?,
    total: Double?,
    maxMarks: Double,
    grade: String
): ResultDisplayCells {
    val state = resolvePassState(passFail, absent, percentage, total)
    val isAb = state == PassState.ABSENT
    return ResultDisplayCells(
        totalText = if (isAb) "AB" else "${formatMark(total ?: 0.0)} / ${formatMark(maxMarks)}",
        percentText = if (isAb) "AB" else "${formatMark(percentage ?: 0.0)}%",
        gradeText = if (isAb || grade.isBlank()) "—" else grade,
        passState = state,
        passLabel = when (state) {
            PassState.ABSENT -> "AB"
            PassState.PASS -> "Pass"
            PassState.FAIL -> "Fail"
            PassState.NONE -> "—"
        }
    )
}

/** Cells for an overall student result. */
fun cellsFor(doc: ResultDoc): ResultDisplayCells =
    buildResultCells(doc.passFail, doc.absent, doc.percentage, doc.totalMarks, doc.maxMarks, doc.grade)

/** Cells for a single subject within a result. */
fun cellsFor(sub: SubjectResultDoc): ResultDisplayCells =
    buildResultCells(sub.passFail, sub.absent, sub.percentage, sub.total, sub.maxMarks, sub.grade)
