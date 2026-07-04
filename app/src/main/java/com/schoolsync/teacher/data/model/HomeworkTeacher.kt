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
)
