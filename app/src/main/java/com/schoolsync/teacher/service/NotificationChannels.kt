package com.schoolsync.teacher.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.schoolsync.teacher.R

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
    const val TIMETABLE = "ch_timetable"
    const val GALLERY = "ch_gallery"
    const val STORIES = "ch_stories"

    // Name/description are held as @StringRes, never as String. A String captured
    // at object-init time would freeze whatever language the process launched in
    // and survive recreate() — the single most repeated i18n bug in this codebase.
    // They are resolved inside ensureChannels(), against the caller's Context.
    private data class Def(val id: String, @StringRes val name: Int, @StringRes val description: Int)

    private val CHANNELS = listOf(
        Def(GENERAL, R.string.nch_general, R.string.nch_general_desc),
        Def(NOTICES, R.string.nch_notices, R.string.nch_notices_desc),
        Def(MESSAGES, R.string.nch_messages, R.string.nch_messages_desc),
        Def(ATTENDANCE, R.string.nch_attendance, R.string.nch_attendance_desc),
        Def(LEAVE, R.string.nch_leave, R.string.nch_leave_desc),
        Def(EVENTS, R.string.nch_events, R.string.nch_events_desc),
        Def(TIMETABLE, R.string.nch_timetable, R.string.nch_timetable_desc),
        Def(GALLERY, R.string.nch_gallery, R.string.nch_gallery_desc),
        Def(STORIES, R.string.nch_stories, R.string.nch_stories_desc),
    )

    /**
     * Create every channel (idempotent — re-creating an existing channel with
     * the same id is a no-op that only refreshes name/description). Safe to call
     * on every service/app start.
     *
     * Also call this right after a language change: the OS caches channel
     * names at creation time, so the tray keeps showing the old language until
     * the channel is re-created. [context] must be the locale-wrapped one.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        CHANNELS.forEach { def ->
            val channel = NotificationChannel(
                def.id,
                context.getString(def.name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(def.description)
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
        "substitute_assigned", "timetable_changed" -> TIMETABLE
        "gallery_added" -> GALLERY
        "story", "story_created" -> STORIES
        else -> GENERAL
    }
}
