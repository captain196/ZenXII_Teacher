package com.schoolsync.teacher.data.model

/**
 * Represents a class/section/subject assignment for a teacher.
 * Built in-code from Firestore `subjectAssignments` docs (the only datastore).
 * Each entry maps a teacher to a class + section + subject.
 */
data class ClassAssignment(
    val assignmentId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    /** HW-D1: the canonical code from subjectAssignments.subjectCode.
     *  `subject` above is the human NAME and cannot resolve an assignment
     *  doc id; authorization needs the code. */
    val subjectCode: String = "",
    val classTeacher: Boolean = false
) {
    /** No-arg constructor retained for callers that build an empty instance. */
    constructor() : this(assignmentId = "")

    /**
     * Unique key combining class, section, and subject for display grouping.
     */
    val classKey: String
        get() = "$className-$section"

    val displayLabel: String
        get() = "$className $section - $subject"
}
