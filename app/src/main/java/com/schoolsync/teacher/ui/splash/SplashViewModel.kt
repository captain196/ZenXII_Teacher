package com.schoolsync.teacher.ui.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SplashState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val mustChangePassword: Boolean = false,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SplashState())
    val state = _state.asStateFlow()

    private val prefs by lazy {
        context.getSharedPreferences("schoolsync_onboarding", Context.MODE_PRIVATE)
    }

    init {
        viewModelScope.launch {
            val loggedIn = tokenManager.isLoggedIn.first()
            val seenOnboarding = prefs.getBoolean("onboarding_seen", false)

            // Force-refresh the Firebase ID token on cold start so security-
            // rules claims (school_id, role) are current. Custom claims set
            // AFTER the last fresh login only reach the token on refresh; a
            // restored session with a stale token has no school_id claim, so
            // tenantActive()/isSameSchool() reject every read with
            // PERMISSION_DENIED until the token auto-refreshes. Best-effort.
            if (loggedIn) {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                        .currentUser?.getIdToken(true)?.await()
                } catch (_: Exception) { }
            }

            val mustChange = if (loggedIn) tokenManager.mustChangePassword.first() else false
            _state.value = SplashState(
                isLoading = false,
                isLoggedIn = loggedIn,
                hasSeenOnboarding = seenOnboarding,
                mustChangePassword = mustChange,
            )
        }
    }

    fun markOnboardingSeen() {
        prefs.edit().putBoolean("onboarding_seen", true).apply()
    }
}
