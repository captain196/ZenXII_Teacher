package com.schoolsync.teacher.data.model

data class HomeworkTeacher(
    val hwId: String = "",
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val dueDate: String = "",
    val createdAt: Long = 0L,
    val status: String = "active",
    val className: String = "",
    val section: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "description" to description,
        "subject" to subject,
        "teacherId" to teacherId,
        "teacherName" to teacherName,
        "dueDate" to dueDate,
        "createdAt" to createdAt,
        "status" to status,
        "className" to className,
        "section" to section
    )

    companion object {
        fun fromMap(hwId: String, data: Map<String, Any?>): HomeworkTeacher = HomeworkTeacher(
            hwId = hwId,
            title = data["title"]?.toString() ?: "",
            description = data["description"]?.toString() ?: "",
            subject = data["subject"]?.toString() ?: "",
            teacherId = data["teacherId"]?.toString() ?: "",
            teacherName = data["teacherName"]?.toString() ?: "",
            dueDate = data["dueDate"]?.toString() ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            status = data["status"]?.toString() ?: "active",
            className = data["className"]?.toString() ?: "",
            section = data["section"]?.toString() ?: ""
        )
    }
}
