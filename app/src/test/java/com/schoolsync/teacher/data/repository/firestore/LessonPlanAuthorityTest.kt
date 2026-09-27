package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.firestore.CurriculumTopicDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE TWO CHECKS THIS APP USED TO LEAVE TO SOMEBODY ELSE.
 *
 * A lesson plan written from the teacher app goes straight to Firestore. The
 * repository's own documentation said subject-assignment authorisation was
 * something the security rules would reject and that topic validity was
 * enforced by the admin UI. Neither is in the path: the rules for lessonPlans
 * admit any staff member of the school, and the admin UI is not involved in a
 * write made from a phone.
 *
 * Both decisions are pure, so both can be executed rather than assumed.
 */
class LessonPlanAuthorityTest {

    private fun assignment(
        className: String, section: String, subject: String, code: String = ""
    ) = ClassAssignment(
        assignmentId = "A1", teacherId = "STA_T1", teacherName = "A Teacher",
        className = className, section = section, subject = subject, subjectCode = code
    )

    private val mine = listOf(
        assignment("Class 8th", "Section A", "Mathematics", "MATH"),
        assignment("Class 9th", "Section B", "Science", "SCI"),
    )

    // ── who may plan what ────────────────────────────────────────────────

    @Test
    fun aTeacherMayPlanTheirOwnSubject() {
        assertTrue(LessonPlanFirestoreRepository
            .isAssignedTo(mine, "Class 8th", "Section A", "Mathematics"))
    }

    @Test
    fun theSubjectMayBeNamedByItsCode() {
        assertTrue(LessonPlanFirestoreRepository
            .isAssignedTo(mine, "Class 8th", "Section A", "MATH"))
    }

    @Test
    fun classAndSectionAreMatchedInCanonicalForm() {
        // The picker and the stored assignment do not always agree on shape.
        assertTrue(LessonPlanFirestoreRepository
            .isAssignedTo(mine, "8", "A", "Mathematics"))
    }

    @Test
    fun aSubjectTheyDoNotTeachToThatClassIsRefused() {
        assertFalse(LessonPlanFirestoreRepository
            .isAssignedTo(mine, "Class 8th", "Section A", "Science"))
    }

    @Test
    fun theSameSubjectInAnotherSectionIsRefused() {
        assertFalse(LessonPlanFirestoreRepository
            .isAssignedTo(mine, "Class 8th", "Section B", "Mathematics"))
    }

    @Test
    fun theSameSubjectInAnotherClassIsRefused() {
        assertFalse(LessonPlanFirestoreRepository
            .isAssignedTo(mine, "Class 10th", "Section A", "Mathematics"))
    }

    @Test
    fun aTeacherWithNoAssignmentsMayPlanNothing() {
        assertFalse(LessonPlanFirestoreRepository
            .isAssignedTo(emptyList(), "Class 8th", "Section A", "Mathematics"))
    }

    /** Blanks must never widen into a wildcard. */
    @Test
    fun blanksAreNotWildcards() {
        assertFalse(LessonPlanFirestoreRepository.isAssignedTo(mine, "", "Section A", "Mathematics"))
        assertFalse(LessonPlanFirestoreRepository.isAssignedTo(mine, "Class 8th", "", "Mathematics"))
        assertFalse(LessonPlanFirestoreRepository.isAssignedTo(mine, "Class 8th", "Section A", ""))
    }

    /** An empty subjectCode must not match a request that names no subject. */
    @Test
    fun anEmptyCodeDoesNotMatchAnEmptyRequest() {
        val noCode = listOf(assignment("Class 8th", "Section A", "Mathematics", ""))
        assertFalse(LessonPlanFirestoreRepository.isAssignedTo(noCode, "Class 8th", "Section A", ""))
    }

    // ── which topic a plan may name ──────────────────────────────────────

    private val topics = listOf(
        CurriculumTopicDoc(id = "T1", topicId = "T1", parentDocId = "CUR_A", title = "Fractions"),
        CurriculumTopicDoc(id = "T2", topicId = "T2", parentDocId = "CUR_A", title = "Decimals"),
    )

    @Test
    fun aTopicInThisCurriculumResolvesToItsOwnTitle() {
        assertEquals("Decimals",
            LessonPlanFirestoreRepository.topicTitleOrNull(topics, "T2"))
    }

    /**
     * The title is what a parent reads on the lesson months later. Taking it
     * from the caller meant any title could be attached to any topic id.
     */
    @Test
    fun theTitleComesFromTheTopicNotFromTheCaller() {
        val title = LessonPlanFirestoreRepository.topicTitleOrNull(topics, "T1")
        assertEquals("Fractions", title)
    }

    @Test
    fun aTopicThatIsNotInThisCurriculumResolvesToNothing() {
        assertNull(LessonPlanFirestoreRepository.topicTitleOrNull(topics, "T_FROM_ANOTHER_CLASS"))
    }

    @Test
    fun aDeletedTopicResolvesToNothing() {
        assertNull(LessonPlanFirestoreRepository.topicTitleOrNull(emptyList(), "T1"))
    }

    @Test
    fun noTopicChosenIsNotATopicToResolve() {
        assertNull(LessonPlanFirestoreRepository.topicTitleOrNull(topics, ""))
    }
}
