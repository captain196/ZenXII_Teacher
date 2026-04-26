@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.teacher.ui.splash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.ui.theme.BgEnd
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.InfoBlue
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealDark
import com.schoolsync.teacher.ui.theme.TextOnAccent
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import kotlinx.coroutines.launch

data class WalkthroughPage(
    val icon: ImageVector,
    val iconBg: List<Color>,
    val title: String,
    val description: String
)

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun teacherPages(): List<WalkthroughPage> = listOf(
    WalkthroughPage(
        icon = Icons.Filled.CheckCircle,
        iconBg = listOf(Teal, TealDark),
        title = "Smart Attendance",
        description = "Take attendance digitally with one tap.\nTrack patterns and generate reports effortlessly."
    ),
    WalkthroughPage(
        icon = Icons.Filled.Grade,
        iconBg = listOf(WarningAmber, Color(0xFFD97706)),
        title = "Marks & Results",
        description = "Upload marks, auto-calculate grades, and\nshare results with parents instantly."
    ),
    WalkthroughPage(
        icon = Icons.Filled.Forum,
        iconBg = listOf(InfoBlue, Color(0xFF2563EB)),
        title = "Stay Connected",
        description = "Communicate with parents, share notices,\nand manage leave requests all in one place."
    )
)

@Composable
fun WalkthroughScreen(
    onFinished: () -> Unit
) {
    val pages = teacherPages()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BgStart, BgEnd, BgStart)
                )
            )
    ) {
        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        ) { page ->
            WalkthroughPageContent(pages[page])
        }

        // Bottom bar with indicators and buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip button
            TextButton(onClick = onFinished) {
                Text(
                    text = "Skip",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val color by animateColorAsState(
                        targetValue = if (isActive) Teal else TextTertiary.copy(alpha = 0.4f),
                        animationSpec = tween(300),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Next / Get Started button
            Button(
                onClick = {
                    if (isLastPage) {
                        onFinished()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal,
                    contentColor = TextOnAccent
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isLastPage) "Get Started" else "Next",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (!isLastPage) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WalkthroughPageContent(page: WalkthroughPage) {
    // Landscape: side-by-side layout
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Icon on left
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.linearGradient(page.iconBg),
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.width(48.dp))

        // Text on right
        Column(
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Text(
                text = page.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = page.description,
                fontSize = 16.sp,
                color = TextSecondary,
                lineHeight = 24.sp
            )
        }
    }
}
