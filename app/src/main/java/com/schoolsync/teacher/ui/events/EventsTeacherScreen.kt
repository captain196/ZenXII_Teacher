package com.schoolsync.teacher.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.firestore.EventDoc
import com.schoolsync.teacher.ui.components.bouncyClickable
import com.schoolsync.teacher.util.AttachmentUrlValidator
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EventsTeacherScreen(
    onOpenAlbum: (albumId: String) -> Unit = {},
    viewModel: EventsTeacherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Store just the event id so the open dialog survives process death.
    var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EventsHeader(onRefresh = viewModel::refresh)

        when {
            state.isLoading && state.events.isEmpty() -> LoadingState()
            state.error != null && state.events.isEmpty() -> ErrorState(state.error ?: "Failed to load events")
            state.events.isEmpty() -> EmptyState(onRefresh = viewModel::refresh)
            else -> EventsList(state.events, onEventClick = { selectedEventId = it.id })
        }
    }

    state.events.firstOrNull { it.id == selectedEventId }?.let { evt ->
        // Look up the admin-generated photo album for this event (if any).
        var eventAlbum by remember(evt.id) { mutableStateOf<GalleryAlbum?>(null) }
        LaunchedEffect(evt.id) {
            eventAlbum = viewModel.getEventAlbum(evt.id)
        }
        EventDetailDialog(
            event = evt,
            album = eventAlbum,
            onViewPhotos = {
                eventAlbum?.let { onOpenAlbum(it.albumId) }
                selectedEventId = null
            },
            onDismiss = { selectedEventId = null }
        )
    }
}

@Composable
private fun EventsHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Events",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun EventsList(events: List<EventDoc>, onEventClick: (EventDoc) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(events, key = { it.id }) { evt ->
            EventCard(evt, onClick = { onEventClick(evt) })
        }
    }
}

@Composable
private fun EventCard(evt: EventDoc, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover = first media that passes the URL allowlist; falls back to
            // the calendar icon when the event has no (valid) media.
            val cover = evt.mediaUrls.firstOrNull {
                AttachmentUrlValidator.validate(it) is AttachmentUrlValidator.Result.Valid
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    EventMediaThumb(
                        url = cover,
                        context = LocalContext.current,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = evt.title.ifBlank { "Untitled event" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDate(evt.startDate),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (evt.location.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = evt.location,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            StatusPill(evt.status)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = statusColors(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun statusColors(status: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status.lowercase()) {
        "ongoing"   -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "completed" -> scheme.surfaceVariant to scheme.onSurfaceVariant
        "cancelled" -> scheme.errorContainer to scheme.onErrorContainer
        else        -> scheme.primaryContainer to scheme.onPrimaryContainer // scheduled + unknown
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit = {}) {
    com.schoolsync.teacher.ui.components.EmptyStatePro(
        icon = Icons.Filled.Event,
        title = "No events scheduled",
        description = "Upcoming PTMs, sports days and assemblies will appear here.",
        actionLabel = "Refresh",
        onAction = onRefresh,
    )
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun EventDetailDialog(
    event: EventDoc,
    album: GalleryAlbum?,
    onViewPhotos: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    // Only surface links that pass the https + trusted-Storage-host allowlist.
    val validMedia = event.mediaUrls.filter {
        AttachmentUrlValidator.validate(it) is AttachmentUrlValidator.Result.Valid
    }
    val cover = validMedia.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = screenH * 0.92f)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Two-pane (cover left · details right) once there's width —
                // landscape phones & tablets. Portrait stacks cover-on-top.
                val twoPane = maxWidth >= 560.dp

                if (twoPane) {
                    // Capture the constraint values into locals — the layout
                    // DslMarker blocks reading maxWidth/maxHeight from inside the
                    // nested Row/Column receivers.
                    val paneHeight = minOf(maxHeight, 460.dp)
                    val coverW = minOf(maxWidth * 0.42f, 300.dp)
                    Row(modifier = Modifier.fillMaxWidth().height(paneHeight)) {
                        EventCoverPane(
                            cover = cover,
                            event = event,
                            context = context,
                            modifier = Modifier
                                .width(coverW)
                                .fillMaxHeight()
                        )
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            EventDetailsBody(
                                event = event,
                                validMedia = validMedia,
                                album = album,
                                onViewPhotos = onViewPhotos,
                                context = context,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                            )
                            EventFooter(album, validMedia, onViewPhotos, scheme)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EventCoverPane(
                            cover = cover,
                            event = event,
                            context = context,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (cover != null) 188.dp else 116.dp)
                        )
                        EventDetailsBody(
                            event = event,
                            validMedia = validMedia,
                            album = album,
                            onViewPhotos = onViewPhotos,
                            context = context,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                        EventFooter(album, validMedia, onViewPhotos, scheme)
                    }
                }

                // Close — top-right of the whole dialog in both layouts.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Firebase Storage download URLs keep the real file extension before the `?`. */
private fun isLikelyVideoUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".3gp", ".m4v").any { path.endsWith(it) }
}

/**
 * Event cover / thumbnail tile. Renders an image URL via Coil, but for a VIDEO
 * url it shows a play glyph instead of handing the raw .mp4 to Coil: decoding a
 * frame off a REMOTE video downloads the entire file (slow, heavy, routinely
 * fails to a blank tile) and events carry no pre-generated poster. Mirrors the
 * Gallery module's deliberate poster-only strategy. (Fixes audit M7.)
 */
@Composable
private fun EventMediaThumb(
    url: String,
    context: android.content.Context,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (isLikelyVideoUrl(url)) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = contentDescription ?: "Video",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

/** Cover image (or gradient fallback) with the status pill + title overlaid. */
@Composable
private fun EventCoverPane(
    cover: String?,
    event: EventDoc,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier) {
        if (cover != null) {
            EventMediaThumb(
                url = cover,
                context = context,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.primaryContainer))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Event, null, tint = scheme.onPrimary, modifier = Modifier.size(44.dp))
            }
        }
        // Bottom scrim so the overlaid title stays legible on any image.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.66f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            StatusPill(event.status)
            Spacer(Modifier.height(8.dp))
            Text(
                text = event.title.ifBlank { "Untitled event" },
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Scrolling details: date/category/venue/organizer, description, photo grid. */
@Composable
private fun EventDetailsBody(
    event: EventDoc,
    validMedia: List<String>,
    album: GalleryAlbum?,
    onViewPhotos: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        val dateText = buildString {
            append(formatDate(event.startDate))
            if (event.endDate.isNotBlank() && event.endDate != event.startDate) {
                append("  →  ").append(formatDate(event.endDate))
            }
        }
        InfoRow(Icons.Filled.CalendarMonth, dateText)
        if (event.category.isNotBlank()) InfoRow(Icons.Filled.Category, event.category)
        if (event.location.isNotBlank()) InfoRow(Icons.Filled.LocationOn, event.location)
        if (event.organizer.isNotBlank()) InfoRow(Icons.Filled.Person, event.organizer)

        if (event.description.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = event.description,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = scheme.onSurface
            )
        }

        if (validMedia.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            EventMediaGrid(
                urls = validMedia,
                totalCount = maxOf(album?.mediaCount ?: 0, validMedia.size),
                onThumbClick = { url ->
                    if (album != null) onViewPhotos()
                    else AttachmentUrlValidator.openAttachmentSafely(context, url, "event_media")
                }
            )
        }
    }
}

/**
 * Sticky "View all photos" action → the full album (with the gallery viewer's
 * swipe/zoom), so partial inline media is never the only way to see the photos.
 * Renders a small spacer when the event has no album.
 */
@Composable
private fun EventFooter(
    album: GalleryAlbum?,
    validMedia: List<String>,
    onViewPhotos: () -> Unit,
    scheme: androidx.compose.material3.ColorScheme
) {
    if (album != null) {
        val count = if (album.mediaCount > 0) album.mediaCount else validMedia.size
        Button(
            onClick = onViewPhotos,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            )
        ) {
            Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (count > 0) "View all photos ($count)" else "View gallery",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = scheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/**
 * Photo preview grid for the event dialog: up to 6 square thumbnails (3 per
 * row); if the album holds more, the last tile shows a "+N" overflow. Any tap
 * routes to the full album (or, when there is no album, opens the single
 * validated URL externally) — so the preview is never the whole story.
 */
@Composable
private fun EventMediaGrid(
    urls: List<String>,
    totalCount: Int,
    onThumbClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val shown = urls.take(6)
    val extra = (totalCount - shown.size).coerceAtLeast(0)

    Text(
        text = "Photos",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    val rows = shown.chunked(3)
    rows.forEachIndexed { rowIdx, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEachIndexed { colIdx, url ->
                val globalIdx = rowIdx * 3 + colIdx
                val isLastShown = globalIdx == shown.size - 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceVariant)
                        .clickable { onThumbClick(url) },
                    contentAlignment = Alignment.Center
                ) {
                    EventMediaThumb(
                        url = url,
                        context = context,
                        contentDescription = "Event photo",
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLastShown && extra > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+$extra", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Keep tiles square on a short final row.
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        if (rowIdx != rows.lastIndex) Spacer(Modifier.height(8.dp))
    }
}

/** Accept "YYYY-MM-DD" and render as "15 Apr 2026". Returns the raw input on parse failure. */
private fun formatDate(raw: String): String {
    if (raw.isBlank()) return "—"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        parser.parse(raw)?.let { formatter.format(it) } ?: raw
    } catch (e: Exception) {
        raw
    }
}
