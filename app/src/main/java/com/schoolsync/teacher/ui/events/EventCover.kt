package com.schoolsync.teacher.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.schoolsync.teacher.util.AttachmentUrlValidator

/** First attachment-valid URL in an event's mediaUrls (the cover), or null. */
internal fun firstEventCover(mediaUrls: List<String>): String? =
    mediaUrls.firstOrNull {
        AttachmentUrlValidator.validate(it) is AttachmentUrlValidator.Result.Valid
    }

/** Category → accent color for event placeholders (reads well in light + dark). */
internal fun eventCategoryColor(category: String): Color = when (category.lowercase()) {
    "exam", "examination", "test"     -> Color(0xFFE1554B)
    "sports", "sport"                 -> Color(0xFFE8912F)
    "holiday"                         -> Color(0xFF3EA36B)
    "ptm", "meeting"                  -> Color(0xFF3B82C4)
    "cultural", "culture", "function" -> Color(0xFF8B5CF6)
    else                              -> Color(0xFF2BB3A3)
}

/** Category → glyph for event placeholders. */
internal fun eventCategoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "exam", "examination", "test"     -> Icons.Filled.School
    "sports", "sport"                 -> Icons.Filled.SportsSoccer
    "holiday"                         -> Icons.Filled.Celebration
    "ptm", "meeting"                  -> Icons.Filled.Groups
    "cultural", "culture", "function" -> Icons.Filled.TheaterComedy
    else                              -> Icons.Filled.Event
}

/**
 * Event cover tile: shows the album/media cover image when [coverUrl] is a valid
 * attachment URL, otherwise a polished category-colored gradient + glyph so
 * photo-less events read as intentional rather than blank. Reused by the
 * dashboard rail and the events list so covers look consistent everywhere.
 */
@Composable
internal fun EventCoverBox(
    coverUrl: String?,
    category: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val valid = coverUrl?.takeIf {
        AttachmentUrlValidator.validate(it) is AttachmentUrlValidator.Result.Valid
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (valid != null) {
            EventMediaThumb(
                url = valid,
                context = LocalContext.current,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val c = eventCategoryColor(category)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(c.copy(alpha = 0.92f), c.copy(alpha = 0.60f)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = eventCategoryIcon(category),
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}
