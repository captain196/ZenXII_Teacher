package com.schoolsync.teacher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.schoolsync.teacher.data.local.offline.OfflineAttendanceQueue
import com.schoolsync.teacher.service.NotificationChannels
import com.schoolsync.teacher.util.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SchoolSyncApp : Application(), Configuration.Provider {

    // C1 — Hilt-aware WorkManager so @HiltWorker AttendanceFlushWorker can inject
    // the app's ApiService/DAO. Requires the default WorkManagerInitializer to be
    // removed in AndroidManifest (see the <provider> tools:node="remove").
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var offlineQueue: OfflineAttendanceQueue

    @Inject lateinit var networkMonitor: NetworkMonitor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // C1 DEVICE-UAT ONLY: route Firebase to the local emulators when this is a
        // debug build launched with -PUAT_EMULATOR=<host> (paired with `adb reverse`
        // on 8192/9199/8055). Guarded by BuildConfig.DEBUG AND a non-empty host, so
        // no release build can ever point at an emulator. MUST run before any other
        // Firebase access below.
        if (BuildConfig.DEBUG && BuildConfig.EMULATOR_HOST.isNotBlank()) {
            val host = BuildConfig.EMULATOR_HOST
            runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().useEmulator(host, 9199)
                com.google.firebase.firestore.FirebaseFirestore.getInstance().useEmulator(host, 8192)
                android.util.Log.w("SchoolSyncApp", "C1 UAT: Firebase wired to emulator @ $host (auth 9199 / firestore 8192)")
            }
        }

        // C1 — recover any offline attendance stranded mid-send by a killed
        // process and flush what is waiting. No-op while the feature flag is off.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            runCatching { offlineQueue.recoverOnStart() }
        }
        // C1 — when connectivity returns, re-check the queue and schedule a flush
        // if none is pending. (A flush already enqueued/in backoff is kept by
        // ExistingWorkPolicy.KEEP — WorkManager's CONNECTED constraint already
        // releases it on reconnect; this only covers the "nothing scheduled yet"
        // case.) No-op while the flag is off.
        appScope.launch {
            networkMonitor.isOnline.distinctUntilChanged().collect { online ->
                if (online) runCatching { offlineQueue.onConnectivityRestored() }
            }
        }
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
