package com.schoolsync.teacher.data.model

data class Story(
    val storyId: String = "",
    val mediaUrl: String = "",
    val type: String = "image", // image, video
    val caption: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val teacherName: String = "",
    val teacherPic: String = "",
    val viewCount: Int = 0,
    /** Canonical class-section tokens this story targets; empty = school-wide. */
    val audienceClassKeys: List<String> = emptyList(),
    /** Aggregate emoji → count (read-only analytics in the teacher app). */
    val reactionCounts: Map<String, Int> = emptyMap()
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}
