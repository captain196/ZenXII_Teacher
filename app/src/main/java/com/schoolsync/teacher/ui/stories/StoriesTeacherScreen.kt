package com.schoolsync.teacher.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
import com.schoolsync.teacher.util.toRelativeTime
import kotlinx.coroutines.flow.collectLatest

@Composable
fun StoriesTeacherScreen(
    onOpenViewer: (authorId: String) -> Unit = {},
    viewModel: StoriesTeacherViewModel = hiltViewModel(),
    viewerViewModel: StoryViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val storyGroups by viewerViewModel.groups.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = viewModel::toggleUploadDialog,
                    containerColor = Teal,
                    contentColor = BgStart,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Story", fontWeight = FontWeight.SemiBold)
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // "Recent stories" — everyone's active stories, grouped
                // by author. Tap a ring to open the full-screen viewer.
                // Shows an empty hint here so the feature is discoverable.
                StoriesSection(
                    groups = storyGroups,
                    onOpenStory = onOpenViewer,
                    title = "Recent stories",
                    showWhenEmpty = true
                )

                // Top bar (your own stories)
                StoriesTopBar(onRefresh = viewModel::refresh)

                // Content
                StoriesGridContent(
                    state = state,
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
    }
}

@Composable
private fun StoriesTopBar(onRefresh: () -> Unit) {
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
                text = "My Stories",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
        }
    }
}

@Composable
private fun StoriesGridContent(
    state: StoriesUiState,
    onDelete: (Story) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "No stories yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Text(
                    text = "Tap + to upload your first story",
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.myStories, key = { it.storyId }) { story ->
                StoryCard(
                    story = story,
                    viewCount = state.viewCounts[story.storyId] ?: 0,
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
    onDelete: () -> Unit
) {
    val isExpired = story.isExpired
    val cardAlpha = if (isExpired) 0.5f else 1f

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
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(SurfaceDark)
        ) {
            if (story.mediaUrl.isNotBlank()) {
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = story.caption.ifBlank { "Story" },
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (isExpired) it.background(Color.Black.copy(alpha = 0.4f)) else it },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (story.type == "video") Icons.Filled.Videocam else Icons.Filled.Image,
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

        // Caption and metadata row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (story.caption.isNotBlank()) {
                    Text(
                        text = story.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary.copy(alpha = cardAlpha),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (story.createdAt > 0) {
                    Text(
                        text = story.createdAt.toRelativeTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary.copy(alpha = cardAlpha),
                        fontSize = 10.sp
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete story",
                    tint = ErrorRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

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
                        if (isPicking) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        progress = { mediaUploadPercent / 100f },
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (isCompressing) "Compressing… $mediaUploadPercent%"
                                        else "Uploading… $mediaUploadPercent%",
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

                // ── AUDIENCE PICKER ───────────────────────────────
                // Who sees this story. Placed directly under the media so
                // the teacher makes an explicit visibility choice BEFORE
                // sharing. "Whole school" = empty target set (visible to
                // all parents). Otherwise scoped to the selected class-
                // sections (parents of those sections). Defaults to the
                // teacher's class-teacher section(s). Always rendered — even
                // if the teacher has no assigned sections yet, the "Whole
                // school" option is shown so the picker is never blank.
                Column {
                    Text(
                        text = "Who can see this story?",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    run {
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
                                leadingIcon = if (wholeSchool) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
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
                                    leadingIcon = if (isSel) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
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
                    }
                    Spacer(modifier = Modifier.height(6.dp))
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

            }
        },
        confirmButton = {
            Button(
                onClick = onUpload,
                enabled = !isUploading && !isPicking && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = BgStart
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = BgStart,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isUploading) "Sharing…" else "Share Story",
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
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp)
    )
}
