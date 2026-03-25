package com.schoolsync.teacher.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Firebase Authentication via custom tokens.
 * The server issues a Firebase custom token at login and refresh,
 * which we use to sign in to Firebase for RTDB Security Rules.
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    /**
     * Sign in to Firebase using a custom token from the auth API.
     * Must be called after login and after each token refresh.
     *
     * @param customToken The Firebase custom token from the server
     * @return The signed-in FirebaseUser
     */
    suspend fun signInWithCustomToken(customToken: String): FirebaseUser {
        return suspendCancellableCoroutine { cont ->
            firebaseAuth.signInWithCustomToken(customToken)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null && cont.isActive) {
                        cont.resume(user)
                    } else if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Firebase sign-in succeeded but user is null")
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    if (cont.isActive) cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Sign out from Firebase.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Get current Firebase user, or null if not signed in.
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Check if currently signed in to Firebase.
     */
    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null
}
