package com.schoolsync.teacher.data.model

data class Story(
    val storyId: String = "",
    val mediaUrl: String = "",
    val type: String = "image", // image, video
    /**
     * Poster frame for video stories ("" for images). StoryFirestoreRepository
     * has always written this to Firestore and StoryDoc has always carried it,
     * but it stopped there — no UI model mapped it, so every generated poster
     * was dead weight and video tiles had nothing to show but the raw .mp4
     * (which Coil's image decoder cannot render → permanently blank tile).
     */
    val thumbnailUrl: String = "",
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
