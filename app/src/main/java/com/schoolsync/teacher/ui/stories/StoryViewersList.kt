package com.schoolsync.teacher.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.schoolsync.teacher.data.repository.firestore.StoryViewerEntry
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.util.toRelativeTime

/**
 * The "Seen by" list — ONE implementation shared by the author's insights
 * sheet (My Stories) and the full-screen viewer's seen-by sheet, so the two
 * can never drift apart.
 *
 * Built the way Instagram and WhatsApp build theirs:
 *   • LAZY + paged. It emits into the HOST's LazyColumn rather than nesting a
 *     scroller, so a school-wide story with 800 parents streams rows instead of
 *     composing all 800 at once (the old version was a plain Column of every
 *     viewer inside a verticalScroll).
 *   • Search appears only once the list is long enough to need it.
 *   • Loading / error / empty are THREE distinct states. Collapsing an error
 *     into "No views yet" is exactly what made a failed read indistinguishable
 *     from an unseen story, so a failure now says so and offers Retry.
 */

/** Rows revealed per page. */
const val VIEWERS_PAGE_SIZE = 50
/** Show the search field only once scrolling alone stops being enough. */
private const val SEARCH_THRESHOLD = 12

/** What the list should be showing right now. */
sealed interface ViewersUiState {
    data object Loading : ViewersUiState
    data class Error(val message: String) : ViewersUiState
    data class Ready(val viewers: List<StoryViewerEntry>) : ViewersUiState
}

/**
 * Emit the viewers section into a host [LazyListScope].
 *
 * @param visibleCount how many rows to reveal; [onLoadMore] is called when the
 *        tail scrolls into view, which is what grows it.
 */
fun LazyListScope.storyViewersSection(
    state: ViewersUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    visibleCount: Int,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        is ViewersUiState.Loading -> {
            item(key = "viewers-skeleton") { ViewerRowsSkeleton() }
        }

        is ViewersUiState.Error -> {
            item(key = "viewers-error") { ViewersError(state.message, onRetry) }
        }

        is ViewersUiState.Ready -> {
            val all = state.viewers
            if (all.isEmpty()) {
                item(key = "viewers-empty") { ViewersEmpty() }
                return
            }

            val q = query.trim()
            val filtered = if (q.isBlank()) all
                else all.filter { it.name.contains(q, ignoreCase = true) }

            if (all.size >= SEARCH_THRESHOLD) {
                item(key = "viewers-search") { ViewerSearchField(query, onQueryChange) }
            }

            if (filtered.isEmpty()) {
                item(key = "viewers-nomatch") {
                    Text(
                        text = "No viewer matches “$q”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
                return
            }

            val shown = filtered.take(visibleCount.coerceAtLeast(VIEWERS_PAGE_SIZE))
            items(shown.size, key = { i -> shown[i].userId }) { i -> ViewerRow(shown[i]) }

            if (shown.size < filtered.size) {
                // Reaching this sentinel IS the "scrolled to the bottom" signal —
                // it only composes when the tail is on screen, so no scroll-state
                // plumbing is needed and it works inside any host list.
                item(key = "viewers-more") {
                    LaunchedEffect(shown.size) { onLoadMore() }
                    LoadMoreRow(shown.size, filtered.size)
                }
            }
        }
    }
}

// ─── Pieces ──────────────────────────────────────────────────────────────

@Composable
private fun ViewerSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                "Search viewers",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search, contentDescription = null,
                tint = TextTertiary, modifier = Modifier.size(18.dp)
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = Teal,
            unfocusedBorderColor = GlassBorder,
            cursorColor = Teal,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

/** One person, Instagram-style: avatar · name · when · their reaction. */
@Composable
private fun ViewerRow(v: StoryViewerEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(TealSurface),
            contentAlignment = Alignment.Center
        ) {
            if (v.pic.isNotBlank()) {
                AsyncImage(
                    model = v.pic,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            } else {
                Text(
                    text = v.name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    color = Teal,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = v.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                v.reactedOnly -> "Reacted"
                // A server timestamp reads null for one snapshot on the writer's
                // own device; "Just now" beats a 1970 date or a dropped row.
                v.viewedAtMillis > 0 -> v.viewedAtMillis.toRelativeTime()
                else -> "Just now"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                // Distinguishes "watched it" from "opened it" per person, which
                // is the whole reason completion is tracked separately.
                if (v.completed) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Watched fully",
                        tint = Teal,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal
                    )
                }
            }
        }
        if (v.emoji != null) Text(v.emoji, fontSize = 18.sp)
    }
}

@Composable
private fun ViewersEmpty() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = "No views yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Text(
            // Says plainly why the author's own view isn't listed — otherwise
            // "0 views" on a story you just opened yourself reads as a bug
            // rather than as the same rule Instagram and WhatsApp apply.
            text = "People appear here as they watch. Your own views aren't counted.",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun ViewersError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = ErrorRed,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Couldn't load who viewed this.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRetry) {
            Text("Retry", color = Teal, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadMoreRow(shown: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = Teal,
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$shown of $total",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

/** Placeholder rows while the first snapshot is in flight. */
@Composable
private fun ViewerRowsSkeleton() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { i ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Glass))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (i % 2 == 0) 0.45f else 0.62f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Glass)
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(9.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Glass.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

/**
 * Compact "N views" pill — the author's affordance for opening the seen-by
 * list from the full-screen viewer, where Instagram and WhatsApp put theirs.
 */
@Composable
fun SeenByPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Visibility,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            // "0 views" on a story you just opened yourself reads as broken.
            // Your own views are excluded by design (Instagram and WhatsApp do
            // the same) — "No views yet" states the fact without implying a
            // number failed to update.
            text = when (count) {
                0 -> "No views yet"
                1 -> "1 view"
                else -> "$count views"
            },
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
