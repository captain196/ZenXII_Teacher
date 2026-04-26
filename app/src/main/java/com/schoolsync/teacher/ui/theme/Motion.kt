package com.schoolsync.teacher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * Material 3 motion tokens — easing curves and standard durations.
 * Use [Motion.fast], [Motion.standard], [Motion.emphasized], [Motion.slow]
 * to get a [TweenSpec] instead of writing tween() everywhere.
 */
object Motion {
    // Material 3 easing curves
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** Material 3 emphasized — pronounced curve for hero/content reveal. */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Durations (ms)
    const val DurationFast = 150
    const val DurationStandard = 250
    const val DurationEmphasized = 400
    const val DurationSlow = 500

    fun <T> fast(): TweenSpec<T> = tween(DurationFast, easing = Standard)
    fun <T> standard(): TweenSpec<T> = tween(DurationStandard, easing = Standard)
    fun <T> emphasized(): TweenSpec<T> = tween(DurationEmphasized, easing = Emphasized)
    fun <T> slow(): TweenSpec<T> = tween(DurationSlow, easing = EmphasizedDecelerate)
}
