package com.schoolsync.teacher.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.schoolsync.teacher.MainActivity
import com.schoolsync.teacher.R
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var tokenManager: TokenManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        // Create all channels up front so the manifest default_notification_channel_id
        // always resolves (backgrounded notification-payload pushes rely on it) and
        // per-category muting works. Idempotent.
        NotificationChannels.ensureChannels(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        serviceScope.launch {
            try {
                val deviceId = tokenManager.deviceId.firstOrNull() ?: ""
                val userId = tokenManager.userId.firstOrNull() ?: ""
                if (deviceId.isNotBlank() && userId.isNotBlank()) {
                    authRepository.registerFcmToken(token, userId, deviceId)
                    Log.d(TAG, "FCM token registered successfully")
                } else {
                    Log.w(TAG, "No device ID or user ID available, skipping FCM registration")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // If the message carries a notification block, show exactly that and
        // stop — otherwise running the data-type switch as well double-posts the
        // same push. Data-only messages fall through to handleDataPayload.
        val notification = remoteMessage.notification
        if (notification != null) {
            showNotification(
                notification.title ?: "ZenXii",
                notification.body ?: "",
                remoteMessage.data
            )
            return
        }

        if (remoteMessage.data.isNotEmpty()) {
            // Log only the shape (type + keys), never values — payloads can carry
            // names/messages/PII that must not land in logcat.
            Log.d(TAG, "Data payload type=${remoteMessage.data["type"]} keys=${remoteMessage.data.keys}")
            handleDataPayload(remoteMessage.data)
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        // Empty (not early-return) so a data-only push that omits `type` but
        // carries title/body still shows via the else branch instead of being
        // silently dropped.
        val type = data["type"] ?: ""

        when (type) {
            "notice", "notice_created" -> {
                val title = data["title"] ?: "New Notice"
                val body = data["body"] ?: "A new notice has been posted"
                showNotification(title, body, data)
            }
            "circular", "circular_created" -> {
                val title = data["title"] ?: "New Circular"
                val body = data["body"] ?: "A new circular has been posted"
                showNotification(title, body, data)
            }
            "message" -> {
                val senderName = data["senderName"] ?: "New Message"
                val message = data["message"] ?: ""
                showNotification("Message from $senderName", message, data)
            }
            "attendance_reminder" -> {
                showNotification(
                    "Attendance Reminder",
                    data["body"] ?: "Don't forget to mark today's attendance",
                    data
                )
            }
            "leave_update" -> {
                val status = data["status"] ?: ""
                showNotification(
                    "Leave Request $status",
                    data["body"] ?: "Your leave request has been updated",
                    data
                )
            }
            // Phase 4 cross-system: explicit handling for HR's leave decisions.
            // Server sends title/body in the data payload; we surface them
            // with the right routing and a clear notification id.
            "leave_approved" -> {
                showNotification(
                    data["title"] ?: "Leave Approved",
                    data["body"] ?: "Your leave request has been approved.",
                    data
                )
            }
            "leave_rejected" -> {
                showNotification(
                    data["title"] ?: "Leave Rejected",
                    data["body"] ?: "Your leave request has been rejected.",
                    data
                )
            }
            "event", "event_created" -> {
                // Backend sends title = "New Event: {…}", body = "{startDate} | {location}".
                // Keep the server-provided text, fall back if either is missing.
                val title = data["title"] ?: "New Event"
                val body  = data["body"]  ?: "Tap to view details"
                showNotification(title, body, data)
            }
            // Story pushes normally target parents only (the universal dispatcher's
            // STORY_POSTED audience is parents), so a teacher rarely sees this — but
            // handle it explicitly so it shows on the STORIES channel with sensible
            // text instead of falling through to the generic else.
            "story", "story_created" -> {
                val title = data["title"] ?: "New Story"
                val body  = data["body"]  ?: "A new story was posted. Tap to view."
                showNotification(title, body, data)
            }
            else -> {
                val title = data["title"] ?: "ZenXii"
                val body = data["body"] ?: ""
                if (title.isNotEmpty() || body.isNotEmpty()) {
                    showNotification(title, body, data)
                }
            }
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channels are created in onCreate; resolve the per-category channel for
        // this payload (falls back to GENERAL for unknown/missing types).
        val channelId = NotificationChannels.channelForType(data["type"] ?: data["mark"])

        // Create intent
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_zenxii)
            .setColor(ContextCompat.getColor(this, R.color.notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(body)
            )
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
