package com.schoolsync.teacher.ui.stories

/**
 * Arc layout for the segmented story ring — pure geometry, no Compose.
 *
 * Extracted from the Composable so the maths is unit-testable: the ring has to
 * stay legible from 1 story to 30, and "does a 30-segment ring still draw
 * visible arcs?" is a question worth answering in a test rather than on a
 * device.
 *
 * Angles are degrees in Compose's `drawArc` convention: 0° points right (3
 * o'clock) and sweeps clockwise, so 12 o'clock is -90°.
 */
object StoryRingGeometry {

    /** Visual gap between adjacent arcs, before the per-segment clamp. */
    const val GAP_DEGREES = 7f
    /** Floor so an arc never vanishes entirely at high story counts. */
    const val MIN_SWEEP_DEGREES = 1.5f
    /** Above this many segments, rounded caps close the gaps — use butt caps. */
    const val ROUND_CAP_MAX_SEGMENTS = 12
    /** A gap may never eat more than this share of its segment. */
    const val MAX_GAP_SHARE = 0.35f

    /** One arc to draw. */
    data class Arc(val startAngle: Float, val sweepAngle: Float)

    /**
     * True when arcs should be drawn with rounded ends. Rounded caps look
     * better but each extends the arc by ~stroke/2 beyond its sweep, which
     * closes the gaps once segments get tight.
     */
    fun useRoundCaps(segmentCount: Int): Boolean =
        segmentCount in 2..ROUND_CAP_MAX_SEGMENTS

    /**
     * Lay out [segmentCount] arcs around a circle.
     *
     * @param radiusPx radius of the arc's centre-line (i.e. already inset by
     *        half the stroke), used to convert the cap overhang to degrees.
     * @return one [Arc] per segment, clockwise from 12 o'clock. A count of 0 or
     *         1 yields a single unbroken 360° circle — WhatsApp draws no gap
     *         for a single status, and one arc with one gap just looks broken.
     */
    fun arcs(segmentCount: Int, radiusPx: Float, strokePx: Float): List<Arc> {
        if (segmentCount <= 1) return listOf(Arc(startAngle = 0f, sweepAngle = 360f))

        val segment = 360f / segmentCount
        val capDegrees = if (useRoundCaps(segmentCount) && radiusPx > 0f) {
            Math.toDegrees((strokePx / 2f / radiusPx).toDouble()).toFloat()
        } else 0f
        val gap = GAP_DEGREES.coerceAtMost(segment * MAX_GAP_SHARE)
        val sweep = (segment - gap - 2f * capDegrees).coerceAtLeast(MIN_SWEEP_DEGREES)

        return List(segmentCount) { i ->
            Arc(
                // -90° puts the first arc at 12 o'clock; half the gap on each
                // side keeps gaps visually centred between neighbours.
                startAngle = -90f + i * segment + gap / 2f + capDegrees,
                sweepAngle = sweep
            )
        }
    }
}
