package com.schoolsync.teacher.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.GalleryMedia
import com.schoolsync.teacher.util.AttachmentUrlValidator
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.glassCard
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GalleryTeacherScreen(
    initialAlbumId: String? = null,
    onInitialAlbumConsumed: () -> Unit = {},
    viewModel: GalleryTeacherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Deep-link from the Events "View Photos" jump: preselect the album.
    LaunchedEffect(initialAlbumId) {
        val id = initialAlbumId
        if (!id.isNullOrBlank()) {
            viewModel.openAlbumById(id)
            onInitialAlbumConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GalleryEvent.Success -> snackbarHostState.showSnackbar(event.message)
                is GalleryEvent.Error -> snackbarHostState.showSnackbar("Error: ${event.message}")
            }
        }
    }

    GradientBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left panel: Albums list
                AlbumsPanel(
                    albums = state.albums,
                    selectedAlbum = state.selectedAlbum,
                    isLoading = state.isLoadingAlbums,
                    onAlbumClick = viewModel::selectAlbum,
                    onCreateAlbum = viewModel::showCreateAlbumDialog,
                    onRefresh = viewModel::loadAlbums,
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                )

                // Thin divider
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = DividerColor
                )

                // Right panel: Media grid
                MediaPanel(
                    selectedAlbum = state.selectedAlbum,
                    media = state.media,
                    isLoading = state.isLoadingMedia,
                    onUploadClick = viewModel::showUploadMediaDialog,
                    onBackClick = { viewModel.selectAlbum(null) },
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
                )
            }

            // Snackbar
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Glass,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Create Album Dialog
        if (state.showCreateAlbumDialog) {
            CreateAlbumDialog(
                isCreating = state.isCreatingAlbum,
                onDismiss = viewModel::hideCreateAlbumDialog,
                onCreate = viewModel::createAlbum
            )
        }

        // Upload Media Dialog (file picker + Storage upload)
        if (state.showUploadMediaDialog) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            UploadMediaDialog(
                isUploading = state.isUploading,
                onDismiss = viewModel::hideUploadMediaDialog,
                onUpload = { uri, type, caption ->
                    viewModel.uploadMediaFile(ctx, uri, type, caption)
                }
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
private fun AlbumsPanel(
    albums: List<GalleryAlbum>,
    selectedAlbum: GalleryAlbum?,
    isLoading: Boolean,
    onAlbumClick: (GalleryAlbum) -> Unit,
    onCreateAlbum: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gallery",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
                IconButton(
                    onClick = onCreateAlbum,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Teal.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Album", tint = Teal)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Albums list
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal)
            }
        } else if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Collections,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No albums yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Create your first album",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(albums, key = { it.albumId }) { album ->
                    AlbumCard(
                        album = album,
                        isSelected = selectedAlbum?.albumId == album.albumId,
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: GalleryAlbum,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                cornerRadius = 12.dp,
                borderColor = if (isSelected) Teal.copy(alpha = 0.5f) else GlassBorder,
                backgroundColor = if (isSelected) TealSurface else Glass
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover image thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            if (album.coverImage.isNotEmpty()) {
                AsyncImage(
                    model = album.coverImage,
                    contentDescription = album.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (album.category.isNotEmpty()) {
                Text(
                    text = album.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    fontSize = 10.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${album.mediaCount} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
                if (album.createdAt.isNotBlank()) {
                    Text(
                        text = album.createdAt.take(10),   // ISO date portion: YYYY-MM-DD
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPanel(
    selectedAlbum: GalleryAlbum?,
    media: List<GalleryMedia>,
    isLoading: Boolean,
    onUploadClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (selectedAlbum != null) {
            // Header with album name and upload button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedAlbum.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedAlbum.description.isNotEmpty()) {
                            Text(
                                text = selectedAlbum.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Button(
                    onClick = onUploadClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal,
                        contentColor = BgStart
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Media grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Teal)
                }
            } else if (media.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No media in this album",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Upload images or videos to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                // Store just the id so the open viewer survives process death.
                var viewerMediaId by rememberSaveable { mutableStateOf<String?>(null) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(media, key = { it.mediaId }) { item ->
                        MediaCard(media = item, onClick = { viewerMediaId = item.mediaId })
                    }
                }
                val viewerStartIndex = media.indexOfFirst { it.mediaId == viewerMediaId }
                if (viewerStartIndex >= 0) {
                    GalleryMediaPagerViewer(
                        media = media,
                        initialIndex = viewerStartIndex,
                        onDismiss = { viewerMediaId = null }
                    )
                }
            }
        } else {
            // No album selected
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Collections,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Select an album",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary
                    )
                    Text(
                        text = "Choose an album from the left to view its media",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(media: GalleryMedia, onClick: () -> Unit) {
    val context = LocalContext.current
    // Prefer an explicit poster frame; else decode the first video frame.
    val thumbModel = media.thumbnail.ifBlank { media.url }
    // Only render Coil for trusted, well-formed https Storage URLs.
    val isValid = AttachmentUrlValidator.validate(thumbModel) is AttachmentUrlValidator.Result.Valid
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .glassCard(cornerRadius = 10.dp)
            .clickable(onClick = onClick)
    ) {
        if (isValid) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbModel)
                    .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = media.caption,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Video badge
        if (media.type == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BgStart.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = "Video",
                    tint = Teal,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Caption overlay at bottom
        if (media.caption.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        BgStart.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = media.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CreateAlbumDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Text("Create Album", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = galleryTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = galleryTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    placeholder = { Text("e.g., Events, Sports, Academics", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = galleryTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.trim(), description.trim(), category.trim()) },
                enabled = !isCreating && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = BgStart
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = BgStart,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun UploadMediaDialog(
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onUpload: (uri: android.net.Uri, type: String, caption: String) -> Unit
) {
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }

    // System Photo Picker — no runtime permissions needed (Android 13+ falls
    // back to MediaStore picker on older devices automatically).
    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    AlertDialog(
        // Block tap-outside + back-press dismissal so an in-progress
        // upload (or partly-filled form) can't be lost by accident.
        // Only the X / Cancel buttons close the dialog.
        onDismissRequest = { /* no-op — must use X or Cancel */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress    = false
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Upload Media",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    enabled = !isUploading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = if (isUploading) TextTertiary else TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type toggle (drives which picker filter we use)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Type:", color = TextSecondary, fontSize = 13.sp)
                    TextButton(
                        onClick = { isVideo = false; pickedUri = null },
                        enabled = !isUploading,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (!isVideo) Teal else TextTertiary
                        )
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Image", fontWeight = if (!isVideo) FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(
                        onClick = { isVideo = true; pickedUri = null },
                        enabled = !isUploading,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isVideo) Teal else TextTertiary
                        )
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video", fontWeight = if (isVideo) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                // Picker / preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .clickable(enabled = !isUploading) {
                            val mediaType = if (isVideo) {
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                            } else {
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            }
                            pickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(mediaType)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val uri = pickedUri
                    if (uri != null) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to choose ${if (isVideo) "video" else "photo"}",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = galleryTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isUploading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    pickedUri?.let { uri ->
                        onUpload(uri, if (isVideo) "video" else "image", caption.trim())
                    }
                },
                enabled = !isUploading && pickedUri != null,
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
                Text(if (isUploading) "Uploading..." else "Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * MIUI-gallery–style full-screen media viewer.
 *
 * Ports the Stories viewer's gesture stack so images/videos behave the same
 * everywhere:
 *   • swipe left / right → previous / next media in the album (HorizontalPager)
 *   • pinch to zoom (1×–5×) + double-tap to toggle zoom, with panning + bounds
 *   • one-finger pan while zoomed; page-swipe is LOCKED while zoomed so a pan
 *     never accidentally flips the page
 *   • swipe down (when not zoomed) → dismiss, with the backdrop fading away
 *   • tap → toggle chrome (close button + counter + caption)
 *   • videos play inline (validated URL → ExoPlayer)
 *
 * Consumes multi-touch (and one-finger-while-zoomed) only, exactly like the
 * Stories viewer — so single-finger horizontal swipes still reach the pager
 * and single-finger vertical swipes still reach the dismiss detector.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GalleryMediaPagerViewer(
    media: List<GalleryMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (media.isEmpty()) return
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        val context = LocalContext.current
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, media.size - 1)
        ) { media.size }

        // Transform state for the CURRENT page only; reset when the page changes.
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var playingVideo by remember { mutableStateOf(false) }
        var chromeVisible by remember { mutableStateOf(true) }
        var dragY by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(pagerState.currentPage) {
            scale = 1f; offsetX = 0f; offsetY = 0f; playingVideo = false; dragY = 0f
        }
        val isZoomed = scale > 1.02f
        val dismissProgress = (dragY / 620f).coerceIn(0f, 1f)
        val chromeAlpha by animateFloatAsState(
            targetValue = if (chromeVisible && !isZoomed && dragY < 4f) 1f else 0f,
            animationSpec = tween(180), label = "chrome"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - dismissProgress * 0.9f))
        ) {
            HorizontalPager(
                state = pagerState,
                // Lock paging while zoomed → the finger pans instead of flipping.
                userScrollEnabled = !isZoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = media[page]
                val isActive = page == pagerState.currentPage
                val isVideo = item.type.equals("video", ignoreCase = true)
                val isValid = AttachmentUrlValidator.validate(item.url) is
                    AttachmentUrlValidator.Result.Valid

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Whole active page slides + shrinks a touch while
                        // swiping down to dismiss.
                        .then(
                            if (isActive) Modifier.graphicsLayer {
                                translationY = dragY.coerceAtLeast(0f)
                                val s = 1f - dismissProgress * 0.2f
                                scaleX = s; scaleY = s
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo && isActive && playingVideo && isValid) {
                        GalleryVideoPlayer(url = item.url)
                    } else {
                        val model = if (isVideo && item.thumbnail.isNotBlank())
                            item.thumbnail else item.url
                        if (isValid || (isVideo && item.thumbnail.isNotBlank())) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(model)
                                    .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.caption.ifBlank { null },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isActive) Modifier.graphicsLayer {
                                            scaleX = scale; scaleY = scale
                                            translationX = offsetX; translationY = offsetY
                                        } else Modifier
                                    )
                                    .then(
                                        if (isActive) Modifier
                                            // Pinch / one-finger-while-zoomed pan.
                                            // Consumes ONLY multi-touch or zoomed
                                            // drags, so the pager + dismiss keep
                                            // their single-finger gestures.
                                            .pointerInput(page) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val pressed = event.changes.count { it.pressed }
                                                        val pan = event.calculatePan()
                                                        if (pressed >= 2) {
                                                            val newScale = (scale * event.calculateZoom())
                                                                .coerceIn(1f, 5f)
                                                            scale = newScale
                                                            val maxX = (size.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                                            val maxY = (size.height * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                                            event.changes.forEach { it.consume() }
                                                        } else if (pressed == 1 && scale > 1.02f) {
                                                            val maxX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                            val maxY = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
                                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    } while (event.changes.any { it.pressed })
                                                }
                                            }
                                            .pointerInput(page) {
                                                detectTapGestures(
                                                    onTap = { chromeVisible = !chromeVisible },
                                                    onDoubleTap = {
                                                        if (scale > 1.5f) {
                                                            scale = 1f; offsetX = 0f; offsetY = 0f
                                                        } else scale = 2.5f
                                                    }
                                                )
                                            }
                                            // Swipe-down dismiss — only when NOT
                                            // zoomed (a zoomed pan owns the drag).
                                            .pointerInput(page, isZoomed) {
                                                if (!isZoomed) {
                                                    detectVerticalDragGestures(
                                                        onVerticalDrag = { _, dy ->
                                                            dragY = (dragY + dy).coerceAtLeast(0f)
                                                        },
                                                        onDragEnd = {
                                                            if (dragY > 250f) onDismiss() else dragY = 0f
                                                        },
                                                        onDragCancel = { dragY = 0f }
                                                    )
                                                }
                                            }
                                        else Modifier
                                    )
                            )
                        } else {
                            Text("No preview available", color = TextSecondary)
                        }
                        // Play affordance over a video poster frame.
                        if (isVideo && isActive && !playingVideo) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .clickable { if (isValid) playingVideo = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Chrome: top bar (close + counter) ────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = chromeAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${media.size}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // ── Chrome: caption ──────────────────────────────────────────
            val current = media.getOrNull(pagerState.currentPage)
            if (!current?.caption.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = chromeAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = current!!.caption,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun galleryTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal,
    unfocusedBorderColor = GlassBorder,
    cursorColor = Teal,
    focusedContainerColor = Glass.copy(alpha = 0.2f),
    unfocusedContainerColor = Glass.copy(alpha = 0.1f),
    focusedLabelColor = Teal,
    unfocusedLabelColor = TextTertiary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

/**
 * Fullscreen ExoPlayer for a gallery video. The [url] must already be
 * validated (https + trusted Storage host) by the caller. Mirrors the
 * Stories viewer's player; shows the default controls so the teacher can
 * scrub/pause. The player is released on dispose.
 */
@Composable
private fun GalleryVideoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
