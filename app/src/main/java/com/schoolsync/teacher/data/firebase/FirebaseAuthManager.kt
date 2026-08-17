package com.schoolsync.teacher.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Firebase Authentication via email/password.
 * Uses synthetic emails: {userId}@schoolsync.app
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    companion object {
        private const val EMAIL_DOMAIN = "schoolsync.app"
    }

    /**
     * Sign in to Firebase using userId + password.
     * Constructs synthetic email as {userId.lowercase()}@schoolsync.app
     *
     * @param userId The teacher ID (e.g., "STA0001")
     * @param password The password
     * @return The signed-in FirebaseUser, or null on failure
     */
    suspend fun signInWithEmailAndPassword(userId: String, password: String): FirebaseUser? {
        val email = "${userId.lowercase()}@$EMAIL_DOMAIN"
        return suspendCancellableCoroutine { cont ->
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    if (cont.isActive) cont.resume(authResult.user)
                }
                .addOnFailureListener { exception ->
                    if (cont.isActive) cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Get the ID token result with custom claims (role, school_id, etc.).
     */
    suspend fun getIdTokenResult(forceRefresh: Boolean = false): GetTokenResult {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user")
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { exception ->
                    if (cont.isActive) cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Change the current user's password.
     */
    suspend fun changePassword(newPassword: String) {
        val user = firebaseAuth.currentUser
            ?: throw IllegalStateException("No authenticated user")
        return suspendCancellableCoroutine { cont ->
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
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
     * Observe Firebase auth state as a Flow, emitting the current FirebaseUser
     * (or null) on every change.
     *
     * Consumed by SessionGuardViewModel: when Firebase drops `currentUser`
     * mid-session — a revoked refresh token after an admin password reset, or a
     * disabled/deleted account — the app must end the session rather than sit in
     * a dead one where every Firestore read fails with PERMISSION_DENIED.
     * (The Parent app already had this; Teacher did not.)
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
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
