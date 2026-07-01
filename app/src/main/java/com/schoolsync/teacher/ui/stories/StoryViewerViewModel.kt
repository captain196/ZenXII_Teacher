package com.schoolsync.teacher.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import com.schoolsync.teacher.data.repository.firestore.StoryFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Drives the teacher's full-screen "All stories" viewer.
 *
 * Reads the SAME real-time `observeActiveStories()` stream the parent
 * app uses (all non-expired, active stories in the school), groups
 * them by author into [StoryGroup] rings, and sorts admin-high →
 * admin-normal → unviewed → name — matching the parent viewer's order.
 *
 * Teachers are staff, so this viewer is NOT audience-filtered: a
 * teacher sees every section's stories.
 *
 * Seen state is tracked IN MEMORY for the session only (no Firestore
 * write). The teacher viewer is intentionally read-only — persistent
 * seen + viewCount increments are the parent app's job, so a teacher
 * browsing doesn't inflate the parent-facing view counts.
 */
@HiltViewModel
class StoryViewerViewModel @Inject constructor(
    private val storyRepo: StoryFirestoreRepository
) : ViewModel() {

    /** Story ids viewed during this session. */
    private val seenIds = MutableStateFlow<Set<String>>(emptySet())

    val groups: StateFlow<List<StoryGroup>> =
        combine(storyRepo.observeActiveStories(), seenIds) { docs, seen ->
            groupByAuthor(docs, seen)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun markSeen(storyId: String) {
        if (storyId.isBlank()) return
        seenIds.update { if (storyId in it) it else it + storyId }
    }

    private fun groupByAuthor(docs: List<StoryDoc>, seen: Set<String>): List<StoryGroup> {
        return docs
            .groupBy { it.effectiveAuthorId }
            .map { (authorId, authorDocs) ->
                val first = authorDocs.first()
                val stories = authorDocs
                    .sortedBy { it.expiresAtMillis }   // oldest-expiring (≈ oldest posted) first
                    .map { d ->
                        ViewerStory(
                            storyId = d.id,
                            mediaUrl = d.mediaUrl,
                            type = d.type,
                            caption = d.caption,
                            createdAt = createdMillis(d),
                            viewCount = d.viewCount,
                            reactionCounts = d.reactionCounts,
                            isViewed = d.id in seen
                        )
                    }
                StoryGroup(
                    authorId = authorId,
                    authorName = first.effectiveAuthorName,
                    authorPic = first.effectiveAuthorPic,
                    authorType = first.authorType.ifBlank { "teacher" },
                    priority = first.priority.ifBlank { "normal" },
                    stories = stories,
                    hasUnviewed = stories.any { !it.isViewed }
                )
            }
            .sortedWith(
                compareByDescending<StoryGroup> { it.authorType == "admin" }
                    .thenByDescending { it.authorType == "admin" && it.priority == "high" }
                    .thenByDescending { it.hasUnviewed }
                    .thenBy { it.authorName }
            )
    }

    private fun createdMillis(d: StoryDoc): Long = when (val ts = d.createdAt) {
        is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
        is Number -> ts.toLong()
        else -> 0L
    }
}
