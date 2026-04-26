package com.schoolsync.teacher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.schoolsync.teacher.util.DeepLinkBridge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.ui.navigation.AppNavGraph
import com.schoolsync.teacher.ui.navigation.Route
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SchoolSyncTeacherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — app proceeds either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        publishDeepLinkFromIntent(intent)

        // Immersive landscape -- hide system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            val themeMode by tokenManager.themeMode.collectAsState(initial = "system")
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark  // "system" follows OS
            }

            SchoolSyncTeacherTheme(darkTheme = isDark) {
                GradientBackground {
                    val navController = rememberNavController()

                    // Determine start destination based on saved auth state
                    // For now, default to login; TokenManager check will be added
                    // when data layer is wired up
                    var startDestination by remember { mutableStateOf(Route.Splash.route) }

                    AppNavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishDeepLinkFromIntent(intent)
    }

    /**
     * Map FCM intent extras (set by FCMService.showNotification) onto a
     * Route-string that AppNavGraph consumes via DeepLinkBridge. Quiet
     * no-op when extras don't contain a supported 'type'.
     */
    private fun publishDeepLinkFromIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type") ?: return
        val target = when (type) {
            "event", "event_created"                           -> "events"
            "notice", "notice_created",
            "circular", "circular_created"                     -> "notices"
            "message"                                          -> "messages"
            "attendance_reminder"                              -> "attendance"
            "leave_update"                                     -> "leave"
            else -> null
        } ?: return
        DeepLinkBridge.publish(target)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
