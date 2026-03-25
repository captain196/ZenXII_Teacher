package com.schoolsync.teacher.util

import com.schoolsync.teacher.data.model.ClassAssignment

data class TeacherPermissions(
    val canMarkAttendance: Boolean,
    val canViewAllStudents: Boolean,
    val canApproveLeaves: Boolean,
    val canCreateAnyFlag: Boolean,
    val canCreateSubjectFlag: Boolean,
    val canCreateHomework: Boolean,
    val canUploadGallery: Boolean = true
)

object RoleHelper {
    fun getPermissions(
        assignments: List<ClassAssignment>,
        className: String,
        section: String,
        subject: String? = null
    ): TeacherPermissions {
        val isClassTeacher = assignments.any {
            it.className == className && it.section == section && it.classTeacher
        }
        val isSubjectTeacher = subject?.let { subj ->
            assignments.any {
                it.className == className && it.section == section && it.subject == subj
            }
        } ?: false

        return TeacherPermissions(
            canMarkAttendance = isClassTeacher,
            canViewAllStudents = isClassTeacher,
            canApproveLeaves = isClassTeacher,
            canCreateAnyFlag = isClassTeacher,
            canCreateSubjectFlag = isSubjectTeacher || isClassTeacher,
            canCreateHomework = isSubjectTeacher || isClassTeacher
        )
    }

    fun getAssignedClasses(assignments: List<ClassAssignment>): List<Pair<String, String>> =
        assignments.map { it.className to it.section }.distinct()

    fun getAssignedSubjects(assignments: List<ClassAssignment>, className: String, section: String): List<String> =
        assignments.filter { it.className == className && it.section == section }.map { it.subject }

    fun isClassTeacher(assignments: List<ClassAssignment>, className: String, section: String): Boolean =
        assignments.any { it.className == className && it.section == section && it.classTeacher }
}
