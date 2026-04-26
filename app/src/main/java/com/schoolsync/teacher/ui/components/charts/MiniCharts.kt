package com.schoolsync.teacher.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.ui.theme.LocalAppColors
import kotlin.math.max

/**
 * Zero-dependency Compose chart primitives. Use these inside dashboard
 * cards anywhere a number or trend would otherwise be plain text.
 *
 * - [Sparkline]      → 7-day trend line for attendance / late count
 * - [BarMini]        → 5-bar score distribution / class comparison
 * - [DonutMini]      → present / absent ratio for the day
 * - [ProgressRingMini] → animated % gauge (assignments graded, fees collected)
 */

/**
 * Sparkline — clean line chart with gradient fill below.
 *
 * @param values raw datapoints; empty list renders nothing.
 * @param showDots draw a dot at each datapoint (last point is highlighted).
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    lineColor: Color? = null,
    showDots: Boolean = true,
    smoothing: Float = 0.18f,
) {
    val c = LocalAppColors.current
    val color = lineColor ?: c.accent
    val animated by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "spark-grow"
    )

    Box(modifier = modifier.height(height)) {
        if (values.isEmpty()) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val maxV = values.max()
            val minV = values.min()
            val range = (maxV - minV).coerceAtLeast(0.0001f)

            val pts = values.mapIndexed { i, v ->
                val x = if (values.size == 1) w / 2f else w * i / (values.size - 1)
                val yNorm = (v - minV) / range
                val y = h - (yNorm * (h - 6f) + 3f)
                Offset(x, y)
            }

            // Smooth path (cubic bezier through points)
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val curr = pts[i]
                    val cp1x = prev.x + (curr.x - prev.x) * (0.5f - smoothing)
                    val cp2x = prev.x + (curr.x - prev.x) * (0.5f + smoothing)
                    cubicTo(cp1x, prev.y, cp2x, curr.y, curr.x, curr.y)
                }
            }

            // Fill under line
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, h)
                lineTo(pts.first().x, h)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.0f))
                )
            )

            // Animated stroke reveal — clip via a horizontal "wipe"
            val visibleW = w * animated
            clipRect(right = visibleW) {
                drawPath(
                    path = line,
                    color = color,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            if (showDots && animated > 0.95f) {
                val last = pts.last()
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = last)
                drawCircle(color = color, radius = 3.dp.toPx(), center = last)
            }
        }
    }
}

/** clipRect helper — small Compose Canvas extension. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRect(
    right: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.clipRect(0f, 0f, right, size.height)
        block()
        canvas.restore()
    }
}

/**
 * BarMini — animated mini bar chart. Pass [labels] same length as [values]
 * to render a 1-line caption beneath each bar.
 */
@Composable
fun BarMini(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    barColor: Color? = null,
    labels: List<String>? = null,
) {
    val c = LocalAppColors.current
    val color = barColor ?: c.accent
    val animated by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "bar-grow"
    )

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            if (values.isEmpty()) return@Canvas
            val maxV = max(1f, values.max())
            val barCount = values.size
            val gap = 6.dp.toPx()
            val barW = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
            values.forEachIndexed { i, v ->
                val barH = (v / maxV) * size.height * animated
                val left = i * (barW + gap)
                val top = size.height - barH
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.65f))
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }
        }
        if (!labels.isNullOrEmpty() && labels.size == values.size) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * DonutMini — segments rendered as arcs with a centered label.
 * Pass each segment as (value, color). Total ≠ 100 is fine — values are
 * normalised internally.
 */
@Composable
fun DonutMini(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    strokeWidth: Dp = 10.dp,
    centerLabel: String? = null,
    centerSubLabel: String? = null,
) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val animated by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "donut-grow"
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val sw = strokeWidth.toPx()
            val gap = 1.5f
            var startAngle = -90f
            segments.forEach { (v, color) ->
                val sweep = (v / total) * 360f * animated
                if (sweep > 0.5f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                        topLeft = Offset(sw / 2f, sw / 2f),
                        size = Size(size.width - sw, size.height - sw)
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerLabel != null) {
                Text(
                    text = centerLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (centerSubLabel != null) {
                Text(
                    text = centerSubLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * ProgressRingMini — animated circular progress with a percent label.
 * `progress` must be in 0f..1f.
 */
@Composable
fun ProgressRingMini(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    color: Color? = null,
    showPercent: Boolean = true,
) {
    val c = LocalAppColors.current
    val arcColor = color ?: c.accent
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 800),
        label = "ring"
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val sw = strokeWidth.toPx()
            // Track
            drawArc(
                color = arcColor.copy(alpha = 0.15f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = sw),
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw)
            )
            // Progress
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw)
            )
        }
        if (showPercent) {
            Text(
                text = "${(animated * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
