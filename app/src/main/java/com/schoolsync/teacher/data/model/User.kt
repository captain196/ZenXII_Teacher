package com.schoolsync.teacher.data.model

/**
 * Teacher profile model (in-memory; hydrated from the Firestore staff doc).
 */
data class User(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "teacher",
    val schoolId: String = "",
    val schoolDisplayName: String = "",
    val profilePic: String = "",
    val position: String = "",
    val department: String = "",
    val classesAssigned: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val gender: String = "",
    val dob: String = "",
    val qualification: String = "",
    val joiningDate: String = "",
    val address: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(userId = "")

    companion object {
        fun fromLoginUser(loginUser: LoginUser): User {
            return User(
                userId = loginUser.userId,
                name = loginUser.name,
                email = loginUser.email ?: "",
                phone = loginUser.phone ?: "",
                role = loginUser.role,
                schoolId = loginUser.schoolId,
                schoolDisplayName = loginUser.schoolDisplayName ?: "",
                profilePic = loginUser.profilePic ?: "",
                position = loginUser.position ?: "",
                department = loginUser.department ?: "",
                classesAssigned = loginUser.classesAssigned ?: emptyList(),
                subjects = loginUser.subjects ?: emptyList()
            )
        }
    }
}
