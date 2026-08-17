package com.schoolsync.teacher.ui.stories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * WhatsApp/Instagram-style SEGMENTED story ring.
 *
 * One arc per story, so an author with three stories shows three arcs around
 * a single circle instead of one unbroken ring. Each arc is coloured on its
 * OWN seen-state: watched stories go grey, unwatched ones stay accented — so
 * a half-watched author reads as half-grey at a glance, which is the whole
 * point of the segmented ring and something a single `hasUnviewed` boolean
 * could never express.
 *
 * Arcs run clockwise from 12 o'clock in the same order the viewer plays them
 * (oldest first), so the arc that greys out is the one you just watched — and
 * it CROSSFADES rather than snapping, which is what makes the transition read
 * as "that one's done" instead of as a redraw.
 *
 * Geometry lives in [StoryRingGeometry] so it can be unit-tested.
 *
 * @param segmentSeen one entry per story, oldest first; true = already viewed.
 */
@Composable
fun SegmentedStoryRing(
    segmentSeen: List<Boolean>,
    unseenBrush: Brush,
    seenColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 62.dp,
    strokeWidth: Dp = 2.5.dp
) {
    // Per-arc crossfade progress: 0 = fully accented, 1 = fully grey. Built
    // with an explicit loop + key(i) because animateFloatAsState is composable
    // and can't be called from a non-composable lambda like map's; key(i)
    // gives each arc a stable identity so its animation survives the list
    // growing when a new story is posted.
    val greyProgress = ArrayList<Float>(segmentSeen.size)
    for (i in segmentSeen.indices) {
        greyProgress += key(i) {
            animateFloatAsState(
                targetValue = if (segmentSeen[i]) 1f else 0f,
                animationSpec = tween(durationMillis = 420),
                label = "storyArcSeen$i"
            ).value
        }
    }

    Canvas(modifier = modifier.size(diameter)) {
        val stroke = strokeWidth.toPx()
        // Inset by half the stroke so the ring is drawn fully INSIDE the box
        // rather than straddling its edge and clipping.
        val d = size.minDimension - stroke
        if (d <= 0f) return@Canvas
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(d, d)
        val radius = d / 2f

        val n = segmentSeen.size
        val arcs = StoryRingGeometry.arcs(n, radius, stroke)
        val cap = if (StoryRingGeometry.useRoundCaps(n)) StrokeCap.Round else StrokeCap.Butt

        arcs.forEachIndexed { i, arc ->
            val grey = greyProgress.getOrElse(i) { if (segmentSeen.getOrNull(i) == true) 1f else 0f }
            val style = Stroke(width = stroke, cap = cap)
            // Stack both paints and crossfade their alphas: a Brush can't be
            // lerped toward a Color, but two arcs at complementary alpha read
            // as one arc changing colour.
            if (grey < 1f) {
                drawArc(
                    brush = unseenBrush, alpha = 1f - grey,
                    startAngle = arc.startAngle, sweepAngle = arc.sweepAngle, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = style
                )
            }
            if (grey > 0f) {
                drawArc(
                    color = seenColor, alpha = grey,
                    startAngle = arc.startAngle, sweepAngle = arc.sweepAngle, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = style
                )
            }
        }
    }
}
