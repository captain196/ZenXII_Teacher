package com.schoolsync.teacher.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SchoolSync Teacher spacing tokens — 4dp base grid.
 * Read via [LocalSpacing] inside composables.
 */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,

    // Card / pill radii
    val cardCornerRadius: Dp = 18.dp,
    val pillCornerRadius: Dp = 50.dp,

    // Minimum tappable surface (Material accessibility)
    val touchTarget: Dp = 48.dp,

    // Icon sizing scale
    val iconSm: Dp = 18.dp,
    val iconMd: Dp = 22.dp,
    val iconLg: Dp = 28.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
