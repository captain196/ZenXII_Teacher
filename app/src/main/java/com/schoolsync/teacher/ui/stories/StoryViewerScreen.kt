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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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

private const val IMAGE_DURATION_MS = 6000L
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
    onClose: () -> Unit,
    viewModel: StoryViewerViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

    if (groups.isEmpty()) {
        // Still loading (or genuinely empty). Show black; user can back out.
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
        }
        return
    }

    StoryViewerPager(
        groups = groups,
        initialAuthorId = initialAuthorId,
        currentUserId = currentUserId,
        onClose = onClose,
        onSeen = viewModel::markSeen
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryViewerPager(
    groups: List<StoryGroup>,
    initialAuthorId: String,
    currentUserId: String,
    onClose: () -> Unit,
    onSeen: (String, String) -> Unit
) {
    val initialPage = groups.indexOfFirst { it.authorId == initialAuthorId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { groups.size }
    val scope = rememberCoroutineScope()

    // No opaque background here — each page paints its own black backdrop
    // whose alpha fades during a swipe-down / pinch-out dismiss, so the
    // story appears to fall away and reveal what's behind it.
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            AuthorStoryPage(
                group = groups[pageIndex],
                isCurrentPage = pagerState.currentPage == pageIndex,
                currentUserId = currentUserId,
                onClose = onClose,
                onSeen = onSeen,
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
    group: StoryGroup,
    isCurrentPage: Boolean,
    currentUserId: String,
    onClose: () -> Unit,
    onSeen: (String, String) -> Unit,
    onGroupFinished: () -> Unit
) {
    val stories = group.stories
    if (stories.isEmpty()) return
    // Only the story's own author (the uploader) sees the view count in the
    // viewer chrome; everyone else's chrome hides it. Full "who viewed" list
    // lives in the author's My-Stories insights + the admin panel.
    val isAuthor = currentUserId.isNotBlank() && group.authorId == currentUserId

    // Open on first unseen story (WhatsApp/Instagram behaviour).
    var index by remember(group.authorId) {
        val firstUnseen = stories.indexOfFirst { !it.isViewed }
        mutableIntStateOf(if (firstUnseen >= 0) firstUnseen else 0)
    }
    val story = stories.getOrNull(index) ?: return
    val isVideo = story.type.equals("video", ignoreCase = true)

    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Mark the story seen as it becomes current (persists + counts the view
    // unless this staff member is the author — handled in the VM). Require a
    // brief dwell so merely swiping PAST a story doesn't count as a view; if
    // the page changes before the delay elapses the effect is cancelled.
    LaunchedEffect(story.storyId, isCurrentPage) {
        if (isCurrentPage) {
            kotlinx.coroutines.delay(500)
            onSeen(story.storyId, group.authorId)
        }
    }

    // ── Progress state ─────────────────────────────────────────────
    // Image: elapsed-ms timer that starts once the image is DISPLAYED
    // and pauses while zoomed. Video: driven by real playback position.
    var imageDisplayed by remember(index, group.authorId) { mutableStateOf(false) }

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
    LaunchedEffect(index, isCurrentPage, isPaused, isVideo, mediaZoomed, imageDisplayed, isDismissing) {
        if (isVideo || !isCurrentPage || isPaused || mediaZoomed || isDismissing) return@LaunchedEffect
        if (!imageDisplayed) return@LaunchedEffect   // wait until visible (start-on-load)
        var last = 0L
        while (imageElapsed < IMAGE_DURATION_MS) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val delta = if (last == 0L) 0L else (now - last) / 1_000_000L
            last = now
            imageElapsed += delta.coerceIn(0L, 64L)
        }
        if (index < stories.size - 1) index++ else onGroupFinished()
    }

    val currentProgress = if (isVideo) videoProgress
    else (imageElapsed.toFloat() / IMAGE_DURATION_MS).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
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
                    VideoStoryPlayer(
                        url = story.mediaUrl,
                        isCurrentPage = isCurrentPage,
                        // Pause playback while the user holds, pinch-zooms,
                        // or is swiping the story away.
                        isPaused = isPaused || mediaZoomed || isDismissing,
                        isMuted = isMuted,
                        onProgress = { videoProgress = it },
                        onEnded = { if (index < stories.size - 1) index++ else onGroupFinished() }
                    )
                } else {
                    coil.compose.AsyncImage(
                        model = story.mediaUrl,
                        contentDescription = story.caption.ifBlank { "Story by ${group.authorName}" },
                        contentScale = ContentScale.Fit,
                        onSuccess = { imageDisplayed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (!isVideo && !imageDisplayed) {
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
                    .pointerInput(index, group.authorId) {
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
                    .pointerInput(Unit) {
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
                    .pointerInput(index, stories.size) {
                        val longPressMs = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPaused = true
                            // Quick release with no drag → a tap → navigate.
                            val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
                            if (up != null) {
                                isPaused = false
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
                                isPaused = false
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
                onToggleMute = { isMuted = !isMuted },
                onClose = onClose
            )
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
    /** Only the author sees the view count; hidden for everyone else. */
    showViewCount: Boolean,
    reactionCounts: Map<String, Int>,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Collapse the N decorative segment bars into ONE spoken
                    // summary for TalkBack instead of reading each bar's raw
                    // progress percentage.
                    .clearAndSetSemantics {
                        contentDescription = "Story ${currentIndex + 1} of $storyCount"
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
                            contentDescription = "$authorName profile photo",
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
                            Text(formatStoryTime(createdAt), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
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
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
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

    // Advance the story when the video actually ends (accurate to the
    // clip length, unlike a fixed timer).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
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
        modifier = Modifier.fillMaxSize()
    )
}

private fun formatStoryTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> try { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp)) } catch (_: Exception) { "" }
    }
}
