package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class HomeworkDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val sectionKey: String = "",     // "Class 9th/Section A"
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val dueDate: String = "",        // "2026-03-28"
    val createdAt: Any? = null,
    val status: String = "active",   // "active", "closed"
    val submissionCount: Int = 0,
    val totalStudents: Int = 0,
    // Legacy URL-only attachments. Kept for backward compatibility with
    // older docs and pre-Step-2 readers. Step 4 writers dual-emit both
    // this field and `attachmentObjects` so legacy readers still see URLs.
    val attachments: List<String> = emptyList(),
    // Rich attachment metadata (Step 2 backward-compatibility addition,
    // 2026-05-15). Each entry is the raw Firestore Map for an Attachment
    // — call `parsedAttachments()` (extension fn on HomeworkDoc) to merge
    // this with `attachments` into a canonical List<Attachment>. Empty
    // for legacy docs that lack the field.
    val attachmentObjects: List<Map<String, Any?>> = emptyList()
)
