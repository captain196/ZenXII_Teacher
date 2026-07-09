package com.schoolsync.teacher.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import com.schoolsync.teacher.data.model.firestore.StorySharedConfig
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.StoryFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the teacher's full-screen "All stories" viewer.
 *
 * Reads the SAME real-time `observeActiveStories()` stream the parent
 * app uses (all non-expired, active stories in the school), groups
 * them by author into [StoryGroup] rings, and sorts admin-high →
 * admin-normal → unviewed → name — matching the parent viewer's order.
 *
 * Audience scoping (mirrors the parent app): a story reaches a teacher
 * when it is WHOLE-SCHOOL (empty audienceClassKeys), targets a section
 * the teacher teaches / is class-teacher of, or was authored by this
 * teacher. So a class-scoped story (e.g. "9-a") is hidden from teachers
 * who don't teach 9-A instead of leaking to every teacher. The teacher's
 * own posts always show so they can see what they published in the ring.
 *
 * Seen state is PERSISTENT (Instagram-style): hydrated once from the
 * `viewers` collection-group and augmented as the teacher opens stories,
 * so a ring stays grey across app restarts. A staff view also counts
 * toward the story's viewCount (one-user-one-view) and appears in the
 * author's "who viewed" list — EXCEPT when the teacher opens their OWN
 * story, which never self-counts.
 */
@HiltViewModel
class StoryViewerViewModel @Inject constructor(
    private val storyRepo: StoryFirestoreRepository,
    private val teacherRepo: TeacherRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    /** Story ids seen by this staff member (persistent + session). */
    private val seenIds = MutableStateFlow<Set<String>>(emptySet())

    /** This staff member's own id — used to decide isAuthor (hide the
     *  view count from non-authors) and to skip self-counting. */
    val currentUserId: StateFlow<String> = flow { emit(tokenManager.userId.firstOrNull().orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        // LIVE seen-state → persists across launches AND greys the ring the
        // moment a view is recorded (even from a different VM instance).
        viewModelScope.launch {
            storyRepo.observeSeenStoryIds().collect { live -> seenIds.update { it + live } }
        }
    }

    /**
     * (own staffId, canonical audience keys of every section this teacher
     * teaches / is class-teacher of). Computed ONCE — assignments are
     * stable for a session. On fetch failure this degrades to (id, empty),
     * i.e. the teacher still sees whole-school + their own stories.
     */
    private val audienceContext: Flow<Pair<String, Set<String>>> = flow {
        val myId = tokenManager.userId.firstOrNull().orEmpty()
        val keys = teacherRepo.getAssignedClasses().getOrNull().orEmpty()
            .filter { it.section.isNotBlank() }
            .map { StorySharedConfig.audienceKey(it.className, it.section) }
            .toSet()
        emit(myId to keys)
    }

    /** True until the first grouped snapshot arrives — drives the dashboard
     *  stories shimmer so the ring row fades in instead of popping. */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val groups: StateFlow<List<StoryGroup>> =
        combine(storyRepo.observeActiveStories(), seenIds, audienceContext) { docs, seen, ctx ->
            val (myId, myKeys) = ctx
            val visible = docs.filter { d ->
                StorySharedConfig.isWholeSchool(d.audienceClassKeys) ||  // whole-school (empty OR "*")
                d.audienceClassKeys.any { it in myKeys } ||             // a section I teach
                d.effectiveAuthorId == myId                            // my own post
            }
            groupByAuthor(visible, seen)
        }
            // Grouping is idempotent for identical inputs; skip re-emitting an
            // equal list so a seen-state ping that changes nothing doesn't
            // re-sort the ring or bounce downstream collectors (StoryGroup /
            // ViewerStory are data classes → structural equality).
            .distinctUntilChanged()
            .onEach { _isLoading.value = false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * Mark a story seen. Updates the ring's seen-state immediately, and
     * (unless this staff member is the story's own author) persists a
     * viewer doc + bumps viewCount so the view is counted and shows in the
     * author's "who viewed" list.
     */
    fun markSeen(storyId: String, authorId: String) {
        if (storyId.isBlank()) return
        val alreadySeen = storyId in seenIds.value
        seenIds.update { if (storyId in it) it else it + storyId }
        if (alreadySeen) return
        // Never self-count the author's view of their own story.
        if (authorId.isNotBlank() && authorId == currentUserId.value) return
        viewModelScope.launch { storyRepo.markAsViewed(storyId) }
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
