package com.schoolsync.teacher.ui.theme

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private fun buildDarkScheme(c: AppColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.textOnAccent,
    primaryContainer = c.accentSecondary,
    onPrimaryContainer = c.accent,
    secondary = c.slateBluePrimary,
    onSecondary = c.textPrimary,
    secondaryContainer = c.slateBlueLight,
    onSecondaryContainer = c.textPrimary,
    tertiary = c.info,
    onTertiary = c.textPrimary,
    background = c.bgStart,
    onBackground = c.textPrimary,
    surface = c.surfaceCard,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceElevated,
    onSurfaceVariant = c.textSecondary,
    error = c.error,
    onError = c.textPrimary,
    errorContainer = c.errorSurface,
    onErrorContainer = c.error,
    outline = c.glassBorder,
    outlineVariant = c.divider,
    inverseSurface = c.textPrimary,
    inverseOnSurface = c.bgStart,
    surfaceTint = c.accent
)

private fun buildLightScheme(c: AppColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = Color.White,
    primaryContainer = c.accentSurface,
    onPrimaryContainer = c.accent,
    secondary = c.slateBluePrimary,
    onSecondary = Color.White,
    secondaryContainer = c.accentSurface,
    onSecondaryContainer = c.textPrimary,
    tertiary = c.info,
    onTertiary = Color.White,
    background = c.bgMid,
    onBackground = c.textPrimary,
    surface = c.surfaceElevated,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceDark,
    onSurfaceVariant = c.textSecondary,
    error = c.error,
    onError = Color.White,
    errorContainer = c.errorSurface,
    onErrorContainer = c.error,
    outline = c.glassBorder,
    outlineVariant = c.divider,
    inverseSurface = c.textPrimary,
    inverseOnSurface = c.bgStart,
    surfaceTint = c.accent
)

@Composable
fun SchoolSyncTeacherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) TeacherDarkColors else TeacherLightColors
    val colorScheme = if (darkTheme) buildDarkScheme(appColors) else buildLightScheme(appColors)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = appColors.statusBarColor.toArgb()
            window.navigationBarColor = appColors.statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = appColors.lightStatusBar
                isAppearanceLightNavigationBars = appColors.lightStatusBar
            }
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// ---- Glass Card Modifiers (theme-aware) ----

/**
 * Glass-morphism card modifier.
 * Reads defaults from [LocalAppColors] for theme-awareness.
 * Explicit [borderColor] and [backgroundColor] can still be passed for
 * screens that compute their own colours (e.g. selection highlight).
 */
@Composable
fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    borderColor: Color = LocalAppColors.current.glassBorder,
    backgroundColor: Color = LocalAppColors.current.glass
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(cornerRadius))

/** Theme-aware gradient background modifier. */
@Composable
fun Modifier.gradientBackground(): Modifier {
    val c = LocalAppColors.current
    return this
        .background(c.bgStart)
        .drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(c.bgStart, c.bgMid, c.bgEnd, c.bgStart),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
        }
}

// ---- Backward-compatible composable ----

/** Full-screen gradient background used behind all screens (legacy). */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(c.bgStart, c.bgEnd),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            ),
        content = content
    )
}
