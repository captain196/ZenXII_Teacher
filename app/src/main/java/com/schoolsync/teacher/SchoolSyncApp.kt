package com.schoolsync.teacher

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.schoolsync.teacher.service.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SchoolSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.schoolsync.teacher.util.initDebugLog(this)
        // Firestore-only (ZERO-RTDB policy): no Realtime Database persistence.
        // Enable Firestore persistent local cache so notices (and every other
        // Firestore read) render offline / on cold start from disk. MUST be set
        // before any Firestore access — the Application onCreate runs before any
        // Activity/repository touches it. Idempotent-safe via runCatching so a
        // late call after first use can't crash the process.
        runCatching {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder().build()
                    )
                    .build()
        }
        // Create all notification channels at process start so they exist
        // (and persist through force-stop) before any push arrives — a
        // notification-payload message delivered while the app is killed is
        // shown by the OS against the manifest default channel WITHOUT running
        // the app, so the channel must already exist. Idempotent.
        NotificationChannels.ensureChannels(this)
    }
}
