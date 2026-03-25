package com.schoolsync.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
