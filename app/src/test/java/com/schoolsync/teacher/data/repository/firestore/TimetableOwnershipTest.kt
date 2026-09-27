package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.model.TimetableEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHOSE PERIOD A TEACHER SEES ON THEIR OWN TIMETABLE.
 *
 * This is the rule that fills the teacher's week, and being wrong in either
 * direction is a real fault: too strict and a teacher's own lesson is missing
 * from their day; too loose and they are looking at a colleague's class.
 *
 * It used to live inline inside a nested loop over every class and every day,
 * where no test could reach it. The screen it decides is the one teachers open
 * first every morning.
 */
class TimetableOwnershipTest {

    private val ME = "STA0001"
    private val THEM = "STA0002"

    private fun period(
        teacherId: String = "", teacher: String = "",
        subject: String = "Mathematics", className: String = "Class 8th",
        section: String = "Section A", isBreak: Boolean = false,
    ) = TimetableEntry(
        day = "Monday", periodNumber = 1, subject = subject,
        teacher = teacher, teacherId = teacherId,
        startTime = "09:00", endTime = "09:40", room = "",
        className = className, section = section,
        type = if (isBreak) "break" else "class", isBreak = isBreak
    )

    private val myKeys = TimetableFirestoreRepository.subjectKeysOf(
        listOf(Triple("Class 8th", "Section A", "Mathematics"))
    )

    private fun claims(p: TimetableEntry, keys: Set<String> = emptySet()) =
        TimetableFirestoreRepository.claimsPeriod(p, ME, keys)

    // ── the ordinary case ────────────────────────────────────────────────

    @Test
    fun aPeriodThatNamesMeIsMine() {
        assertTrue(claims(period(teacherId = ME)))
    }

    @Test
    fun aPeriodThatNamesSomebodyElseIsNotMine() {
        assertFalse(claims(period(teacherId = THEM)))
    }

    /** Legacy documents stored an id in the name field; it still means me. */
    @Test
    fun aLegacyPeriodCarryingMyIdInTheNameFieldIsMine() {
        assertTrue(claims(period(teacher = ME)))
    }

    // ── the admin data gap ───────────────────────────────────────────────

    /**
     * A published period with no teacher stamped on it, for a class/subject I
     * am assigned to. Seen in production; without this the teacher's own lesson
     * is missing from their week.
     */
    @Test
    fun anUnstampedPeriodForASubjectIAmAssignedToIsMine() {
        assertTrue(claims(period(teacherId = ""), myKeys))
    }

    @Test
    fun anUnstampedPeriodForASubjectIDoNotTeachIsNotMine() {
        assertFalse(claims(period(teacherId = "", subject = "Science"), myKeys))
    }

    @Test
    fun anUnstampedPeriodInAClassIDoNotTeachIsNotMine() {
        assertFalse(claims(period(teacherId = "", className = "Class 9th"), myKeys))
    }

    @Test
    fun anUnstampedPeriodInAnotherSectionIsNotMine() {
        assertFalse(claims(period(teacherId = "", section = "Section B"), myKeys))
    }

    /** The gap-filler needs an assignment: with none, it claims nothing. */
    @Test
    fun withNoAssignmentsAnUnstampedPeriodIsNotMine() {
        assertFalse(claims(period(teacherId = "")))
    }

    /**
     * And it must never override a period that DOES name someone. Otherwise a
     * teacher assigned to the subject would claim a colleague's class.
     */
    @Test
    fun theGapFillerNeverOverridesANamedTeacher() {
        assertFalse(claims(period(teacherId = THEM), myKeys))
    }

    // ── breaks ───────────────────────────────────────────────────────────

    /**
     * A break belongs to the whole section, not to a teacher. The caller adds
     * it once and deduplicates across the classes a teacher holds; claiming it
     * here would put lunch on the week once per class.
     */
    @Test
    fun aBreakIsNotClaimedBySubject() {
        assertFalse(claims(period(teacherId = "", subject = "Mathematics", isBreak = true), myKeys))
    }

    @Test
    fun aBreakThatNamesMeIsStillMine() {
        assertTrue(claims(period(teacherId = ME, isBreak = true), myKeys))
    }

    // ── identity ─────────────────────────────────────────────────────────

    /** A signed-out or identity-less caller claims nothing at all. */
    @Test
    fun withoutAnIdentityNothingIsClaimed() {
        assertFalse(TimetableFirestoreRepository.claimsPeriod(period(teacherId = ""), "", myKeys))
        assertFalse(TimetableFirestoreRepository.claimsPeriod(period(teacherId = ""), "", emptySet()))
    }

    /** Case and spacing differences in stored labels must not lose a period. */
    @Test
    fun theSubjectMatchIsCaseAndSpaceInsensitive() {
        assertTrue(claims(period(teacherId = "", subject = " mathematics ",
            className = " class 8th ", section = "section a"), myKeys))
    }
}
