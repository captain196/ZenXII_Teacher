package com.schoolsync.teacher

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.schoolsync.teacher.service.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SchoolSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.schoolsync.teacher.util.initDebugLog(this)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        // Create all notification channels at process start so they exist
        // (and persist through force-stop) before any push arrives — a
        // notification-payload message delivered while the app is killed is
        // shown by the OS against the manifest default channel WITHOUT running
        // the app, so the channel must already exist. Idempotent.
        NotificationChannels.ensureChannels(this)
    }
}
