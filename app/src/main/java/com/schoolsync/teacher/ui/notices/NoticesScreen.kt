package com.schoolsync.teacher.ui.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.R
import androidx.compose.foundation.lazy.itemsIndexed
import com.schoolsync.teacher.ui.components.bouncyClickable
import com.schoolsync.teacher.ui.components.staggerIn
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.LocalAppColors
import com.schoolsync.teacher.ui.theme.LocalSpacing
import com.schoolsync.teacher.ui.theme.glassCard

@Composable
fun NoticesScreen(
    viewModel: NoticesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val sp = LocalSpacing.current

    GradientBackground {
        Row(modifier = Modifier.fillMaxSize()) {
            // Notices list
            Column(
                modifier = Modifier
                    .weight(if (state.selectedNotice != null) 0.5f else 1f)
                    .fillMaxHeight()
                    .padding(start = sp.lg, top = sp.sm, bottom = sp.sm, end = sp.sm)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(sp.iconLg.minus(4.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.notices_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(sp.touchTarget)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = c.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sp.sm))

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            label = {
                                Text(
                                    text = category,
                                    fontSize = 11.sp,
                                    fontWeight = if (state.selectedCategory == category) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.accent.copy(alpha = 0.15f),
                                selectedLabelColor = c.accent,
                                containerColor = c.glass,
                                labelColor = c.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = c.glassBorder,
                                selectedBorderColor = c.accent.copy(alpha = 0.3f),
                                enabled = true,
                                selected = state.selectedCategory == category
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sp.sm))

                // Notices list
                Crossfade(
                    targetState = state.isLoading,
                    animationSpec = tween(220),
                    label = "teacher-notices-loading"
                ) { loading ->
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = c.accent)
                    }
                } else if (state.error != null && state.filteredNotices.isEmpty()) {
                    // A load failure must NOT render as an empty inbox (C1) —
                    // for a comms channel that false "all clear" is dangerous.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = c.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(sp.sm))
                            Text(
                                text = "Couldn't load notices",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.error,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Check your connection and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                            Spacer(modifier = Modifier.height(sp.md))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .clickable { viewModel.refresh() }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(sp.xs))
                                Text("Retry", color = c.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else if (state.filteredNotices.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(sp.sm))
                            Text(
                                text = stringResource(R.string.notices_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textTertiary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(sp.sm),
                        contentPadding = PaddingValues(vertical = sp.xs)
                    ) {
                        itemsIndexed(
                            items = state.filteredNotices,
                            key = { _, it -> it.noticeId }
                        ) { index, notice ->
                            Box(modifier = Modifier.staggerIn(index)) {
                                NoticeCard(
                                    notice = notice,
                                    isSelected = state.selectedNotice?.noticeId == notice.noticeId,
                                    onClick = { viewModel.selectNotice(notice) }
                                )
                            }
                        }
                    }
                }
                }
            }

            // Detail panel
            AnimatedVisibility(
                visible = state.selectedNotice != null,
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut() + slideOutHorizontally { it / 2 }
            ) {
                state.selectedNotice?.let { notice ->
                    NoticeDetailPanel(
                        notice = notice,
                        onClose = { viewModel.selectNotice(null) },
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight()
                            .padding(start = sp.sm, top = sp.sm, bottom = sp.sm, end = sp.lg)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: NoticeItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current

    val categoryColor = when (notice.category.lowercase()) {
        "academic" -> c.info
        "event" -> c.warning
        "holiday" -> c.success
        "exam" -> c.accent
        "recruitment" -> c.accent
        "fee" -> c.warning
        "policy" -> c.info
        else -> c.textSecondary
    }

    val categoryIcon: ImageVector = when (notice.category.lowercase()) {
        "academic" -> Icons.Filled.MenuBook
        "event" -> Icons.Filled.Celebration
        "holiday" -> Icons.Filled.EventAvailable
        "exam" -> Icons.Filled.Assignment
        "recruitment" -> Icons.Filled.WorkOutline
        "fee" -> Icons.Filled.MonetizationOn
        "policy" -> Icons.Filled.Policy
        else -> Icons.Filled.Campaign
    }

    // Outer card with left color-strip accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                cornerRadius = 14.dp,
                borderColor = if (isSelected) c.accent.copy(alpha = 0.5f) else c.glassBorder,
                backgroundColor = if (isSelected) c.accentSurface else c.glass
            )
            .bouncyClickable(onClick = onClick)
    ) {
        // 4dp left color strip — subtle ERP-style priority/category cue
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(categoryColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Top row: category pill + unread dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = notice.category.uppercase(),
                        color = categoryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
                if (!notice.isRead) {
                    Icon(
                        Icons.Filled.Circle,
                        contentDescription = stringResource(R.string.notices_unread),
                        tint = c.accent,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )

            if (notice.body.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = notice.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Bottom meta row: author (role) · date
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (notice.author.isNotBlank()) {
                    Text(
                        text = notice.author,
                        color = c.textTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (notice.authorRole.isNotBlank()) {
                        Text(
                            text = " · ${notice.authorRole}",
                            color = c.textTertiary,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(" · ", color = c.textTertiary, fontSize = 11.sp)
                }
                Text(
                    text = notice.date,
                    color = c.textTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun NoticeDetailPanel(
    notice: NoticeItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val categoryColor = when (notice.category.lowercase()) {
        "academic" -> c.info
        "event" -> c.warning
        "holiday" -> c.success
        "exam" -> c.accent
        "recruitment" -> c.accent
        "fee" -> c.warning
        "policy" -> c.info
        else -> c.accent
    }
    val categoryIcon: ImageVector = when (notice.category.lowercase()) {
        "academic" -> Icons.Filled.MenuBook
        "event" -> Icons.Filled.Celebration
        "holiday" -> Icons.Filled.EventAvailable
        "exam" -> Icons.Filled.Assignment
        "recruitment" -> Icons.Filled.WorkOutline
        "fee" -> Icons.Filled.MonetizationOn
        "policy" -> Icons.Filled.Policy
        else -> Icons.Filled.Campaign
    }

    Column(
        modifier = modifier
            .glassCard()
            .verticalScroll(rememberScrollState())
    ) {
        // Colored header band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(categoryColor.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_close),
                        tint = categoryColor
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = notice.category.uppercase(),
                        color = categoryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Author (role) · date
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (notice.author.isNotBlank()) {
                    Text(
                        text = notice.author,
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (notice.authorRole.isNotBlank()) {
                        Text(
                            text = " · ${notice.authorRole}",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    Text(" · ", color = c.textTertiary, fontSize = 12.sp)
                }
                Text(
                    text = notice.date,
                    color = c.textTertiary,
                    fontSize = 12.sp
                )
            }

            // Attachment chip
            if (notice.attachmentUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accentSurface)
                        .clickable {
                            // Only hand http(s) URLs to ACTION_VIEW — never
                            // intent:/file:/custom schemes from server content.
                            val url = notice.attachmentUrl.trim()
                            if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)
                                        )
                                    )
                                }.onFailure {
                                    android.widget.Toast.makeText(ctx, "No app can open this attachment", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(ctx, "Attachment link is invalid", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Assignment,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Open attachment",
                        color = c.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = c.divider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(14.dp))

            if (notice.bodyHtml.isNotBlank()) {
                // Rich HTML render via WebView (HR-styled posters etc.).
                // Theme-aware text color so the body is legible in dark mode.
                val htmlTextColor = String.format(
                    "#%06X", 0xFFFFFF and c.textPrimary.toArgb()
                )
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            settings.javaScriptEnabled = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { webView ->
                        // Wrap with a font-size + color defaults so posters match app theme
                        val html = """
                            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                              body{font-family:system-ui,-apple-system,sans-serif;margin:0;padding:0;
                                   font-size:14px;line-height:1.5;color:$htmlTextColor;}
                              img{max-width:100%;height:auto;}
                            </style></head>
                            <body>${notice.bodyHtml}</body></html>
                        """.trimIndent()
                        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = notice.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
