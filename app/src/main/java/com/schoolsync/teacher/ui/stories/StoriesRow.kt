package com.schoolsync.teacher.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary

/**
 * The story tray — Instagram's model, one row.
 *
 * "Your story" is the FIRST tile and doubles as the post entry (a `+` badge on
 * your own avatar). Everyone else's stories follow as segmented rings.
 *
 * This replaces a design where a staff member's own story rendered THREE times:
 * once as their ring in the Dashboard carousel, again as their ring in the
 * Stories-screen carousel, and a third time as a card in the "My Stories" grid
 * directly beneath that second carousel. Own stories are now split out of the
 * general list and represented once, in the tile that is unambiguously theirs.
 */
@Composable
fun StoriesTray(
    /** Everyone ELSE's story groups. Own stories must be filtered out upstream. */
    groups: List<StoryGroup>,
    /** This staff member's own active stories, or null when they've posted none. */
    myGroup: StoryGroup?,
    myName: String,
    myPic: String,
    /** Edit-level rights: view-only staff browse but never post. */
    canPost: Boolean,
    onOpenStory: (authorId: String) -> Unit,
    onCreateStory: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Nothing to show and nothing to do — render nothing rather than an empty
    // row with a stranded tile.
    if (groups.isEmpty() && myGroup == null && !canPost) return

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The tile is shown whenever this member can post OR already has
        // stories — a view-only grantee with nothing posted sees no tile.
        if (myGroup != null || canPost) {
            item(key = "__mine") {
                YourStoryTile(
                    myGroup = myGroup,
                    name = myName,
                    pic = myPic,
                    canPost = canPost,
                    onOpen = { myGroup?.let { onOpenStory(it.authorId) } },
                    onCreate = onCreateStory
                )
            }
        }
        items(groups, key = { it.authorId }) { group ->
            StoryAvatar(group = group, onClick = { onOpenStory(group.authorId) })
        }
    }
}

/**
 * "Your story" — own avatar, own segmented ring when there's something to
 * show, and a `+` badge that posts. With no active story the tile is purely
 * the post button, which is what makes it a viable replacement for the FAB.
 */
@Composable
private fun YourStoryTile(
    myGroup: StoryGroup?,
    name: String,
    pic: String,
    canPost: Boolean,
    onOpen: () -> Unit,
    onCreate: () -> Unit
) {
    val hasStories = myGroup != null && myGroup.stories.isNotEmpty()
    val unseenCount = myGroup?.stories?.count { !it.isViewed } ?: 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            // Tapping the tile opens your story when you have one; with none it
            // is the post button, so the whole tile is a full-size target for
            // the action that matters in that state.
            .clickable { if (hasStories) onOpen() else if (canPost) onCreate() }
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    !hasStories -> "Add to your story"
                    unseenCount > 0 -> "Your story, $unseenCount not yet seen by you"
                    else -> "Your story, ${myGroup?.stories?.size ?: 0} posted"
                }
            }
    ) {
        Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            if (hasStories) {
                SegmentedStoryRing(
                    segmentSeen = myGroup!!.stories.map { it.isViewed },
                    unseenBrush = Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.5f))),
                    seenColor = GlassBorder,
                    diameter = 62.dp,
                    strokeWidth = 2.5.dp
                )
            } else {
                // No ring to draw — a faint outline keeps the tile the same
                // size and shape as its neighbours instead of shrinking.
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(1.dp, GlassBorder.copy(alpha = 0.5f), CircleShape)
                )
            }

            StoryCircleContent(
                name = name,
                pic = pic,
                // Nothing posted yet → no media to preview, so the tile falls
                // back to your own avatar and reads as "add", not as a story.
                mediaUrl = if (hasStories) myGroup!!.previewMediaUrl() else "",
                size = 54
            )

            if (canPost) {
                // Instagram's `+` badge. A shortcut, not the only route —
                // the screen header carries a full-size "New story" action so
                // posting never depends on hitting a 22dp target.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Teal)
                        .border(2.dp, BgStart, CircleShape)
                        .clickable { onCreate() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add to your story",
                        tint = BgStart,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "Your story",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The ONE media still that represents an author's stories in the tray.
 *
 * Picks the story the tap will actually open — the first unseen one, falling
 * back to the most recent when everything has been seen — so the circle
 * previews what you're about to watch rather than an arbitrary post. An author
 * with three stories still shows a single thumbnail; the arc count is what
 * communicates "three".
 *
 * Video stories preview from their POSTER, never the .mp4: Coil's image decoder
 * can't decode video, and VideoFrameDecoder needs a local file so it would pull
 * the whole clip down to draw a 54dp circle.
 */
private fun StoryGroup.previewMediaUrl(): String {
    // MUST mirror the viewer's opening index (AuthorStoryPage): first unseen,
    // otherwise the FIRST story. Falling back to last() instead made the tile
    // preview a different story from the one tapping it opens — an author whose
    // stories are all "seen" saw their newest story's thumbnail but landed on
    // their oldest, whose view count is legitimately different. That reads as a
    // stale/wrong count when both numbers are actually correct.
    val s = stories.firstOrNull { !it.isViewed } ?: stories.firstOrNull() ?: return ""
    return if (s.type.equals("video", ignoreCase = true)) s.thumbnailUrl else s.mediaUrl
}

/**
 * Circle content: the story's media still, falling back to the person's photo,
 * then to initials. Every step can fail (missing poster, dead URL, no avatar),
 * so all three are wired rather than assumed.
 */
@Composable
private fun StoryCircleContent(
    name: String,
    pic: String,
    mediaUrl: String,
    size: Int
) {
    var mediaFailed by remember(mediaUrl) { mutableStateOf(false) }
    var picFailed by remember(pic) { mutableStateOf(false) }

    when {
        mediaUrl.isNotBlank() && !mediaFailed -> AsyncImage(
            model = mediaUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { mediaFailed = true },
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
        pic.isNotBlank() && !picFailed -> AsyncImage(
            model = pic,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { picFailed = true },
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
        else -> {
            val initials = name.split(" ").take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("").ifBlank { "S" }
            Box(
                modifier = Modifier.size(size.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Labeled tray section: a header + the tray. Used on the Dashboard and the
 * Stories screen so both lead with the same "Your story" tile.
 */
@Composable
fun StoriesSection(
    groups: List<StoryGroup>,
    myGroup: StoryGroup?,
    myName: String,
    myPic: String,
    canPost: Boolean,
    onOpenStory: (authorId: String) -> Unit,
    onCreateStory: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Stories",
    /** Show a quiet hint instead of nothing when there is nothing at all. */
    showWhenEmpty: Boolean = false
) {
    val nothingToShow = groups.isEmpty() && myGroup == null && !canPost
    if (nothingToShow && !showWhenEmpty) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )
        if (nothingToShow) {
            Text(
                text = "No stories in the last 24 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp)
            )
        } else {
            StoriesTray(
                groups = groups,
                myGroup = myGroup,
                myName = myName,
                myPic = myPic,
                canPost = canPost,
                onOpenStory = onOpenStory,
                onCreateStory = onCreateStory
            )
        }
    }
}

@Composable
private fun StoryAvatar(group: StoryGroup, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                val unseen = group.stories.count { !it.isViewed }
                contentDescription = when {
                    unseen == 0 -> "${group.authorName}, all stories seen"
                    unseen == 1 -> "${group.authorName}, 1 unseen story"
                    else -> "${group.authorName}, $unseen unseen stories"
                }
            }
    ) {
        val isAdmin = group.authorType == "admin"
        val isHighPri = isAdmin && group.priority == "high"
        val ringBrush = when {
            isHighPri -> Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFFC107)))
            isAdmin -> Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF8F00)))
            else -> Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.5f)))
        }

        Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            SegmentedStoryRing(
                segmentSeen = group.stories.map { it.isViewed },
                unseenBrush = ringBrush,
                seenColor = GlassBorder,
                diameter = 62.dp,
                strokeWidth = if (isHighPri) 3.dp else 2.5.dp
            )
            StoryCircleContent(
                name = group.authorName,
                pic = group.authorPic,
                mediaUrl = group.previewMediaUrl(),
                size = 54
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        val anyUnseen = group.stories.any { !it.isViewed }
        Text(
            text = group.authorName.split(" ").firstOrNull() ?: "Staff",
            style = MaterialTheme.typography.labelSmall,
            color = if (anyUnseen) TextPrimary else TextSecondary,
            fontWeight = if (anyUnseen) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
