package com.schoolsync.teacher.data.model

import com.google.firebase.database.PropertyName

/**
 * Represents a class/section/subject assignment for a teacher.
 * Source: Schools/{schoolCode}/{session}/Academic/Subject_Assignments/
 * Each entry maps a teacher to a class + section + subject.
 */
data class ClassAssignment(
    val assignmentId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    @get:PropertyName("isClassTeacher") @set:PropertyName("isClassTeacher")
    var classTeacher: Boolean = false
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(assignmentId = "")

    /**
     * Unique key combining class, section, and subject for display grouping.
     */
    val classKey: String
        get() = "$className-$section"

    val displayLabel: String
        get() = "$className $section - $subject"
}
