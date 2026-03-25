package com.schoolsync.teacher.data.model

/**
 * Student basic info for roster display.
 * Profile read from: Users/Parents/{parentDbKey}/{studentId}/
 * Student list from: Schools/{schoolCode}/{session}/{class}/{section}/Students/
 *   → Map<studentId, Boolean>
 */
data class StudentInfo(
    val studentId: String = "",
    val name: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val rollNo: String = "",
    val className: String = "",
    val section: String = "",
    val gender: String = "",
    val dob: String = "",
    val profilePic: String = "",
    val parentDbKey: String = "",
    val phone: String = "",
    val admissionDate: String = "",
    val email: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(studentId = "")

    val displayName: String
        get() = name.ifBlank { studentId }
}
