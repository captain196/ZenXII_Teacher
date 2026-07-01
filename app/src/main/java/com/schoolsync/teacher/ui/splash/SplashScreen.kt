package com.schoolsync.teacher.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.R
import com.schoolsync.teacher.ui.theme.BgEnd
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import kotlinx.coroutines.delay

// Splash accent — aligned to the "Soft Blue & Cloud" palette accent (#3E5F8A)
// so the splash matches the (blue) login screen that follows it. Was a legacy
// brand green (#2DB87A) that clashed with the rebranded palette.
private val ZenXiiAccent = Color(0xFF3E5F8A)

@Composable
fun SplashScreen(
    onNavigateToWalkthrough: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToForceChange: () -> Unit,
    isLoggedIn: Boolean,
    hasSeenOnboarding: Boolean,
    mustChangePassword: Boolean,
) {
    var phase by remember { mutableIntStateOf(0) }

    // ── Logo animations ──
    val logoScale by animateFloatAsState(
        targetValue = when (phase) { 0 -> 0.2f; 1 -> 1.1f; else -> 1f },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(400), label = "logoAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(500), label = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 20f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "titleOffset"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(400), label = "subAlpha"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(400, delayMillis = 150), label = "tagAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowRadius"
    )

    LaunchedEffect(Unit) {
        delay(300)
        phase = 1
        delay(800)
        phase = 2
        delay(500)
        phase = 3
        delay(1000)
        when {
            isLoggedIn && mustChangePassword -> onNavigateToForceChange()
            isLoggedIn -> onNavigateToMain()
            !hasSeenOnboarding -> onNavigateToWalkthrough()
            else -> onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BgStart, BgEnd, BgStart)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Glow
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .alpha(if (phase >= 1) glowAlpha else 0f)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZenXiiAccent.copy(alpha = 0.5f),
                                ZenXiiAccent.copy(alpha = 0f)
                            ),
                            center = center,
                            radius = size.minDimension / 2f * glowRadius
                        )
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.zenxii_logo),
                    contentDescription = "ZenXii",
                    modifier = Modifier
                        .size(140.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "ZenXii",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffsetY.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SCHOOL MANAGEMENT",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Teacher Portal",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ZenXiiAccent,
                modifier = Modifier.alpha(taglineAlpha)
            )
        }
    }
}
