package com.schoolsync.teacher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Centralised haptics so every screen feels the same.
 *
 * Usage:
 * ```
 * val haptics = rememberAppHaptics()
 * Button(onClick = { haptics.medium(); onSubmit() }) { ... }
 * ```
 */
class AppHaptics internal constructor(private val raw: HapticFeedback) {
    fun light() = raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun medium() = raw.performHapticFeedback(HapticFeedbackType.LongPress)
    fun success() = raw.performHapticFeedback(HapticFeedbackType.LongPress)
    fun error() = raw.performHapticFeedback(HapticFeedbackType.LongPress)
    fun navTick() = raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val raw = LocalHapticFeedback.current
    return remember(raw) { AppHaptics(raw) }
}
