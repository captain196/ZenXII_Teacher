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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.R
import com.schoolsync.teacher.ui.theme.BgEnd
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.data.lifecycle.SchoolAccess
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
    schoolAccess: SchoolAccess = SchoolAccess.Unknown,
) {
    // ── SCHOOL LIFECYCLE GATE ────────────────────────────────────────
    // When the school itself is not usable — archived, not yet open, or the
    // service is on hold — say so plainly instead of routing the user into an
    // app whose every screen will fail with PERMISSION_DENIED and render as an
    // empty list. Unknown fails OPEN (canUseApp = true), so an unreadable
    // mirror never strands a legitimate user.
    if (isLoggedIn && !schoolAccess.canUseApp) {
        SchoolUnavailableScreen(schoolAccess)
        return
    }

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

/**
 * Shown instead of the app when the school itself is unavailable.
 *
 * Deliberately explains the SCHOOL's situation in the user's terms and never
 * surfaces a Firestore error, a status code, or the words "permission
 * denied" — a teacher whose school has closed is not experiencing a
 * permissions problem, and telling them so would be both confusing and
 * untrue. The wording comes from SchoolAccess, so a commercial hold and a
 * closure can never read the same.
 */
@Composable
private fun SchoolUnavailableScreen(access: SchoolAccess) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.linearGradient(colors = listOf(BgStart, BgEnd, BgStart))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Theme text colours, NOT hardcoded white: BgStart/BgEnd are LIGHT
            // in this palette, so white text rendered all but invisible on a
            // real device. Caught on hardware, not in a unit test.
            Text(
                text = access.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = access.message,
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
