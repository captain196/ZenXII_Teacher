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
    val section: String = "",
    // Phase 1b (2026-05-15) — legacy URL-only attachments field, populated
    // by Step 4 writers via dual-emit. Read by HomeworkDetailPanel for the
    // teacher's own review of what they uploaded. Treat URLs as opaque;
    // [com.schoolsync.teacher.util.AttachmentUrlValidator] enforces the
    // https + firebasestorage.googleapis.com allowlist at the click site.
    val attachments: List<String> = emptyList()
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
        "section" to section,
        "attachments" to attachments
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
            section = data["section"]?.toString() ?: "",
            attachments = (data["attachments"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
                ?: emptyList()
        )
    }
}
