package com.schoolsync.teacher

import com.schoolsync.teacher.util.LocaleManager
import android.content.Context
import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.schoolsync.teacher.service.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SchoolSyncApp : Application() {

    /**
     * Apply the chosen language to the Application context, so
     * context.getString() is correct in ViewModels, services and any
     * non-Activity code path too.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }
    override fun onCreate() {
        super.onCreate()
        com.schoolsync.teacher.util.initDebugLog(this)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
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
