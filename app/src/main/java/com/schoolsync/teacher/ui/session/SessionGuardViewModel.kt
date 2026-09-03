package com.schoolsync.teacher.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.schoolsync.teacher.data.firebase.FirebaseAuthManager
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

/**
 * Mid-session enforcement for credential changes. Mirror of the Parent app's
 * guard, reading the `staff` mirror instead of `students`.
 *
 * This matters more for staff than for parents, because **staff sign in on two
 * surfaces**: the Teacher app and the admin panel. Before this, an admin who
 * reset a staff password affected neither a running app (the flag was only read
 * at cold start) nor a live web session (the panel gate reads a CI session value
 * seeded once at login). The account stayed fully usable on both.
 *
 * OWASP session management (ASVS V3.3) requires other sessions to be invalidated
 * on a credential change, and an admin-forced reset is the strongest case — it is
 * done specifically to cut off whoever holds the account. Token auth cannot
 * revoke an already-issued token without a per-request revocation check
 * (Microsoft Entra carries the same ~1h window), so the achievable bar is "the
 * user cannot continue once they next touch the app".
 *
 * Two triggers: app foreground (ON_RESUME), and Firebase dropping `currentUser`.
 * Transient/offline failures keep the session — it never invents a logout.
 */
@HiltViewModel
class SessionGuardViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firestoreService: FirestoreService,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object { private const val TAG = "SessionGuard" }

    private val _sessionEnded = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits a user-facing reason; the host navigates to Login. */
    val sessionEnded = _sessionEnded.asSharedFlow()

    private var running = false

    /**
     * Set once a session has been ended, so the two triggers (auth-state and the
     * foreground re-check) cannot both fire and stack two toasts / two Login
     * navigations. Re-armed on the next successful sign-in, because this
     * ViewModel outlives a logout→login cycle within one app run.
     */
    private var ended = false

    init {
        viewModelScope.launch {
            firebaseAuthManager.observeAuthState().collect { firebaseUser ->
                if (firebaseUser != null) {
                    ended = false            // fresh sign-in — re-arm the guard
                } else if (tokenManager.isLoggedIn.first()) {
                    Log.w(TAG, "Firebase dropped currentUser while still signed in — ending session")
                    end(appContext.localizedString(R.string.vm_session_ended))
                }
            }
        }
    }

    /** Call on app foreground. Cheap no-op when signed out or already gated. */
    fun recheck() {
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                if (!tokenManager.isLoggedIn.first()) return@launch

                // Already inside the legitimate force-change flow: the user signed
                // in WITH the flag and the navigation gate owns them. Re-checking
                // here would log them out mid-way through setting a password.
                if (tokenManager.mustChangePassword.first()) return@launch

                val tokenResult = try {
                    FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
                } catch (e: FirebaseAuthInvalidUserException) {
                    Log.w(TAG, "token refresh rejected — ending session", e)
                    end(appContext.localizedString(R.string.vm_session_ended))
                    return@launch
                } catch (e: Exception) {
                    Log.d(TAG, "token refresh failed transiently; keeping session")
                    return@launch
                }

                val claimMustChange = when (val v = tokenResult?.claims?.get("must_change_password")) {
                    is Boolean -> v
                    is String  -> v.equals("true", ignoreCase = true)
                    else       -> false
                }

                // The Firestore mirror is written alongside the claim by every
                // reset site, so read it too: a claim that has not propagated to
                // this device yet must not be able to hide a reset.
                var docMustChange = false
                val userId = tokenManager.userId.firstOrNull()
                val schoolId = tokenManager.schoolId.firstOrNull()
                if (!userId.isNullOrBlank() && !schoolId.isNullOrBlank()) {
                    try {
                        val doc = firestoreService.getDocumentMap(
                            Constants.Firestore.STAFF, "${schoolId}_$userId"
                        )
                        // Untyped map: the field can arrive as a Boolean or, if it
                        // round-tripped through JSON, as a String. Normalise both
                        // rather than `== true`, which silently misses "true".
                        docMustChange = when (val v = doc?.get("mustChangePassword")) {
                            is Boolean -> v
                            is String  -> v.equals("true", ignoreCase = true)
                            else       -> false
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "mirror re-check failed; relying on the claim", e)
                    }
                }

                if (claimMustChange || docMustChange) {
                    Log.w(TAG, "password reset detected mid-session — ending session")
                    end(appContext.localizedString(R.string.vm_password_reset_by_school))
                }
            } finally {
                running = false
            }
        }
    }

    private suspend fun end(message: String) {
        if (ended) return
        ended = true
        // clearAll BEFORE signOut. signOut() trips observeAuthState, and its
        // collector re-enters here while isLoggedIn is still true — a second
        // toast and a second Login navigation. Clearing first makes that
        // collector a no-op; the `ended` flag covers the remaining race.
        try { tokenManager.clearAll() } catch (_: Exception) {}
        try { firebaseAuthManager.signOut() } catch (_: Exception) {}
        _sessionEnded.emit(message)
    }
}
