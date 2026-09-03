package com.schoolsync.teacher.ui.splash

import androidx.compose.ui.platform.LocalContext
import com.schoolsync.teacher.util.LocaleManager
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
import androidx.compose.ui.res.stringResource
import java.util.Locale

// Splash accent — aligned to the "Soft Blue & Cloud" palette accent (#3E5F8A)
// so the splash matches the (blue) login screen that follows it. Was a legacy
// brand green (#2DB87A) that clashed with the rebranded palette.
private val ZenXiiAccent = Color(0xFF3E5F8A)

@Composable
fun SplashScreen(
    onNavigateToWalkthrough: () -> Unit,
    onNavigateToLanguageSetup: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToForceChange: () -> Unit,
    isLoggedIn: Boolean,
    hasSeenOnboarding: Boolean,
    mustChangePassword: Boolean,
) {
    // Hoisted: the routing decision below runs inside a LaunchedEffect, which
    // is not a composable scope.
    val context = LocalContext.current
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
            // Language is checked BEFORE isLoggedIn, deliberately.
            //
            // Staff are already logged in when they update. If the login branch
            // ran first, every existing user would go straight to the dashboard
            // and NEVER be shown the language chooser — the whole existing user
            // base would miss the feature and only fresh installs would see it.
            // The choice is one-time and sticky, so that first exposure is the
            // entire opportunity.
            //
            // Fires only while no explicit choice exists: shown once per
            // install, never on logout or relaunch. Afterwards we route back
            // through Splash so this decision lives in one place.
            !LocaleManager.hasExplicitChoice(context) -> onNavigateToLanguageSetup()
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

            // The 4sp tracking is a Latin-caps flourish. Applied to Tamil,
            // Telugu or Devanagari it prises apart the conjuncts and the word
            // reads as loose syllables — so it is only used for Latin script.
            val latinScript = Locale.getDefault().language in setOf("en")
            Text(
                text = stringResource(R.string.splash_school_management),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = if (latinScript) 4.sp else 0.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.splash_teacher_portal),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ZenXiiAccent,
                modifier = Modifier.alpha(taglineAlpha)
            )
        }
    }
}
