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

            // Start from the cached flag (fail-safe when offline).
            var mustChange = if (loggedIn) tokenManager.mustChangePassword.first() else false

            // Force-refresh the Firebase ID token on cold start so security-
            // rules claims (school_id, role) are current. Custom claims set
            // AFTER the last fresh login only reach the token on refresh; a
            // restored session with a stale token has no school_id claim, so
            // tenantActive()/isSameSchool() reject every read with
            // PERMISSION_DENIED until the token auto-refreshes.
            //
            // WATERTIGHT (2026-07-20): we also RE-DERIVE must_change_password
            // from the freshly-refreshed claims and re-cache it, so an admin-
            // initiated reset (which re-sets the claim server-side after login)
            // is detected on the NEXT cold start instead of being silently
            // ignored until the next full interactive login. Offline / refresh
            // failure keeps the cached value (a cached `true` still gates; we
            // never fall open).
            if (loggedIn) {
                try {
                    val tokenResult = com.google.firebase.auth.FirebaseAuth.getInstance()
                        .currentUser?.getIdToken(true)?.await()
                    if (tokenResult != null) {
                        val freshMustChange = when (val v = tokenResult.claims["must_change_password"]) {
                            is Boolean -> v
                            is String  -> v.equals("true", ignoreCase = true)
                            else       -> false
                        }
                        if (freshMustChange != mustChange) {
                            tokenManager.saveMustChangePassword(freshMustChange)
                            mustChange = freshMustChange
                        }
                    }
                } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    // Session is dead — the account was disabled/deleted or its refresh
                    // token was revoked (e.g. by an admin password reset). Sign out
                    // cleanly and route to Login instead of leaving a logged-in-but-
                    // broken state that fails every Firestore read with PERMISSION_DENIED.
                    // (Transient offline failures throw FirebaseNetworkException and fall
                    // through to the generic catch, which keeps the cached session.)
                    try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) { }
                    tokenManager.clearAll()
                    _state.value = SplashState(
                        isLoading = false,
                        isLoggedIn = false,
                        hasSeenOnboarding = seenOnboarding,
                        mustChangePassword = false,
                    )
                    return@launch
                } catch (_: Exception) { /* offline / transient: keep cached value, do not fall open */ }
            }
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
