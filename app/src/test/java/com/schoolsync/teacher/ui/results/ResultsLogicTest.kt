package com.schoolsync.teacher.ui.results

import com.schoolsync.teacher.data.model.firestore.ResultDoc
import com.schoolsync.teacher.data.model.firestore.SubjectResultDoc
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the pure results-display logic (ResultsLogic.kt). Verifies
 * the AB-aware pass/fail rendering decision: absent is authoritative and takes
 * precedence, pass/fail is driven ONLY by the stored passFail string (never a
 * hardcoded threshold), and grade/percentage/total render "AB"/"—" appropriately.
 * No Android/Firebase runtime is needed — the *Doc data classes are plain Kotlin.
 */
class ResultsLogicTest {

    // ── resolvePassState ─────────────────────────────────────────────────

    @Test
    fun resolvePassState_pass() {
        assertEquals(PassState.PASS, resolvePassState("Pass", absent = false, percentage = 82.0, total = 82.0))
    }

    @Test
    fun resolvePassState_fail() {
        assertEquals(PassState.FAIL, resolvePassState("Fail", absent = false, percentage = 20.0, total = 20.0))
    }

    @Test
    fun resolvePassState_caseInsensitive() {
        assertEquals(PassState.PASS, resolvePassState("pass", absent = false, percentage = 40.0, total = 40.0))
        assertEquals(PassState.FAIL, resolvePassState("FAIL", absent = false, percentage = 10.0, total = 10.0))
    }

    @Test
    fun resolvePassState_absentFlagTakesPrecedenceOverPassFail() {
        // Even with a stray "Pass" string, absent==true wins → ABSENT.
        assertEquals(PassState.ABSENT, resolvePassState("Pass", absent = true, percentage = 0.0, total = 0.0))
    }

    @Test
    fun resolvePassState_nullPercentageOrTotal_isAbsent() {
        assertEquals(PassState.ABSENT, resolvePassState("Fail", absent = false, percentage = null, total = 0.0))
        assertEquals(PassState.ABSENT, resolvePassState("Fail", absent = false, percentage = 0.0, total = null))
    }

    @Test
    fun resolvePassState_blankPassFail_isNone() {
        assertEquals(PassState.NONE, resolvePassState("", absent = false, percentage = 50.0, total = 50.0))
    }

    // ── buildResultCells: present student ────────────────────────────────

    @Test
    fun buildResultCells_present_pass_rendersMarksAndGrade() {
        val cells = buildResultCells(
            passFail = "Pass", absent = false, percentage = 85.0, total = 85.0, maxMarks = 100.0, grade = "A"
        )
        assertEquals("85 / 100", cells.totalText)
        assertEquals("85%", cells.percentText)
        assertEquals("A", cells.gradeText)
        assertEquals(PassState.PASS, cells.passState)
        assertEquals("Pass", cells.passLabel)
    }

    @Test
    fun buildResultCells_present_fractionalMarks_keepDecimals() {
        val cells = buildResultCells(
            passFail = "Pass", absent = false, percentage = 87.5, total = 43.75, maxMarks = 50.0, grade = "A+"
        )
        assertEquals("43.75 / 50", cells.totalText)
        assertEquals("87.5%", cells.percentText)
    }

    @Test
    fun buildResultCells_present_blankGrade_rendersDash() {
        val cells = buildResultCells(
            passFail = "Fail", absent = false, percentage = 30.0, total = 30.0, maxMarks = 100.0, grade = ""
        )
        assertEquals("—", cells.gradeText)
        assertEquals(PassState.FAIL, cells.passState)
        assertEquals("Fail", cells.passLabel)
    }

    // ── buildResultCells: absent student ─────────────────────────────────

    @Test
    fun buildResultCells_absent_rendersAbNotZeroFailed() {
        val cells = buildResultCells(
            passFail = "Fail", absent = true, percentage = 0.0, total = 0.0, maxMarks = 100.0, grade = "F"
        )
        assertEquals("AB", cells.totalText)
        assertEquals("AB", cells.percentText)
        assertEquals("—", cells.gradeText)
        assertEquals(PassState.ABSENT, cells.passState)
        assertEquals("AB", cells.passLabel)
    }

    @Test
    fun buildResultCells_nullTotal_treatedAsAbsent() {
        val cells = buildResultCells(
            passFail = "", absent = false, percentage = null, total = null, maxMarks = 100.0, grade = "A"
        )
        assertEquals("AB", cells.totalText)
        assertEquals(PassState.ABSENT, cells.passState)
    }

    // ── cellsFor(ResultDoc) / cellsFor(SubjectResultDoc) ─────────────────

    @Test
    fun cellsFor_resultDoc_present() {
        val doc = ResultDoc(
            studentName = "Asha", passFail = "Pass", absent = false,
            percentage = 90.0, totalMarks = 450.0, maxMarks = 500.0, grade = "A+"
        )
        val cells = cellsFor(doc)
        assertEquals("450 / 500", cells.totalText)
        assertEquals("90%", cells.percentText)
        assertEquals("A+", cells.gradeText)
        assertEquals("Pass", cells.passLabel)
    }

    @Test
    fun cellsFor_resultDoc_absentFlag() {
        // Admin maps a fully-absent student's null percentage/total onto 0.0, so
        // the absent flag is the only reliable signal — it must still render AB.
        val doc = ResultDoc(
            studentName = "Ben", passFail = "Fail", absent = true,
            percentage = 0.0, totalMarks = 0.0, maxMarks = 500.0, grade = ""
        )
        val cells = cellsFor(doc)
        assertEquals("AB", cells.totalText)
        assertEquals("AB", cells.passLabel)
    }

    @Test
    fun cellsFor_subjectResultDoc_present() {
        val sub = SubjectResultDoc(
            total = 72.0, maxMarks = 80.0, percentage = 90.0, grade = "A", passFail = "Pass", absent = false
        )
        val cells = cellsFor(sub)
        assertEquals("72 / 80", cells.totalText)
        assertEquals("A", cells.gradeText)
        assertEquals("Pass", cells.passLabel)
    }

    @Test
    fun cellsFor_subjectResultDoc_absent() {
        val sub = SubjectResultDoc(
            total = 0.0, maxMarks = 80.0, percentage = 0.0, grade = "", passFail = "Fail", absent = true
        )
        val cells = cellsFor(sub)
        assertEquals("AB", cells.totalText)
        assertEquals("—", cells.gradeText)
        assertEquals("AB", cells.passLabel)
    }
}
