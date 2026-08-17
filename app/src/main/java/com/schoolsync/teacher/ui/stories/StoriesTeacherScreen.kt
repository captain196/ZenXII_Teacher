package com.schoolsync.teacher.ui.stories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.teacher.data.model.Story
import com.schoolsync.teacher.data.model.firestore.StorySharedConfig
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.glassCard
import com.schoolsync.teacher.util.StoryVideoCache
import com.schoolsync.teacher.util.toRelativeTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun StoriesTeacherScreen(
    /** (authorId, storyId?) — storyId names a specific story to open on. */
    onOpenViewer: (authorId: String, storyId: String?) -> Unit = { _, _ -> },
    canEdit: Boolean = true,
    /** True when we arrived here from the Dashboard's "Your story" + — open the
     *  upload dialog immediately instead of making them find it again. */
    openUploadOnEntry: Boolean = false,
    onUploadOpenConsumed: () -> Unit = {},
    viewModel: StoriesTeacherViewModel = hiltViewModel(),
    viewerViewModel: StoryViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val storyGroups by viewerViewModel.groups.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Honour a "+ from the Dashboard" request exactly once. Gated on canEdit so
    // a stale flag can never open a post dialog for a view-only grantee.
    LaunchedEffect(openUploadOnEntry, canEdit) {
        if (openUploadOnEntry) {
            if (canEdit && !state.showUploadDialog) viewModel.toggleUploadDialog()
            onUploadOpenConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StoriesEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is StoriesEvent.Error -> snackbarHostState.showSnackbar("Error: ${event.message}")
            }
        }
    }

    GradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Glass,
                        contentColor = TextPrimary,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // The tray — "Your story" first (and it's the post entry), then
                // everyone else. Own stories are SPLIT OUT of the general list:
                // they used to render both as a ring here and as a card in the
                // grid below, ~200px apart, in two visual languages.
                val myGroup = storyGroups.firstOrNull { it.authorId == state.myUserId }
                val otherGroups = storyGroups.filter { it.authorId != state.myUserId }
                StoriesSection(
                    groups = otherGroups,
                    myGroup = myGroup,
                    myName = state.myName,
                    myPic = state.myPic,
                    canPost = canEdit,
                    onOpenStory = { authorId -> onOpenViewer(authorId, null) },
                    onCreateStory = viewModel::toggleUploadDialog,
                    title = "Stories",
                    showWhenEmpty = true
                )

                // "Your stories" header + New story + Archived entries.
                StoriesTopBar(
                    archivedCount = state.archivedStories.size,
                    canPost = canEdit,
                    onNewStory = viewModel::toggleUploadDialog,
                    onOpenArchived = viewModel::openArchivedGallery
                )

                // At-a-glance grid of your own posts. Tapping opens the VIEWER
                // (where the seen-by list and the ⋮ actions live), not a
                // separate insights sheet — one story, one place to act on it.
                StoriesGridContent(
                    state = state,
                    // Open on the story that was tapped, not the author's
                    // first unseen one — the card names a specific story.
                    onCardClick = { story -> onOpenViewer(state.myUserId, story.storyId) },
                    onDelete = viewModel::deleteStory,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Upload dialog
        if (state.showUploadDialog) {
            val context = LocalContext.current
            UploadStoryDialog(
                url = state.uploadUrl,
                caption = state.uploadCaption,
                type = state.uploadType,
                isUploading = state.isUploading,
                mediaUploadPercent = state.mediaUploadPercent,
                isCompressing = state.isCompressing,
                posterPending = state.posterPending,
                pickedLocalUri = state.pickedLocalUri,
                audienceOptions = state.audienceOptions,
                selectedAudience = state.selectedAudience,
                onToggleAudience = viewModel::toggleAudience,
                onSelectWholeSchool = viewModel::selectWholeSchool,
                onUrlChange = viewModel::setUploadUrl,
                onCaptionChange = viewModel::setUploadCaption,
                onTypeChange = viewModel::setUploadType,
                onPickMedia = { uri -> viewModel.pickAndUploadMedia(context, uri) },
                onClearPickedMedia = viewModel::clearPickedMedia,
                onUpload = viewModel::uploadStory,
                onDismiss = viewModel::toggleUploadDialog
            )
        }

        // Error dialog
        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("Error", color = TextPrimary) },
                text = { Text(error, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) {
                        Text("OK", color = Teal)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Archived gallery — full-screen grid of expired stories.
        if (state.showArchivedGallery) {
            ArchivedGalleryOverlay(
                stories = state.archivedStories,
                viewCounts = state.viewCounts,
                onOpen = viewModel::openArchivedViewer,
                onClose = viewModel::closeArchivedGallery
            )
        }

        // Archived full-screen gallery viewer — swipe between expired stories.
        state.archivedViewerIndex?.let { idx ->
            ArchivedStoryViewer(
                stories = state.archivedStories,
                initialIndex = idx,
                onClose = viewModel::closeArchivedViewer
            )
        }
    }
}

@Composable
private fun StoriesTopBar(
    archivedCount: Int,
    canPost: Boolean,
    onNewStory: () -> Unit,
    onOpenArchived: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Your stories",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Full-size "New story" action. The tray tile's `+` badge is the
            // primary entry now that the FAB is gone, but that badge is ~22dp —
            // too small to be the ONLY way to post. This keeps a comfortable
            // target without reintroducing a floating button over the grid.
            if (canPost) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Teal)
                        .clickable { onNewStory() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = BgStart, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        "New story",
                        style = MaterialTheme.typography.labelLarge,
                        color = BgStart,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Archived pill — opens the full-screen gallery of expired stories.
            if (archivedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Glass)
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .clickable { onOpenArchived() }
                        .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
                ) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Archived", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Teal)
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "$archivedCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = BgStart,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoriesGridContent(
    state: StoriesUiState,
    onCardClick: (Story) -> Unit,
    onDelete: (Story) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasArchived = state.archivedStories.isNotEmpty()
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Loading stories...",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else if (state.myStories.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (hasArchived) "No active stories" else "No stories yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    text = if (hasArchived) "Your past stories are in Archived above."
                           else "Tap + to upload your first story",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // Bottom padding clears the "New Story" FAB so the last row
            // is never hidden behind it.
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(state.myStories, key = { it.storyId }) { story ->
                StoryCard(
                    story = story,
                    viewCount = state.viewCounts[story.storyId] ?: 0,
                    onClick = { onCardClick(story) },
                    onDelete = { onDelete(story) }
                )
            }
        }
    }
}

@Composable
private fun StoryCard(
    story: Story,
    viewCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpired = story.isExpired
    val cardAlpha = if (isExpired) 0.5f else 1f
    val reactionTotal = story.reactionCounts.values.sum()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glass.copy(alpha = if (isExpired) 0.3f else 1f))
            .border(
                1.dp,
                if (isExpired) ErrorRed.copy(alpha = 0.3f) else GlassBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(SurfaceDark)
        ) {
            // For a video, the tile must show the POSTER, never the video URL.
            // This used to pass story.mediaUrl straight to Coil for both types;
            // Coil's BitmapFactoryDecoder cannot decode an .mp4, so every video
            // story rendered as a permanently blank box (the Videocam fallback
            // below only fires when mediaUrl is empty, which it never is).
            // Deliberately NOT using VideoFrameDecoder here: it needs a local
            // file, so it would download the whole video to draw a thumbnail.
            val isVideoStory = story.type.equals("video", ignoreCase = true)
            val tileUrl = storyTileUrl(story)
            var tileFailed by remember(tileUrl) { mutableStateOf(false) }

            if (tileUrl.isNotBlank() && !tileFailed) {
                AsyncImage(
                    model = tileUrl,
                    contentDescription = story.caption.ifBlank { "Story" },
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (isExpired) it.background(Color.Black.copy(alpha = 0.4f)) else it },
                    contentScale = ContentScale.Crop,
                    onError = { tileFailed = true }
                )
            } else {
                // No poster (older video story, or poster extraction failed) or
                // the poster 404'd — show the type glyph rather than a blank box.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isVideoStory) Icons.Filled.Videocam else Icons.Filled.Image,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Type badge (top-left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Glass)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (story.type == "video") Icons.Filled.Videocam else Icons.Filled.Image,
                        contentDescription = null,
                        tint = TextPrimary.copy(alpha = cardAlpha),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = story.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary.copy(alpha = cardAlpha),
                        fontSize = 9.sp
                    )
                }
            }

            // Expired label (top-right)
            if (isExpired) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ErrorRed.copy(alpha = 0.8f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Expired",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // View count badge (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Glass)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = TextPrimary.copy(alpha = cardAlpha),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = viewCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary.copy(alpha = cardAlpha),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Footer — caption, reactions summary, insights cue + delete.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
        ) {
            if (story.caption.isNotBlank()) {
                Text(
                    text = story.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary.copy(alpha = cardAlpha),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (story.createdAt > 0) {
                        Text(
                            text = story.createdAt.toRelativeTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary.copy(alpha = cardAlpha),
                            fontSize = 10.sp
                        )
                    }
                    if (reactionTotal > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = story.reactionCounts.entries
                                .filter { it.value > 0 }
                                .sortedByDescending { it.value }
                                .take(3)
                                .joinToString("") { it.key },
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = reactionTotal.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary.copy(alpha = cardAlpha),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                }
                // ≥48dp touch target (a11y) while keeping the small glyph.
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete story",
                        tint = ErrorRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = Teal.copy(alpha = 0.9f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tap to see who viewed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadStoryDialog(
    url: String,
    caption: String,
    type: String,
    isUploading: Boolean,
    /** -1 = idle, 0..99 = transcode/upload in flight, 100 = ready. */
    mediaUploadPercent: Int,
    /** True while a video is transcoding on-device (before upload). */
    isCompressing: Boolean = false,
    /** True while a picked video's poster frame is still being produced. */
    posterPending: Boolean = false,
    /** Local content:// Uri of picked media for the live preview. */
    pickedLocalUri: String,
    audienceOptions: List<AudienceOption>,
    selectedAudience: Set<String>,
    onToggleAudience: (String) -> Unit,
    onSelectWholeSchool: () -> Unit,
    onUrlChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onPickMedia: (android.net.Uri) -> Unit,
    onClearPickedMedia: () -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit
) {
    // Modern photo / video picker — no runtime permission required on
    // any Android version; Photos UI is system-provided and respects
    // user privacy. Constrained to image-or-video by `type` chip.
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickMedia(uri)
    }
    // One picker for both — the media type is auto-detected from the
    // chosen file's MIME in the ViewModel, so the user never pre-selects.
    val pickerMime = ActivityResultContracts.PickVisualMedia.ImageAndVideo
    val isPicking = mediaUploadPercent in 0..99
    val hasPickedMedia = mediaUploadPercent == 100 && url.isNotBlank()
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Teal,
        focusedBorderColor = Teal,
        unfocusedBorderColor = GlassBorder,
        focusedLabelColor = Teal,
        unfocusedLabelColor = TextSecondary
    )

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Story", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            // Landscape two-column layout: media on the LEFT, the
            // audience + caption controls on the RIGHT so the "Who can
            // see this?" choice is ALWAYS visible without scrolling past
            // the tall media card (the app is locked to landscape).
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Column(modifier = Modifier.weight(1f)) {

                // ── HERO MEDIA CARD ───────────────────────────────
                // One tappable surface: an inviting call-to-action when
                // empty, the live preview (with progress + remove) once
                // media is picked. A single tap opens the system photo
                // picker; the media type is auto-detected from the file.
                val previewModel = pickedLocalUri.ifBlank { url }
                val hasPreview = previewModel.isNotBlank()
                val heroContext = LocalContext.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (hasPreview) SurfaceDark else TealSurface)
                        .border(
                            width = 1.5.dp,
                            color = if (hasPreview) GlassBorder else Teal.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = !isUploading && !isPicking) {
                            mediaPicker.launch(PickVisualMediaRequest(pickerMime))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (hasPreview) {
                        val request = coil.request.ImageRequest.Builder(heroContext)
                            .data(previewModel)
                            .apply {
                                if (type == "video") decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                            }
                            .crossfade(true)
                            .build()
                        AsyncImage(
                            model = request,
                            contentDescription = "Selected media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (type == "video") {
                            Icon(
                                Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .size(20.dp)
                            )
                        }
                        if (!isPicking && hasPickedMedia) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { onClearPickedMedia() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove media",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (!isPicking) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                        )
                                    )
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Tap to replace",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (isPicking || posterPending) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (posterPending) {
                                        // Indeterminate: poster extraction has no
                                        // meaningful percentage, and showing a
                                        // frozen 100% bar would read as "done".
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            progress = { mediaUploadPercent / 100f },
                                            color = Color.White,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        when {
                                            posterPending -> "Preparing preview…"
                                            isCompressing -> "Compressing… $mediaUploadPercent%"
                                            else -> "Uploading… $mediaUploadPercent%"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Teal.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Teal,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Add photo or video",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Tap to choose from your gallery",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
              } // end LEFT (media) column

              // ── RIGHT COLUMN: audience + caption (scroll-safe) ──
              Column(
                  modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight()
                      .verticalScroll(rememberScrollState()),
                  verticalArrangement = Arrangement.spacedBy(14.dp)
              ) {

                // ── AUDIENCE PICKER (prominent) ───────────────────
                // Explicit, always-visible visibility choice. "Whole
                // school" = empty target set (all parents). Otherwise
                // scoped to the selected class-sections. Defaults to the
                // teacher's class-teacher section(s). The "Whole school"
                // option is always shown so the picker is never blank.
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Who can see this story?",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val audienceChipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealSurface,
                        selectedLabelColor = Teal,
                        selectedLeadingIconColor = Teal,
                        containerColor = Color.Transparent,
                        labelColor = TextSecondary,
                        iconColor = TextSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val wholeSchool = selectedAudience.isEmpty()
                        FilterChip(
                            selected = wholeSchool,
                            onClick = onSelectWholeSchool,
                            label = { Text("Whole school") },
                            leadingIcon = {
                                Icon(
                                    if (wholeSchool) Icons.Filled.Check else Icons.Filled.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = audienceChipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = GlassBorder,
                                selectedBorderColor = Teal.copy(alpha = 0.4f),
                                enabled = true,
                                selected = wholeSchool
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        audienceOptions.forEach { option ->
                            val isSel = option.token in selectedAudience
                            FilterChip(
                                selected = isSel,
                                onClick = { onToggleAudience(option.token) },
                                label = { Text(option.label) },
                                leadingIcon = {
                                    Icon(
                                        if (isSel) Icons.Filled.Check else Icons.Filled.Groups,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = audienceChipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = GlassBorder,
                                    selectedBorderColor = Teal.copy(alpha = 0.4f),
                                    enabled = true,
                                    selected = isSel
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedAudience.isEmpty())
                            "Everyone in your school will see this story."
                        else if (audienceOptions.isEmpty())
                            "You have no assigned class-sections yet — this will post to the whole school."
                        else
                            "Only parents of the selected class-section(s) will see this story.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    label = { Text("Caption") },
                    placeholder = { Text("Write a caption…  (optional)", color = TextTertiary) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )

              } // end RIGHT column
            } // end two-column Row
        },
        confirmButton = {
            Button(
                onClick = onUpload,
                // posterPending is the fix for the race that shipped a
                // posterless video: the media upload completes (enabling this
                // button) a few seconds BEFORE the poster frame finishes
                // uploading, and a story written in that window carries an
                // empty thumbnailUrl — a permanently blank tile everywhere.
                enabled = !isUploading && !isPicking && url.isNotBlank() && !posterPending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = BgStart
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isUploading || posterPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = BgStart,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    when {
                        isUploading -> "Sharing…"
                        posterPending -> "Preparing preview…"
                        else -> "Share Story"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
        // Landscape: opt out of the narrow platform default width so the
        // two-column (media | audience+caption) layout has room to breathe.
        modifier = Modifier.fillMaxWidth(0.82f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun StoryPosterTile(
    story: Story,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp
) {
    val isVideo = story.type.equals("video", ignoreCase = true)
    val tileUrl = storyTileUrl(story)
    var failed by remember(tileUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Glass),
        contentAlignment = Alignment.Center
    ) {
        if (tileUrl.isNotBlank() && !failed) {
            // Poster URL, not the video. VideoFrameDecoder needs a LOCAL file,
            // so pointing Coil at a remote .mp4 made it buffer the entire clip
            // to disk just to draw this tile — routinely timing out blank.
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(tileUrl)
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else {
            Icon(
                if (isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(size * 0.45f)
            )
        }
        // Play affordance so a video still reads as a video even when the
        // poster loaded (matching the archived grid's treatment).
        if (isVideo && tileUrl.isNotBlank() && !failed) {
            Box(
                modifier = Modifier
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.3f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Archived gallery — full-screen grid of expired stories
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ArchivedGalleryOverlay(
    stories: List<Story>,
    viewCounts: Map<String, Int>,
    onOpen: (Story) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    // In-composition full-screen overlay (NOT a Dialog) so it fits the
    // edge-to-edge landscape window; safeDrawingPadding keeps everything
    // clear of the status bar, nav bar and side camera cutout.
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Timer, contentDescription = null, tint = Teal, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Archived",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${stories.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTertiary
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(stories, key = { "gal_${it.storyId}" }) { story ->
                    ArchivedThumb(
                        story = story,
                        viewCount = viewCounts[story.storyId] ?: 0,
                        onClick = { onOpen(story) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedThumb(story: Story, viewCount: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Glass)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(SurfaceDark)
        ) {
            // Poster URL, not the video — see storyTileUrl().
            val archivedTileUrl = storyTileUrl(story)
            var archivedTileFailed by remember(archivedTileUrl) { mutableStateOf(false) }
            if (archivedTileUrl.isNotBlank() && !archivedTileFailed) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(archivedTileUrl)
                        .crossfade(true).build(),
                    contentDescription = story.caption.ifBlank { "Story" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { archivedTileFailed = true }
                )
            } else if (story.type != "video") {
                // No poster / it 404'd. Videos already draw the play glyph
                // below; an image with nothing to show needs its own, or the
                // cell renders as an unexplained empty square.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (story.type == "video") {
                Box(
                    modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Glass)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("$viewCount", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 10.sp)
                }
            }
        }
        if (story.caption.isNotBlank()) {
            Text(
                text = story.caption,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Archived full-screen viewer — one expired story, gallery-style
// ─────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedStoryViewer(
    stories: List<Story>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (stories.isEmpty()) { onClose(); return }
    // Proper full-screen story layout (in-composition overlay, NOT a Dialog)
    // — same window as the app so it fits landscape + camera cutout.
    BackHandler(onBack = onClose)
    run {
        val safeInitial = initialIndex.coerceIn(0, stories.size - 1)
        val pagerState = rememberPagerState(initialPage = safeInitial) { stories.size }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var menuOpen by remember { mutableStateOf(false) }
        var detailsFor by remember { mutableStateOf<Story?>(null) }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Gallery-style swipe left/right, NO auto-advance timer.
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val story = stories[page]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (story.type == "video" && story.mediaUrl.isNotBlank()) {
                        // Only the visible page instantiates a player.
                        if (page == pagerState.currentPage) {
                            ArchivedVideoPlayer(url = story.mediaUrl)
                        } else {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(story.mediaUrl)
                                    .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        AsyncImage(
                            model = story.mediaUrl,
                            contentDescription = story.caption.ifBlank { "Story" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Caption centered at bottom (per page).
                    if (story.caption.isNotBlank()) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                                .padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 40.dp),
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

            val current = stories[pagerState.currentPage]

            // Top bar: close · counter · 3-dot menu (details / download).
            // safeDrawingPadding keeps the buttons clear of the side notch.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${stories.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Details") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuOpen = false; detailsFor = current }
                        )
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                android.widget.Toast.makeText(context, "Saving…", android.widget.Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val ok = downloadStoryMedia(context, current)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) "Saved to your gallery" else "Download failed",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Per-story details sheet.
        detailsFor?.let { s ->
            ArchivedDetailsSheet(story = s, onDismiss = { detailsFor = null })
        }
    }
}

/** Bottom sheet with one archived story's metadata. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchivedDetailsSheet(story: Story, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    run {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(SurfaceDark)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Text("Story details", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                DetailRow("Type", story.type.replaceFirstChar { it.uppercase() })
                if (story.caption.isNotBlank()) DetailRow("Caption", story.caption)
                if (story.createdAt > 0) DetailRow("Posted", story.createdAt.toRelativeTime())
                DetailRow("Views", story.viewCount.toString())
                val reactionTotal = story.reactionCounts.values.sum()
                DetailRow("Reactions", reactionTotal.toString())
                val breakdown = story.reactionCounts.filterValues { it > 0 }.entries.sortedByDescending { it.value }
                if (breakdown.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        breakdown.forEach { (emoji, count) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Glass).padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(emoji, fontSize = 15.sp)
                                Spacer(Modifier.width(5.dp))
                                Text("$count", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                val audience = if (StorySharedConfig.isWholeSchool(story.audienceClassKeys)) "Whole school"
                    else story.audienceClassKeys.joinToString(", ")
                DetailRow("Audience", audience)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextTertiary, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/**
 * Save a story's media to the device gallery via MediaStore. Images →
 * Pictures/ZenXii, videos → Movies/ZenXii. Returns true on success.
 */
private suspend fun downloadStoryMedia(context: android.content.Context, story: Story): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val url = story.mediaUrl
            if (url.isBlank()) return@withContext false
            val isVideo = story.type == "video"
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            val ext = if (isVideo) "mp4" else "jpg"
            val name = "ZenXii_Story_${System.currentTimeMillis()}.$ext"
            val resolver = context.contentResolver

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val collection = if (isVideo)
                    android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else
                    android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/ZenXii" else "Pictures/ZenXii")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(collection, values) ?: return@withContext false
                resolver.openOutputStream(itemUri)?.use { out ->
                    java.net.URL(url).openStream().use { input -> input.copyTo(out) }
                } ?: return@withContext false
                values.clear()
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                true
            } else {
                // Pre-Q: app-specific external dir (no runtime permission).
                val dir = context.getExternalFilesDir(if (isVideo) "Movies" else "Pictures")
                val file = java.io.File(dir, name)
                java.net.URL(url).openStream().use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

@androidx.annotation.OptIn(UnstableApi::class)
/**
 * The URL to render as a story's still thumbnail.
 *
 * For a video that is the uploaded POSTER, never the video itself: Coil's image
 * decoder cannot decode an .mp4 (blank tile), and VideoFrameDecoder requires a
 * local file so it would download the entire video to produce one frame. Videos
 * uploaded before posters were wired through return "" — callers fall back to a
 * type glyph. Type match is case-insensitive so a legacy "Video" still works.
 */
private fun storyTileUrl(story: Story): String =
    if (story.type.equals("video", ignoreCase = true)) story.thumbnailUrl else story.mediaUrl

// Media3's ProgressiveMediaSource / DataSource factories are @UnstableApi.
// Opting in explicitly, same as the Parent app's StoryViewer does — androidx's
// OptIn, not Kotlin's, because the marker is Java-defined.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ArchivedVideoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        // Reuse the shared disk cache (M6) like the live VideoStoryPlayer so
        // an archived clip the teacher already watched replays from disk
        // instead of re-downloading.
        val source = ProgressiveMediaSource.Factory(StoryVideoCache.dataSourceFactory(context))
            .createMediaSource(MediaItem.fromUri(url))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(source)
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    // Key on `player`, NOT Unit. The player is remember(url)-keyed, so when the
    // url changes in a retained slot a new ExoPlayer is built while a Unit-keyed
    // effect never re-runs: the old instance is never released AND onDispose
    // still captures the FIRST player, so the current one leaks too — audio kept
    // playing from the orphan.
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        modifier = Modifier.fillMaxSize()
    )
}
