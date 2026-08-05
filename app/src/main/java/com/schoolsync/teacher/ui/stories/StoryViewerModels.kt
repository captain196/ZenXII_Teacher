package com.schoolsync.teacher.ui.stories

/**
 * A single story as the full-screen viewer needs it. Lighter than the
 * Firestore [com.schoolsync.teacher.data.model.firestore.StoryDoc] and
 * carries the per-session `isViewed` flag the rings/ordering use.
 */
data class ViewerStory(
    val storyId: String,
    val mediaUrl: String,
    val type: String,            // "image" | "video"
    val caption: String,
    val createdAt: Long,
    val viewCount: Int,
    val reactionCounts: Map<String, Int>,
    val isViewed: Boolean
)

/**
 * All active stories from one author, grouped into a tappable ring
 * (Instagram/WhatsApp style). Sorted by the viewer VM: admin-high →
 * admin-normal → unviewed → name.
 */
data class StoryGroup(
    val authorId: String,
    val authorName: String,
    val authorPic: String,
    val authorType: String,      // "teacher" | "admin"
    val priority: String,        // "high" | "normal"
    val stories: List<ViewerStory>,
    val hasUnviewed: Boolean
)
