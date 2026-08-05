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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary

/**
 * Labeled "Recent stories" section: a header + the ring carousel.
 * Used on the Stories screen and the Dashboard so a teacher can find
 * everyone's active stories. When there are none, shows a quiet hint
 * if [showWhenEmpty] (Stories screen) or renders nothing (Dashboard).
 */
@Composable
fun StoriesSection(
    groups: List<StoryGroup>,
    onOpenStory: (authorId: String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recent stories",
    showWhenEmpty: Boolean = false
) {
    if (groups.isEmpty() && !showWhenEmpty) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )
        if (groups.isEmpty()) {
            Text(
                text = "No stories in the last 24 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp)
            )
        } else {
            StoriesRow(groups = groups, onClick = onOpenStory)
        }
    }
}

/**
 * Instagram-style ring carousel of all active stories in the school,
 * grouped by author. Tapping a ring opens the full-screen viewer at
 * that author. Renders nothing when there are no active stories.
 */
@Composable
fun StoriesRow(
    groups: List<StoryGroup>,
    onClick: (authorId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.authorId }) { group ->
            StoryAvatar(group = group, onClick = { onClick(group.authorId) })
        }
    }
}

@Composable
private fun StoryAvatar(group: StoryGroup, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp).clickable(onClick = onClick)
    ) {
        val isAdmin = group.authorType == "admin"
        val isHighPri = isAdmin && group.priority == "high"
        val ringBrush = when {
            isHighPri -> Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFFC107)))
            isAdmin -> Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF8F00)))
            else -> Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.5f)))
        }
        // Instagram-style: colored ring ONLY while unseen; once every story
        // in the group is viewed the ring greys out — for admin/whole-school
        // posts too (previously `|| isAdmin` kept them colored forever).
        val showRing = group.hasUnviewed

        Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            val ringModifier = if (showRing) {
                Modifier.size(62.dp).clip(CircleShape).border(
                    width = if (isHighPri) 3.dp else 2.5.dp,
                    brush = ringBrush,
                    shape = CircleShape
                )
            } else {
                Modifier.size(62.dp).clip(CircleShape).border(2.dp, GlassBorder, CircleShape)
            }
            Box(modifier = ringModifier)

            if (group.authorPic.isNotBlank()) {
                AsyncImage(
                    model = group.authorPic,
                    contentDescription = if (group.hasUnviewed)
                        "${group.authorName}, unseen story" else "${group.authorName}, story seen",
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                )
            } else {
                val initials = group.authorName.split(" ").take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                    .joinToString("").ifBlank { "S" }
                Box(
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.6f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = group.authorName.split(" ").firstOrNull() ?: "Staff",
            style = MaterialTheme.typography.labelSmall,
            color = if (group.hasUnviewed) TextPrimary else TextSecondary,
            fontWeight = if (group.hasUnviewed) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
