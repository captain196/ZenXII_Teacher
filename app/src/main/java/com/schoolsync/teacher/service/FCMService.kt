package com.schoolsync.teacher.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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
        private const val CHANNEL_ID = "school_sync_channel"
        private const val CHANNEL_NAME = "SchoolSync Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications from SchoolSync Teacher app"
    }

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var tokenManager: TokenManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

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

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            handleDataPayload(remoteMessage.data)
        }

        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: "SchoolSync"
            val body = notification.body ?: ""
            showNotification(title, body, remoteMessage.data)
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"] ?: return

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
            "event", "event_created" -> {
                // Backend sends title = "New Event: {…}", body = "{startDate} | {location}".
                // Keep the server-provided text, fall back if either is missing.
                val title = data["title"] ?: "New Event"
                val body  = data["body"]  ?: "Tap to view details"
                showNotification(title, body, data)
            }
            else -> {
                val title = data["title"] ?: "SchoolSync"
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

        // Create channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
