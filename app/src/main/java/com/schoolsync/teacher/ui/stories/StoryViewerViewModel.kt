package com.schoolsync.teacher.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import com.schoolsync.teacher.data.model.firestore.StorySharedConfig
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.StoryFirestoreRepository
import com.schoolsync.teacher.util.PendingStoryViews
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val tokenManager: TokenManager,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "StoryViewerVM"
        /** Cap on waiting for a non-blank viewer id; a logged-out session must
         *  not hang the coroutine forever. */
        private const val IDENTITY_TIMEOUT_MS = 3_000L
    }

    /** Story ids seen by this staff member (persistent + session). */
    private val seenIds = MutableStateFlow<Set<String>>(emptySet())

    /** This staff member's own id — used to decide isAuthor (hide the
     *  view count from non-authors) and to skip self-counting. */
    val currentUserId: StateFlow<String> = flow { emit(tokenManager.userId.firstOrNull().orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Own name + avatar for the tray's "Your story" tile. Needed even with no
     *  stories posted, so it can't come from the (absent) own story group. */
    val currentUserName: StateFlow<String> = flow { emit(tokenManager.userName.firstOrNull().orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val currentUserPic: StateFlow<String> = flow { emit(tokenManager.profilePic.firstOrNull().orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        // LIVE seen-state → persists across launches AND greys the ring the
        // moment a view is recorded (even from a different VM instance).
        viewModelScope.launch {
            storyRepo.observeSeenStoryIds().collect { live -> seenIds.update { it + live } }
        }
        // Land any view whose write previously failed, before doing anything else.
        retryPendingViews()
    }

    // ── Author's "who viewed" sheet (opened from the viewer chrome) ────

    /** Story whose seen-by sheet is open in the viewer; null = closed. */
    private val _seenBySheetStoryId = MutableStateFlow<String?>(null)
    val seenBySheetStoryId: StateFlow<String?> = _seenBySheetStoryId.asStateFlow()

    private val _seenByState = MutableStateFlow<ViewersUiState>(ViewersUiState.Loading)
    val seenByState: StateFlow<ViewersUiState> = _seenByState.asStateFlow()

    /**
     * Full insights behind the open sheet — views, watched, reactions AND the
     * viewer rows.
     *
     * Deliberately ONE object behind ONE sheet: the "N views" pill and the ⋮ →
     * Insights action open the same surface. Two near-identical sheets (one
     * "who viewed", one "stats") would be the same duplication this redesign
     * exists to remove, just one level down.
     */
    private val _seenByInsights =
        MutableStateFlow<com.schoolsync.teacher.data.repository.firestore.StoryInsights?>(null)
    val seenByInsights: StateFlow<com.schoolsync.teacher.data.repository.firestore.StoryInsights?> =
        _seenByInsights.asStateFlow()

    private val _seenByQuery = MutableStateFlow("")
    val seenByQuery: StateFlow<String> = _seenByQuery.asStateFlow()

    private val _seenByVisible = MutableStateFlow(VIEWERS_PAGE_SIZE)
    val seenByVisible: StateFlow<Int> = _seenByVisible.asStateFlow()

    private var seenByJob: kotlinx.coroutines.Job? = null

    /**
     * Open the live seen-by list for one of the author's OWN stories — the
     * affordance Instagram and WhatsApp both put behind the view count in the
     * full-screen viewer. Previously the count was displayed with nothing to
     * tap, so the only route to the list was the separate My Stories screen.
     */
    fun openSeenBy(storyId: String) {
        if (storyId.isBlank()) return
        _seenBySheetStoryId.value = storyId
        _seenByQuery.value = ""
        _seenByVisible.value = VIEWERS_PAGE_SIZE
        subscribeSeenBy(storyId)
    }

    private fun subscribeSeenBy(storyId: String) {
        seenByJob?.cancel()
        _seenByState.value = ViewersUiState.Loading
        seenByJob = viewModelScope.launch {
            storyRepo.observeStoryInsights(storyId).collect { result ->
                if (_seenBySheetStoryId.value != storyId) return@collect
                result.fold(
                    onSuccess = { data ->
                        _seenByInsights.value = data
                        _seenByState.value = ViewersUiState.Ready(data.viewers)
                    },
                    onFailure = { e ->
                        android.util.Log.w(TAG, "seen-by stream failed for $storyId", e)
                        _seenByState.value =
                            ViewersUiState.Error(e.message ?: "Couldn't load who viewed this.")
                    }
                )
            }
        }
    }

    fun retrySeenBy() {
        val id = _seenBySheetStoryId.value ?: return
        subscribeSeenBy(id)
    }

    fun setSeenByQuery(q: String) {
        _seenByQuery.value = q
        _seenByVisible.value = VIEWERS_PAGE_SIZE
    }

    fun loadMoreSeenBy() { _seenByVisible.value += VIEWERS_PAGE_SIZE }

    fun closeSeenBy() {
        seenByJob?.cancel()
        seenByJob = null
        _seenBySheetStoryId.value = null
        _seenByState.value = ViewersUiState.Loading
        _seenByInsights.value = null
        _seenByQuery.value = ""
        _seenByVisible.value = VIEWERS_PAGE_SIZE
    }

    // ── Story-level actions (viewer ⋮) ────────────────────────────────

    /** Emitted after a successful delete so the viewer can close itself. */
    private val _storyDeleted = MutableStateFlow<String?>(null)
    val storyDeleted: StateFlow<String?> = _storyDeleted.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /**
     * Delete one of this staff member's own stories from the viewer.
     *
     * Callers must gate on the MANAGE capability — Stories is a baseline
     * module everyone holds at view, so posting is edit-level but destroying
     * is manage-level, matching `firestore.rules`. Gating here as well would
     * duplicate a check the UI already has to make to hide the menu item.
     */
    fun deleteStory(storyId: String) {
        if (storyId.isBlank()) return
        viewModelScope.launch {
            storyRepo.deleteStory(storyId).fold(
                onSuccess = {
                    _actionMessage.value = "Story deleted"
                    _storyDeleted.value = storyId
                },
                onFailure = { e ->
                    android.util.Log.w(TAG, "delete failed for $storyId", e)
                    _actionMessage.value = e.message ?: "Couldn't delete this story"
                }
            )
        }
    }

    fun consumeStoryDeleted() { _storyDeleted.value = null }
    fun consumeActionMessage() { _actionMessage.value = null }

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
            groupByAuthor(visible, seen, myId)
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
     * Mark a story seen — DURABLY.
     *
     * The ring greys immediately (optimistic, so the UI stays instant), but the
     * viewer-doc write is now checked: if it fails the story id is parked in
     * [PendingStoryViews] and retried on every subsequent viewer-VM start until
     * it lands.
     *
     * This closes a permanent data-loss bug. The old version updated `seenIds`
     * first and discarded the write's Result, so a write that failed (offline,
     * stale token, a transient denial before a claims refresh) left the story
     * already marked seen — and the `alreadySeen` short-circuit then guaranteed
     * it would NEVER be attempted again. That viewer simply never appeared in
     * the author's "who viewed" list, with nothing anywhere to say so.
     */
    fun markSeen(storyId: String, authorId: String) {
        if (storyId.isBlank()) return
        val alreadySeen = storyId in seenIds.value
        seenIds.update { if (storyId in it) it else it + storyId }

        viewModelScope.launch {
            // Resolve the viewer id AT CALL TIME, waiting for a non-blank one.
            //
            // `currentUserId` is a StateFlow seeded with "" until DataStore
            // resolves, and markSeen fires 500ms after a story becomes current
            // — a race. Reading `.value` directly made the policy return
            // SKIP_INVALID and the write was never attempted, silently. This is
            // the same defect that broke every parent view; staff viewing
            // another author's story goes through THIS path, so it had the
            // identical exposure.
            val me = withTimeoutOrNull(IDENTITY_TIMEOUT_MS) {
                tokenManager.userId.first { !it.isNullOrBlank() }
            }.orEmpty()

            // Decision is a pure, tested function (StoryViewPolicy) and is
            // logged on EVERY path — "no viewer doc appeared" was previously
            // indistinguishable from "we deliberately skipped it".
            val decision = StoryViewPolicy.decide(
                storyId = storyId,
                authorId = authorId,
                currentUserId = me,
                alreadySeen = alreadySeen
            )
            com.schoolsync.teacher.util.debugLog(
                "Story.view DECISION=$decision story=$storyId author=$authorId me=$me"
            )
            if (decision != ViewRecordDecision.RECORD) return@launch
            persistView(storyId)
        }
    }

    /**
     * Record that this staff member watched a story to the END (image timer
     * elapsed, or video reached its last frame). Tapping forward doesn't count.
     *
     * Same self-view rule as [markSeen]: an author completing their own story
     * is not recorded. Best-effort — see markAsCompleted for why completions
     * aren't queued for retry the way views are.
     */
    fun markCompleted(storyId: String, authorId: String) {
        if (storyId.isBlank()) return
        viewModelScope.launch {
            // Same call-time resolution as markSeen: reading the StateFlow's
            // seeded "" would make an author's own completion look like
            // someone else's and record it.
            val me = withTimeoutOrNull(IDENTITY_TIMEOUT_MS) {
                tokenManager.userId.first { !it.isNullOrBlank() }
            }.orEmpty()
            // Logged on EVERY path, like the view write. Completion produced
            // `completed: undefined` on every viewer doc during UAT and there
            // was no way to tell whether the callback never fired or the write
            // was rejected — that ambiguity is the thing to eliminate first.
            if (authorId.isNotBlank() && authorId == me) {
                com.schoolsync.teacher.util.debugLog(
                    "Story.complete SKIP_SELF story=$storyId me=$me"
                )
                return@launch
            }
            com.schoolsync.teacher.util.debugLog("Story.complete WRITING story=$storyId me=$me")
            storyRepo.markAsCompleted(storyId).fold(
                onSuccess = { com.schoolsync.teacher.util.debugLog("Story.complete OK story=$storyId") },
                onFailure = { e ->
                    com.schoolsync.teacher.util.debugLog(
                        "Story.complete FAILED story=$storyId ${e.javaClass.simpleName}: ${e.message}"
                    )
                    android.util.Log.w(TAG, "completion write failed for $storyId", e)
                }
            )
        }
    }

    /** Write one viewer doc, parking it for retry if the write doesn't land. */
    private suspend fun persistView(storyId: String) {
        com.schoolsync.teacher.util.debugLog("Story.view WRITING story=$storyId")
        storyRepo.markAsViewed(storyId).fold(
            onSuccess = {
                com.schoolsync.teacher.util.debugLog("Story.view OK story=$storyId")
                PendingStoryViews.remove(appContext, storyId)
            },
            onFailure = { e ->
                // The exception TYPE is the whole diagnosis: PERMISSION_DENIED
                // means the payload can't satisfy the rule (wrong schoolId or
                // userId); UNAVAILABLE means offline and the retry will fix it.
                com.schoolsync.teacher.util.debugLog(
                    "Story.view FAILED story=$storyId ${e.javaClass.simpleName}: ${e.message}"
                )
                android.util.Log.w(TAG, "view write failed for $storyId, queued for retry", e)
                PendingStoryViews.add(appContext, storyId)
            }
        )
    }

    /**
     * Drain the retry queue. Runs on every VM start — i.e. every time stories
     * are opened — which is frequent enough to land a deferred view well
     * inside a story's 24h life without a WorkManager job.
     */
    private fun retryPendingViews() {
        viewModelScope.launch {
            val pending = PendingStoryViews.all(appContext)
            if (pending.isEmpty()) return@launch
            android.util.Log.d(TAG, "retrying ${pending.size} deferred story view(s)")
            pending.forEach { persistView(it) }
        }
    }

    /**
     * @param myId this staff member's id. Their OWN stories count as seen: a
     *   self-view is deliberately never written as a viewer doc (no
     *   self-counting), so without this the author's own arcs would reset to
     *   "unseen" on every app restart and their ring would sit permanently
     *   accented — obviously wrong, and much more visible now that each story
     *   draws its own arc.
     */
    private fun groupByAuthor(
        docs: List<StoryDoc>,
        seen: Set<String>,
        myId: String
    ): List<StoryGroup> {
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
                            isViewed = d.id in seen || d.effectiveAuthorId == myId,
                            thumbnailUrl = d.thumbnailUrl
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
