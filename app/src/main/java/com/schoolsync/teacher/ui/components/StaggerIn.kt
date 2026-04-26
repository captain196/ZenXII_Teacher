package com.schoolsync.teacher.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Subtle stagger-on-first-composition for list items.
 * 200ms fade + 16px slide-up, ~30ms per-item delay capped at 8 items.
 */
fun Modifier.staggerIn(
    index: Int,
    perItemDelayMs: Int = 30,
    maxStaggeredItems: Int = 8,
    durationMs: Int = 200,
    translateYpx: Float = 16f,
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    val cappedIndex = index.coerceAtMost(maxStaggeredItems)
    val delay = cappedIndex * perItemDelayMs

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
        label = "stagger"
    )

    this
        .alpha(progress)
        .graphicsLayer {
            translationY = (1f - progress) * translateYpx
        }
}
