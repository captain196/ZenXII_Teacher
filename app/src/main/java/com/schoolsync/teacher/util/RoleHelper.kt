package com.schoolsync.teacher.util

import com.schoolsync.teacher.data.model.ClassAssignment

// RBAC NOTE: the old TeacherPermissions data class + getPermissions() (a
// teacher-only, class-assignment-derived permission model) were REMOVED — the
// centralized Staff & Roles module (staffCapabilities + ModuleGate.canEdit/
// canManage) is now the single gating authority. RoleHelper keeps ONLY the
// class/section ASSIGNMENT-scoping helpers, which are orthogonal to capability
// grants (caps grant the module; assignments scope which classes within it).
object RoleHelper {
    fun getAssignedClasses(assignments: List<ClassAssignment>): List<Pair<String, String>> =
        assignments.map { it.className to it.section }.distinct()

    fun getAssignedSubjects(assignments: List<ClassAssignment>, className: String, section: String): List<String> =
        assignments.filter { it.className == className && it.section == section }.map { it.subject }

    fun isClassTeacher(assignments: List<ClassAssignment>, className: String, section: String): Boolean =
        assignments.any { it.className == className && it.section == section && it.classTeacher }
}
