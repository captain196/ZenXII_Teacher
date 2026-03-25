package com.schoolsync.teacher.data.model

/**
 * Notice/announcement model.
 * Path: Schools/{schoolCode}/Communication/Notices/
 */
data class Notice(
    val noticeId: String = "",
    val title: String = "",
    val body: String = "",
    val author: String = "",
    val authorId: String = "",
    val date: String = "",
    val timestamp: Long = 0L,
    val category: String = "",
    val targetAudience: String = "",
    val attachmentUrl: String = "",
    val priority: String = "normal",
    val isActive: Boolean = true
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(noticeId = "")

    val isHighPriority: Boolean
        get() = priority.equals("high", ignoreCase = true)
}
