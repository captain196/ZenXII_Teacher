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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val IMAGE_DURATION_MS = 6000L
private const val LONG_PRESS_TIMEOUT_MS = 180L
private const val DISMISS_THRESHOLD_PX = 250f

/**
 * Full-screen story viewer for the teacher app — the upgraded Compose
 * viewer that ports the smoothness/accuracy/zoom techniques from the
 * Grader_S reference into Compose:
 *   • video progress tied to actual playback (position/duration) and
 *     advance on STATE_ENDED — not a flat timer
 *   • shared disk-cached + tuned-buffering ExoPlayer (StoryVideoCache)
 *   • pinch / double-tap zoom on images (Telephoto); zooming pauses
 *     the progress timer
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
        onClose = onClose,
        onSeen = viewModel::markSeen
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryViewerPager(
    groups: List<StoryGroup>,
    initialAuthorId: String,
    onClose: () -> Unit,
    onSeen: (String) -> Unit
) {
    val initialPage = groups.indexOfFirst { it.authorId == initialAuthorId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { groups.size }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            AuthorStoryPage(
                group = groups[pageIndex],
                isCurrentPage = pagerState.currentPage == pageIndex,
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
    onClose: () -> Unit,
    onSeen: (String) -> Unit,
    onGroupFinished: () -> Unit
) {
    val stories = group.stories
    if (stories.isEmpty()) return

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

    // Mark the story seen as it becomes current.
    LaunchedEffect(story.storyId, isCurrentPage) {
        if (isCurrentPage) onSeen(story.storyId)
    }

    // ── Progress state ─────────────────────────────────────────────
    // Image: elapsed-ms timer that starts once the image is DISPLAYED
    // and pauses while zoomed. Video: driven by real playback position.
    val imageState = rememberZoomableImageState(rememberZoomableState())
    val isZoomed = (imageState.zoomableState.zoomFraction ?: 0f) > 0.01f

    var imageElapsed by remember(index, group.authorId) { mutableLongStateOf(0L) }
    var videoProgress by remember(index, group.authorId) { mutableFloatStateOf(0f) }

    // Image progress driver.
    LaunchedEffect(index, isCurrentPage, isPaused, isVideo, isZoomed, imageState.isImageDisplayed) {
        if (isVideo || !isCurrentPage || isPaused || isZoomed) return@LaunchedEffect
        if (!imageState.isImageDisplayed) return@LaunchedEffect   // wait until visible (start-on-load)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset.value.coerceAtLeast(0f) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()

            if (isVideo) {
                VideoStoryPlayer(
                    url = story.mediaUrl,
                    isCurrentPage = isCurrentPage,
                    isPaused = isPaused,
                    isMuted = isMuted,
                    onProgress = { videoProgress = it },
                    onEnded = { if (index < stories.size - 1) index++ else onGroupFinished() }
                )
                // Gesture overlay for video: tap zones, hold-to-pause, swipe-down dismiss.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                        .pointerInput(index, stories.size) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                                val up = withTimeoutOrNull(LONG_PRESS_TIMEOUT_MS) { waitForUpOrCancellation() }
                                if (up != null) {
                                    if (down.position.x < widthPx / 3f) {
                                        if (index > 0) index--
                                    } else {
                                        if (index < stories.size - 1) index++ else onGroupFinished()
                                    }
                                } else {
                                    isPaused = true
                                    try { waitForUpOrCancellation() } finally { isPaused = false }
                                }
                            }
                        }
                )
            } else {
                // Image: Telephoto handles pinch/double-tap/pan. onClick
                // (fires only when NOT zooming/panning) drives tap-nav.
                ZoomableAsyncImage(
                    model = story.mediaUrl,
                    contentDescription = null,
                    state = imageState,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { offset ->
                        if (offset.x < widthPx / 3f) {
                            if (index > 0) index--
                        } else {
                            if (index < stories.size - 1) index++ else onGroupFinished()
                        }
                    }
                )
                if (!imageState.isImageDisplayed) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }

        // ── Chrome — fades while paused/zoomed ─────────────────────
        AnimatedVisibility(
            visible = !isPaused && !isZoomed,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
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
                reactionCounts = story.reactionCounts,
                onToggleMute = { isMuted = !isMuted },
                onClose = onClose
            )
        }

        if (story.caption.isNotBlank()) {
            AnimatedVisibility(
                visible = !isPaused && !isZoomed,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
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
    }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
                            contentDescription = null,
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
                            val analytics = buildString {
                                append("  ·  👁 $viewCount")
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

    // Drive the progress bar from real playback position every ~50ms.
    LaunchedEffect(player, isCurrentPage) {
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
