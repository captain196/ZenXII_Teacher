package com.schoolsync.teacher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SchoolSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.schoolsync.teacher.util.initDebugLog(this)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "school_sync_channel",
                "SchoolSync Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "School notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
