package com.schoolsync.teacher.ui.stories

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.schoolsync.teacher.util.StoryVideoCache
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R
import androidx.compose.ui.res.pluralStringResource

private const val IMAGE_DURATION_MS = 6000L
/**
 * How long a story video may sit in STATE_BUFFERING before it is treated as
 * failed. ExoPlayer emits no error for "still buffering", so without a watchdog
 * a stalled video holds the viewer on a black frame indefinitely.
 */
private const val VIDEO_LOAD_TIMEOUT_MS = 12000L
private const val DISMISS_THRESHOLD_PX = 250f
/** Drag distance over which the story fully fades out while swiping down. */
private const val DISMISS_DISTANCE_PX = 620f
/** Pinch-out below this scale (on release) dismisses the story. */
private const val DISMISS_ZOOM_OUT = 0.75f
/** Lower bound the pinch can shrink to (enables the zoom-out-to-dismiss). */
private const val MIN_ZOOM_OUT = 0.5f

/**
 * Full-screen story viewer for the teacher app — the upgraded Compose
 * viewer that ports the smoothness/accuracy/zoom techniques from the
 * Grader_S reference into Compose:
 *   • video progress tied to actual playback (position/duration) and
 *     advance on STATE_ENDED — not a flat timer
 *   • shared disk-cached + tuned-buffering ExoPlayer (StoryVideoCache)
 *   • hand-rolled two-finger pinch-zoom + pan on ALL media (image AND
 *     video) via a pointerInput gesture loop — pinch-IN zooms to 4×,
 *     pinch-OUT below 0.75× dismisses the story; zooming pauses the
 *     progress timer. (No double-tap zoom; no Telephoto dependency.)
 *   • image timer starts only once the image is actually displayed
 *
 * Read-only: teachers browse all sections' stories; seen state is
 * in-memory for the session (see [StoryViewerViewModel]).
 */
@Composable
fun StoryViewerScreen(
    initialAuthorId: String,
    /** Open directly on this story rather than the author's first unseen one —
     *  set when the entry point names a specific story (e.g. tapping one of
     *  your own cards in the grid). */
    initialStoryId: String? = null,
    /** Manage-level Stories rights — gates deleting your own story from the ⋮. */
    canDelete: Boolean = false,
    onClose: () -> Unit,
    viewModel: StoryViewerViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val seenBySheetStoryId by viewModel.seenBySheetStoryId.collectAsStateWithLifecycle()
    // Deleting is destructive and irreversible, so the ⋮ action stages the id
    // here and a confirmation dialog does the actual call.
    var pendingDeleteStoryId by remember { mutableStateOf<String?>(null) }

    // Close the viewer once the story it was showing is gone — otherwise it
    // would sit on a story that no longer exists.
    val deletedStoryId by viewModel.storyDeleted.collectAsStateWithLifecycle()
    LaunchedEffect(deletedStoryId) {
        if (deletedStoryId != null) {
            viewModel.consumeStoryDeleted()
            onClose()
        }
    }

    // The viewer is a bare overlay with no Scaffold, so there's no snackbar
    // host to post to. A Toast is the honest way to report a FAILED delete —
    // without it the ⋮ action would silently do nothing, which is exactly the
    // phantom-success pattern this module has been burned by before.
    val toastContext = LocalContext.current
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeActionMessage()
        }
    }

    // Back closes the seen-by sheet first, then the viewer — otherwise a back
    // press with the sheet open would drop the whole story.
    BackHandler(onBack = { if (seenBySheetStoryId != null) viewModel.closeSeenBy() else onClose() })

    // ── Freeze the running order for this viewing session ─────────────
    //
    // `groups` is LIVE and sorted with `.thenByDescending { hasUnviewed }`, so
    // the instant you dwell 500ms on someone their ring flips to seen and they
    // RE-SORT below everyone still unseen — while you are looking at them. With
    // an unkeyed pager that silently swapped the slot's author mid-swipe, which
    // is why tapping one person could land you on another.
    //
    // WhatsApp and Instagram both pin the running order when the viewer opens.
    // We snapshot the ORDER only; content (counts, seen flags, new stories)
    // stays live because we re-map the frozen ids onto the current groups.
    //
    // Freezing waits until the tapped author is actually PRESENT: the audience
    // filter resolves asynchronously, so an early snapshot could omit them and
    // `indexOfFirst` would return -1 → page 0 → the wrong person.
    var frozenOrder by remember(initialAuthorId) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(groups, initialAuthorId) {
        if (frozenOrder == null && groups.any { it.authorId == initialAuthorId }) {
            frozenOrder = groups.map { it.authorId }
        }
    }

    // Decisive breadcrumb: distinguishes "the gate never opened" (freeze stuck
    // → spinner forever → no page → no dwell → no view) from "the page rendered
    // but the dwell didn't fire". Empty logs after a viewing session meant the
    // view path wasn't reached AT ALL, and this says which half is at fault.
    LaunchedEffect(groups.size, frozenOrder == null) {
        com.schoolsync.teacher.util.debugLog(
            "Story.viewer GATE groups=${groups.size} target=$initialAuthorId " +
            "present=${groups.any { it.authorId == initialAuthorId }} frozen=${frozenOrder != null}"
        )
    }

    val order = frozenOrder
    if (order == null) {
        // Not ready: either nothing loaded yet, or the tapped author hasn't
        // arrived. Showing a spinner is correct — rendering now is what used to
        // open the wrong person.
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
        }
        return
    }

    // Frozen ids re-mapped onto live groups; authors who appear mid-session are
    // appended rather than injected, so nothing shifts under the current page.
    val orderedGroups = remember(groups, order) {
        val byId = groups.associateBy { it.authorId }
        order.mapNotNull { byId[it] } + groups.filter { it.authorId !in order }
    }
    if (orderedGroups.isEmpty()) { onClose(); return }

    Box(modifier = Modifier.fillMaxSize()) {
        StoryViewerPager(
            groups = orderedGroups,
            initialAuthorId = initialAuthorId,
            initialStoryId = initialStoryId,
            currentUserId = currentUserId,
            onClose = onClose,
            onSeen = viewModel::markSeen,
            onCompleted = viewModel::markCompleted,
            onOpenSeenBy = viewModel::openSeenBy,
            canDelete = canDelete,
            onDeleteStory = { storyId -> pendingDeleteStoryId = storyId },
            // The sheet takes over input while it's up, so the story's tap
            // zones and swipe gestures don't fire behind it.
            interactionEnabled = seenBySheetStoryId == null
        )

        pendingDeleteStoryId?.let { storyId ->
            AlertDialog(
                onDismissRequest = { pendingDeleteStoryId = null },
                title = { Text(stringResource(R.string.story_delete_q), color = TextPrimary) },
                text = {
                    // Capped + scrollable per the app's dialog rule, so the
                    // body can't clip in landscape on a short viewport. No
                    // input field here, so imePadding isn't applicable.
                    Text(
                        stringResource(R.string.story_delete_body),
                        color = TextSecondary,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteStoryId = null
                        viewModel.deleteStory(storyId)
                    }) { Text(stringResource(R.string.common_delete), color = ErrorRed, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteStoryId = null }) {
                        Text(stringResource(R.string.common_cancel), color = TextSecondary)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (seenBySheetStoryId != null) {
            val seenByState by viewModel.seenByState.collectAsStateWithLifecycle()
            val insights by viewModel.seenByInsights.collectAsStateWithLifecycle()
            val seenByQuery by viewModel.seenByQuery.collectAsStateWithLifecycle()
            val seenByVisible by viewModel.seenByVisible.collectAsStateWithLifecycle()
            StoryViewerSeenBySheet(
                insights = insights,
                state = seenByState,
                query = seenByQuery,
                visibleCount = seenByVisible,
                onQueryChange = viewModel::setSeenByQuery,
                onLoadMore = viewModel::loadMoreSeenBy,
                onRetry = viewModel::retrySeenBy,
                onDismiss = viewModel::closeSeenBy
            )
        }
    }
}

/**
 * The author's live "Seen by" sheet, opened from the viewer chrome — where
 * Instagram and WhatsApp both put it.
 *
 * An in-composition overlay rather than a Dialog so it shares the viewer's
 * edge-to-edge window and behaves in landscape and around a camera cutout;
 * height is capped and the body is the list's own LazyColumn, so a long
 * viewer list scrolls inside the sheet instead of running off the top.
 */
@Composable
private fun StoryViewerSeenBySheet(
    insights: com.schoolsync.teacher.data.repository.firestore.StoryInsights?,
    state: ViewersUiState,
    query: String,
    visibleCount: Int,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.72f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(SurfaceDark)
                    // Swallow taps so tapping the sheet doesn't dismiss it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextTertiary.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(14.dp))
                val count = insights?.displayViewCount ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.story_views_count, count, count),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = TextSecondary)
                    }
                }
                // Stats live in the SAME sheet as the list, so the ⋮ → Insights
                // action and the "N views" pill land on one surface rather than
                // two near-identical ones.
                if (insights != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SeenByStat(stringResource(R.string.story_views), insights.displayViewCount.toString(), Modifier.weight(1f))
                        SeenByStat(stringResource(R.string.story_watched), insights.completedCount.toString(), Modifier.weight(1f))
                        SeenByStat(
                            stringResource(R.string.story_reactions),
                            insights.reactionCounts.values.sum().toString(),
                            Modifier.weight(1f)
                        )
                    }
                    // Says the quiet part out loud. Zero here is the normal
                    // result of testing on your own account, and without this
                    // line it reads as a number that failed to update.
                    if (insights.displayViewCount == 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.story_own_views_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    storyViewersSection(
                        state = state,
                        query = query,
                        onQueryChange = onQueryChange,
                        visibleCount = visibleCount,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry
                    )
                    item(key = "tail") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

/** One stat in the seen-by sheet header. */
@Composable
private fun SeenByStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Glass)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryViewerPager(
    groups: List<StoryGroup>,
    initialAuthorId: String,
    initialStoryId: String?,
    currentUserId: String,
    onClose: () -> Unit,
    onSeen: (String, String) -> Unit,
    /** Called when a story is watched to the end (not tapped past). */
    onCompleted: (String, String) -> Unit,
    onOpenSeenBy: (String) -> Unit,
    /** Manage-level rights: gates the ⋮ delete action. */
    canDelete: Boolean,
    onDeleteStory: (String) -> Unit,
    /** False while the seen-by sheet is up, so gestures don't fire behind it. */
    interactionEnabled: Boolean
) {
    // Resolved against the FROZEN list, and the caller guarantees the author is
    // present before we get here — so this can no longer silently fall back to
    // page 0 (which is what opened the wrong person).
    val initialPage = groups.indexOfFirst { it.authorId == initialAuthorId }.coerceAtLeast(0)
    // Keyed on the resolved author so the state is rebuilt if the viewer is
    // re-opened on someone else; rememberPagerState otherwise honours
    // initialPage only on its very first composition.
    val pagerState = rememberPagerState(initialPage = initialPage) { groups.size }
    val scope = rememberCoroutineScope()

    // No opaque background here — each page paints its own black backdrop
    // whose alpha fades during a swipe-down / pinch-out dismiss, so the
    // story appears to fall away and reveal what's behind it.
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            // WITHOUT a key, Compose reuses page slots POSITIONALLY — so any
            // change to the list made slot N render a different author, and the
            // page's `remember(group.authorId)` then reset the story index too.
            // Keying by author makes a slot's identity explicit.
            key = { index -> groups[index].authorId },
            userScrollEnabled = interactionEnabled,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            AuthorStoryPage(
                // WhatsApp/Instagram-style cube: each person's page rotates
                // around the edge you're dragging toward, so a swipe reads as
                // turning to the next PERSON rather than sliding a flat panel.
                // Applied at the page root so it composes over the page's own
                // dismiss transform without fighting it.
                modifier = Modifier.graphicsLayer {
                    val offset =
                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                    val clamped = offset.coerceIn(-1f, 1f)
                    cameraDistance = 18f * density
                    rotationY = clamped * -60f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = if (clamped < 0f) 0f else 1f,
                        pivotFractionY = 0.5f
                    )
                    // Darken the outgoing face so the turn reads as depth.
                    val dim = 1f - (kotlin.math.abs(clamped) * 0.35f)
                    alpha = dim
                },
                group = groups[pageIndex],
                initialStoryId = initialStoryId,
                isCurrentPage = pagerState.currentPage == pageIndex,
                currentUserId = currentUserId,
                onClose = onClose,
                onSeen = onSeen,
                onCompleted = onCompleted,
                onOpenSeenBy = onOpenSeenBy,
                canDelete = canDelete,
                onDeleteStory = onDeleteStory,
                interactionEnabled = interactionEnabled,
                onGroupFinished = {
                    scope.launch {
                        val next = pageIndex + 1
                        if (next < groups.size) pagerState.animateScrollToPage(next) else onClose()
                    }
                }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AuthorStoryPage(
    modifier: Modifier = Modifier,
    group: StoryGroup,
    initialStoryId: String?,
    isCurrentPage: Boolean,
    currentUserId: String,
    onClose: () -> Unit,
    onSeen: (String, String) -> Unit,
    /** Called when a story is watched to the end (not tapped past). */
    onCompleted: (String, String) -> Unit,
    onOpenSeenBy: (String) -> Unit,
    /** Manage-level rights: gates the ⋮ delete action. */
    canDelete: Boolean,
    onDeleteStory: (String) -> Unit,
    interactionEnabled: Boolean,
    onGroupFinished: () -> Unit
) {
    val stories = group.stories
    val storyByFallback = stringResource(R.string.story_by_fmt, group.authorName)
    if (stories.isEmpty()) return
    // Only the story's own author (the uploader) sees the view count in the
    // viewer chrome; everyone else's chrome hides it. Full "who viewed" list
    // lives in the author's My-Stories insights + the admin panel.
    val isAuthor = currentUserId.isNotBlank() && group.authorId == currentUserId

    // Open on first unseen story (WhatsApp/Instagram behaviour).
    var index by remember(group.authorId) {
        // An explicitly-named story wins: the caller pointed at THAT one, so
        // opening on "first unseen" instead would show something else. Falls
        // back to first-unseen (Instagram/WhatsApp behaviour) when the id
        // isn't in this author's group — which is the case for every page
        // except the one the caller actually meant.
        val named = initialStoryId?.let { id -> stories.indexOfFirst { it.storyId == id } } ?: -1
        val firstUnseen = stories.indexOfFirst { !it.isViewed }
        mutableIntStateOf(
            when {
                named >= 0 -> named
                firstUnseen >= 0 -> firstUnseen
                else -> 0
            }
        )
    }
    val story = stories.getOrNull(index) ?: return
    val isVideo = story.type.equals("video", ignoreCase = true)

    var isPausedByUser by remember { mutableStateOf(false) }
    // The seen-by sheet holds the story: playback, the progress timer and the
    // auto-advance all freeze while the author is reading their viewer list —
    // otherwise the story would run on and swap out underneath the sheet.
    val isPaused = isPausedByUser || !interactionEnabled
    var isMuted by remember { mutableStateOf(true) }
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Mark the story seen as it becomes current (persists + counts the view
    // unless this staff member is the author — handled in the VM). Require a
    // brief dwell so merely swiping PAST a story doesn't count as a view; if
    // the page changes before the delay elapses the effect is cancelled.
    LaunchedEffect(story.storyId, isCurrentPage) {
        com.schoolsync.teacher.util.debugLog(
            "Story.viewer PAGE author=${group.authorId} story=${story.storyId} isCurrent=$isCurrentPage"
        )
        if (isCurrentPage) {
            kotlinx.coroutines.delay(500)
            com.schoolsync.teacher.util.debugLog("Story.viewer DWELL fired story=${story.storyId}")
            onSeen(story.storyId, group.authorId)
        }
    }

    // ── Progress state ─────────────────────────────────────────────
    // Image: elapsed-ms timer that starts once the image is DISPLAYED
    // and pauses while zoomed. Video: driven by real playback position.
    var imageDisplayed by remember(index, group.authorId) { mutableStateOf(false) }
    // Load failure must be tracked separately. imageDisplayed was only ever set
    // in onSuccess and there was no onError, so a 404 / expired token left the
    // viewer on an infinite spinner: the progress driver below waits on
    // imageDisplayed, so the timer never started and the story never advanced.
    var imageFailed by remember(index, group.authorId) { mutableStateOf(false) }

    var imageElapsed by remember(index, group.authorId) { mutableLongStateOf(0L) }
    var videoProgress by remember(index, group.authorId) { mutableFloatStateOf(0f) }

    // Pinch-to-zoom for ALL media (image AND video). Two-finger pinch
    // scales/pans the media and springs back to normal when the fingers
    // lift — the same feel as a WhatsApp status.
    val mediaZoom = remember(index, group.authorId) { androidx.compose.animation.core.Animatable(1f) }
    val mediaOffsetX = remember(index, group.authorId) { androidx.compose.animation.core.Animatable(0f) }
    val mediaOffsetY = remember(index, group.authorId) { androidx.compose.animation.core.Animatable(0f) }
    val mediaZoomed = mediaZoom.value > 1.01f

    // ── Dismiss progress (0 → 1) from EITHER a downward swipe OR a pinch-
    //    out below 1×. Drives the fade/shrink of the story + backdrop fade,
    //    and (via isDismissing) freezes the timer + hides the bar so it
    //    doesn't run or reappear mid-swipe. ─────────────────────────────
    val dragProgress = (dragOffset.value / DISMISS_DISTANCE_PX).coerceIn(0f, 1f)
    val zoomOutProgress = ((1f - mediaZoom.value) / (1f - MIN_ZOOM_OUT)).coerceIn(0f, 1f)
    val dismissProgress = maxOf(dragProgress, zoomOutProgress)
    val isDismissing = dismissProgress > 0.01f
    // Only the downward drag shrinks the whole card; a pinch-out already
    // shrinks the media via mediaZoom, so we don't double-scale there.
    val cardScale = 1f - dragProgress * 0.25f

    // Chrome (progress/time bar + header) visibility. On hold-to-pause we
    // DON'T yank the bar away instantly — we keep it for a beat (1s) then
    // fade. Zooming or dismissing hides it immediately (handled at the
    // AnimatedVisibility below); releasing the hold brings it back at once.
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isPaused, mediaZoomed) {
        when {
            mediaZoomed -> chromeVisible = false
            !isPaused -> chromeVisible = true
            else -> {
                chromeVisible = true
                kotlinx.coroutines.delay(1000)
                chromeVisible = false
            }
        }
    }

    // Image progress driver — also frozen while dismissing so the timer
    // doesn't advance (or appear to restart) during a swipe-down.
    LaunchedEffect(index, isCurrentPage, isPaused, isVideo, mediaZoomed, imageDisplayed, imageFailed, isDismissing) {
        if (isVideo || !isCurrentPage || isPaused || mediaZoomed || isDismissing) return@LaunchedEffect
        // A failed image still has to advance — otherwise the viewer hangs on it
        // forever. Give it a moment so the error message can be read, then move on.
        if (imageFailed) {
            kotlinx.coroutines.delay(2500)
            if (index < stories.size - 1) index++ else onGroupFinished()
            return@LaunchedEffect
        }
        if (!imageDisplayed) return@LaunchedEffect   // wait until visible (start-on-load)
        var last = 0L
        while (imageElapsed < IMAGE_DURATION_MS) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val delta = if (last == 0L) 0L else (now - last) / 1_000_000L
            last = now
            imageElapsed += delta.coerceIn(0L, 64L)
        }
        // The timer ran out with the image on screen — genuinely watched, not
        // skipped past. Recorded BEFORE advancing so the story id is still the
        // one that finished. Note the imageFailed path above deliberately does
        // NOT call this: auto-advancing off an error is not a completion.
        onCompleted(story.storyId, group.authorId)
        if (index < stories.size - 1) index++ else onGroupFinished()
    }

    val currentProgress = if (isVideo) videoProgress
    else (imageElapsed.toFloat() / IMAGE_DURATION_MS).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        // Backdrop — fades out as the story is dismissed, revealing behind.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - dismissProgress))
        )
        // Story card — moves with the finger, shrinks + fades on dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value.coerceAtLeast(0f)
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = 1f - dismissProgress
                }
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()

            // ── Media (image OR video) with a SHARED pinch-zoom transform.
            //    One code path so every gesture behaves identically on both
            //    media types. ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = mediaZoom.value
                        scaleY = mediaZoom.value
                        translationX = mediaOffsetX.value
                        translationY = mediaOffsetY.value
                    }
            ) {
                if (isVideo) {
                    // Poster UNDER the player: a story video opens on a real
                    // frame instead of a black rectangle while the first bytes
                    // arrive. The player draws over it as soon as it renders,
                    // so there is nothing to hide or time — the same instant
                    // feel Instagram and WhatsApp get. Absent poster ⇒ nothing
                    // painted ⇒ identical to the old behaviour.
                    if (story.thumbnailUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = story.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    VideoStoryPlayer(
                        url = story.mediaUrl,
                        isCurrentPage = isCurrentPage,
                        // Pause playback while the user holds, pinch-zooms,
                        // or is swiping the story away.
                        isPaused = isPaused || mediaZoomed || isDismissing,
                        isMuted = isMuted,
                        onProgress = { videoProgress = it },
                        onEnded = {
                            // Reached the last frame — a real completion, unlike
                            // a tap-forward. Recorded before the index moves.
                            onCompleted(story.storyId, group.authorId)
                            if (index < stories.size - 1) index++ else onGroupFinished()
                        }
                    )
                } else {
                    coil.compose.AsyncImage(
                        model = story.mediaUrl,
                        contentDescription = story.caption.ifBlank { storyByFallback },
                        contentScale = ContentScale.Fit,
                        onSuccess = { imageDisplayed = true },
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (!isVideo && imageFailed) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.story_load_error), color = Color.White)
                }
            } else if (!isVideo && !imageDisplayed) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                }
            }

            // ── SHARED gesture overlay for ALL media ───────────────────
            // pinch-zoom (2 fingers) · tap zones (prev/next) · hold-to-pause
            // · swipe-down dismiss · horizontal swipe falls through to the
            // HorizontalPager (→ previous/next person, WhatsApp-style).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Pinch-zoom FIRST so it claims multi-touch before the
                    // single-finger drag/tap detectors below. Only acts on 2+
                    // pointers, leaving 1-finger gestures intact.
                    .pointerInput(index, group.authorId, interactionEnabled) {
                        if (!interactionEnabled) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var didZoom = false
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    didZoom = true
                                    // Allow shrinking below 1× (down to
                                    // MIN_ZOOM_OUT) so a pinch-OUT becomes a
                                    // dismiss gesture, as well as zoom-IN to 4×.
                                    val newScale = (mediaZoom.value * event.calculateZoom()).coerceIn(MIN_ZOOM_OUT, 4f)
                                    val pan = event.calculatePan()
                                    // Panning only makes sense when zoomed IN.
                                    // Clamp bounds to >=0 so a <1× scale can't
                                    // produce an inverted coerceIn range (crash).
                                    val maxX = (widthPx * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    val maxY = (constraints.maxHeight.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    scope.launch { mediaZoom.snapTo(newScale) }
                                    scope.launch { mediaOffsetX.snapTo((mediaOffsetX.value + pan.x).coerceIn(-maxX, maxX)) }
                                    scope.launch { mediaOffsetY.snapTo((mediaOffsetY.value + pan.y).coerceIn(-maxY, maxY)) }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (didZoom) {
                                if (mediaZoom.value < DISMISS_ZOOM_OUT) {
                                    // Pinched out far enough → dismiss (the
                                    // card is already faded/shrunk via
                                    // dismissProgress). Close the viewer.
                                    onClose()
                                } else {
                                    // Otherwise spring back to normal.
                                    scope.launch { mediaZoom.animateTo(1f, tween(200)) }
                                    scope.launch { mediaOffsetX.animateTo(0f, tween(200)) }
                                    scope.launch { mediaOffsetY.animateTo(0f, tween(200)) }
                                }
                            }
                        }
                    }
                    .pointerInput(interactionEnabled) {
                        if (!interactionEnabled) return@pointerInput
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dy ->
                                scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f)) }
                            },
                            onDragEnd = {
                                if (dragOffset.value > DISMISS_THRESHOLD_PX) onClose()
                                else scope.launch { dragOffset.animateTo(0f, tween(180)) }
                            },
                            onDragCancel = { scope.launch { dragOffset.animateTo(0f, tween(180)) } }
                        )
                    }
                    // Tap zones + hold-to-pause, implemented WITHOUT consuming
                    // events. detectTapGestures' onLongPress swallows the
                    // pointer until release, which blocked a swipe-down that
                    // began during/after a hold. This await-based version never
                    // consumes, so the vertical-drag detector above still drives
                    // the dismiss, and horizontal swipes still reach the pager.
                    .pointerInput(index, stories.size, interactionEnabled) {
                        if (!interactionEnabled) return@pointerInput
                        val longPressMs = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPausedByUser = true
                            // Quick release with no drag → a tap → navigate.
                            val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
                            if (up != null) {
                                isPausedByUser = false
                                if (down.position.x < widthPx / 3f) {
                                    if (index > 0) index--
                                } else {
                                    if (index < stories.size - 1) index++ else onGroupFinished()
                                }
                            } else {
                                // Held past the tap window, OR a drag started
                                // (waitForUpOrCancellation returns null when the
                                // drag detector consumes the move). Stay paused,
                                // wait for release, DON'T consume — so the
                                // swipe-down/horizontal-swipe is free to run. No
                                // navigation on a plain hold-release.
                                waitForUpOrCancellation()
                                isPausedByUser = false
                            }
                        }
                    }
            )
        }

        // ── Chrome — stays through a hold for 4s, then fades; hides at
        //    once while zooming (see chromeVisible above). ────────────
        AnimatedVisibility(
            visible = chromeVisible && !isDismissing,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopChrome(
                storyCount = stories.size,
                currentIndex = index,
                currentProgress = currentProgress,
                isCurrentPage = isCurrentPage,
                authorName = group.authorName,
                authorPic = group.authorPic,
                createdAt = story.createdAt,
                isVideo = isVideo,
                isMuted = isMuted,
                viewCount = story.viewCount,
                showViewCount = isAuthor,
                reactionCounts = story.reactionCounts,
                canDelete = canDelete,
                onOpenInsights = { onOpenSeenBy(story.storyId) },
                onDeleteStory = { onDeleteStory(story.storyId) },
                onToggleMute = { isMuted = !isMuted },
                onClose = onClose
            )
        }

        // ── Author-only "N views" pill, bottom-left — the affordance that
        //    opens the live seen-by list. The chrome previously showed the
        //    author a bare number with nothing to tap, so the only route to
        //    the viewer list was the separate My Stories screen; Instagram
        //    and WhatsApp both surface it right here.
        if (isAuthor) {
            AnimatedVisibility(
                visible = chromeVisible && !isDismissing,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                SeenByPill(
                    count = story.viewCount,
                    onClick = { onOpenSeenBy(story.storyId) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(start = 16.dp, bottom = 18.dp)
                )
            }
        }

        if (story.caption.isNotBlank()) {
            AnimatedVisibility(
                visible = chromeVisible && !isDismissing,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // WhatsApp-style caption: centered, sitting just above the
                // bottom edge (lifted clear of the reaction bar) over a soft
                // bottom-up scrim for legibility on any media.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 24.dp)
                        .padding(top = 48.dp, bottom = 56.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = story.caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        } // end story card
    } // end page root
}

@Composable
private fun TopChrome(
    storyCount: Int,
    currentIndex: Int,
    currentProgress: Float,
    isCurrentPage: Boolean,
    authorName: String,
    authorPic: String,
    createdAt: Long,
    isVideo: Boolean,
    isMuted: Boolean,
    viewCount: Int,
    /** Only the author sees the view count — and the ⋮ actions. */
    showViewCount: Boolean,
    reactionCounts: Map<String, Int>,
    /** Stories is baseline-at-view, so DELETE is gated at manage level. */
    canDelete: Boolean,
    onOpenInsights: () -> Unit,
    onDeleteStory: () -> Unit,
    onToggleMute: () -> Unit,
    onClose: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Hoisted: Modifier.semantics {} is not a composable scope.
            val storyIndexCd = stringResource(R.string.story_index_of, currentIndex + 1, storyCount)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Collapse the N decorative segment bars into ONE spoken
                    // summary for TalkBack instead of reading each bar's raw
                    // progress percentage.
                    .clearAndSetSemantics {
                        contentDescription = storyIndexCd
                    },
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(storyCount) { i ->
                    val barProgress = when {
                        i < currentIndex -> 1f
                        i == currentIndex && isCurrentPage -> currentProgress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (authorPic.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = authorPic,
                            contentDescription = stringResource(R.string.story_author_photo_cd, authorName),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                        )
                    } else {
                        val initials = authorName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("").ifBlank { "S" }
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(authorName, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatStoryTime(LocalContext.current, createdAt), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            val reactionTotal = reactionCounts.values.sum()
                            // View count is author-only; reaction total is shown to all.
                            val analytics = buildString {
                                if (showViewCount) append("  ·  👁 $viewCount")
                                if (reactionTotal > 0) append("  ·  ❤ $reactionTotal")
                            }
                            Text(analytics, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVideo) {
                        IconButton(onClick = onToggleMute) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) stringResource(R.string.story_unmute) else stringResource(R.string.story_mute),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // Author-only ⋮ — story-level actions moved here from the
                    // old My-Stories grid, so one story has one place to act
                    // on it rather than a card, a sheet and a screen.
                    if (showViewCount) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.story_options),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.story_insights)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Visibility, contentDescription = null)
                                    },
                                    onClick = { menuOpen = false; onOpenInsights() }
                                )
                                if (canDelete) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.story_delete)) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, contentDescription = null)
                                        },
                                        onClick = { menuOpen = false; onDeleteStory() }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoStoryPlayer(
    url: String,
    isCurrentPage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    onProgress: (Float) -> Unit,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(url) {
        val source = ProgressiveMediaSource.Factory(StoryVideoCache.dataSourceFactory(context))
            .createMediaSource(MediaItem.fromUri(url))
        // Tuned buffering (ported from Grader_S): start playback with
        // ~1s buffered for a fast start instead of the default.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 10000, 1000, 1000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaSource(source)
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isCurrentPage, isPaused, player) {
        player.playWhenReady = isCurrentPage && !isPaused
    }
    LaunchedEffect(isMuted, player) {
        player.volume = if (isMuted) 0f else 1f
    }

    var isBuffering by remember(url) { mutableStateOf(true) }
    var videoFailed by remember(url) { mutableStateOf(false) }

    // Advance the story when the video actually ends (accurate to the
    // clip length, unlike a fixed timer).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) onEnded()
            }
            // There was NO error handler at all. On STATE_IDLE after a failure
            // onEnded never fired, the video progress stayed at 0, and the
            // segment timer is skipped for video — so the viewer sat on a black
            // screen with a frozen bar and never advanced or reported anything.
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e(
                    "StoryViewerScreen",
                    "Video playback failed (code=${error.errorCodeName}) url=$url",
                    error
                )
                videoFailed = true
                isBuffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause when the app leaves the foreground — otherwise the story keeps
    // playing (audibly) behind the lock screen and the progress bar drifts out
    // of sync with the real playback position.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Watchdog — a video stuck buffering never emits an error.
    LaunchedEffect(url, isBuffering, isCurrentPage, isPaused) {
        if (!isBuffering || !isCurrentPage || isPaused) return@LaunchedEffect
        kotlinx.coroutines.delay(VIDEO_LOAD_TIMEOUT_MS)
        if (isBuffering) videoFailed = true
    }

    LaunchedEffect(videoFailed) {
        if (videoFailed) {
            kotlinx.coroutines.delay(2500)
            onEnded()
        }
    }

    // Drive the progress bar from real playback position every ~50ms — but
    // ONLY while this is the current page AND playback isn't paused (M5).
    // Keying on isPaused/isCurrentPage restarts this effect and returns
    // early when the story is off-screen, held, zoomed, or dismissing, so
    // the 20Hz loop doesn't spin against a stopped player.
    LaunchedEffect(player, isCurrentPage, isPaused) {
        if (!isCurrentPage || isPaused) return@LaunchedEffect
        while (true) {
            val dur = player.duration
            if (dur > 0) {
                onProgress((player.currentPosition.toFloat() / dur).coerceIn(0f, 1f))
            }
            kotlinx.coroutines.delay(50)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        // Rebind on player change: the slot is reused across url changes, so
        // without this PlayerView keeps pointing at the RELEASED player and the
        // new one never gets a surface (black frame, audio only).
        update = { view -> if (view.player !== player) view.player = player },
        modifier = Modifier.fillMaxSize()
    )

    if (videoFailed) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.story_load_error), color = Color.White)
        }
    } else if (isBuffering) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
        }
    }
}

private fun formatStoryTime(ctx: android.content.Context, timestamp: Long): String {
    if (timestamp <= 0) return ""
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    return when {
        minutes < 1 -> ctx.getString(R.string.story_just_now)
        minutes < 60 -> ctx.getString(R.string.story_minutes_ago, minutes.toInt())
        hours < 24 -> ctx.getString(R.string.story_hours_ago, hours.toInt())
        else -> try { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp)) } catch (_: Exception) { "" }
    }
}
