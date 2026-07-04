package com.schoolsync.teacher.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Canonical notification-channel registry for the Teacher app.
 *
 * Android 8+ requires every posted notification to name a channel that has
 * already been created; posting to an unknown channel is **silently dropped**.
 * Historically the app created a single channel inline inside
 * [FCMService.showNotification], which meant:
 *   1. the app couldn't offer per-category muting, and
 *   2. any FCM `notification`-payload delivered while the app was backgrounded
 *      used the manifest `default_notification_channel_id` — which MUST exist.
 *
 * This object owns all channel ids in one place, creates them eagerly (so the
 * manifest default always resolves), and maps a push `type`/`mark` to the right
 * channel so users can independently silence categories in system settings.
 *
 * [GENERAL] keeps the historical id `school_sync_channel` so it stays the
 * manifest default and existing installs don't accumulate an orphaned channel.
 */
object NotificationChannels {

    /** Fallback + manifest `default_notification_channel_id`. Do NOT rename. */
    const val GENERAL = "school_sync_channel"
    const val NOTICES = "ch_notices"
    const val MESSAGES = "ch_messages"
    const val ATTENDANCE = "ch_attendance"
    const val LEAVE = "ch_leave"
    const val EVENTS = "ch_events"

    private data class Def(val id: String, val name: String, val description: String)

    private val CHANNELS = listOf(
        Def(GENERAL, "General", "General notifications from ZenXii"),
        Def(NOTICES, "Notices & Circulars", "New notices and circulars"),
        Def(MESSAGES, "Messages", "Chat messages"),
        Def(ATTENDANCE, "Attendance", "Attendance reminders and updates"),
        Def(LEAVE, "Leave", "Leave request updates"),
        Def(EVENTS, "Events", "School events"),
    )

    /**
     * Create every channel (idempotent — re-creating an existing channel with
     * the same id is a no-op that only refreshes name/description). Safe to call
     * on every service/app start.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        CHANNELS.forEach { def ->
            val channel = NotificationChannel(
                def.id,
                def.name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = def.description
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Map a push `type` (or raw pushRequests `mark`) to a channel id.
     * Unknown / null types fall back to [GENERAL].
     */
    fun channelForType(type: String?): String = when (type?.lowercase()) {
        "notice", "notice_created", "circular", "circular_created" -> NOTICES
        "message", "message_received" -> MESSAGES
        "attendance_reminder" -> ATTENDANCE
        "leave_update", "leave_approved", "leave_rejected" -> LEAVE
        "event", "event_created" -> EVENTS
        else -> GENERAL
    }
}
