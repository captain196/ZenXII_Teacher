package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseAuthManager
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.LoginRequest
import com.schoolsync.teacher.data.model.LoginResponse
import com.schoolsync.teacher.data.model.RefreshRequest
import com.schoolsync.teacher.data.remote.ApiService
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles authentication: login, token refresh, logout, Firebase sign-in.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firebaseService: FirebaseService
) {
    /**
     * Login with userId, password, and deviceId.
     * On success:
     * 1. Saves tokens to DataStore
     * 2. Saves teacher profile
     * 3. Resolves Firebase school code from Indexes/School_codes
     * 4. Signs in to Firebase with custom token
     */
    suspend fun login(
        userId: String,
        password: String,
        deviceId: String
    ): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(userId, password, deviceId))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    // Save tokens
                    tokenManager.saveTokens(
                        accessToken = body.accessToken ?: "",
                        refreshToken = body.refreshToken ?: "",
                        firebaseToken = body.firebaseToken
                    )

                    // Save teacher profile
                    body.user?.let { user ->
                        tokenManager.saveProfile(user)

                        // Resolve Firebase school code from Indexes
                        val schoolCode = resolveSchoolCode(user.schoolId)
                        if (schoolCode != null) {
                            tokenManager.saveSchoolCode(schoolCode)

                            // Resolve active academic session from Firebase
                            val session = resolveActiveSession(schoolCode)
                            if (session != null) {
                                tokenManager.saveSession(session)
                            }
                        }
                    }

                    // Save device ID
                    tokenManager.saveDeviceId(deviceId)

                    // Sign in to Firebase
                    body.firebaseToken?.let { fbToken ->
                        try {
                            firebaseAuthManager.signInWithCustomToken(fbToken)
                        } catch (e: Exception) {
                            // Firebase sign-in failure is non-fatal for login
                            // The app can retry later
                        }
                    }

                    Result.success(body)
                } else {
                    Result.failure(
                        Exception(body?.message ?: "Login failed")
                    )
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Login failed (${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh auth tokens. Called automatically by AuthInterceptor on 401,
     * but can also be called manually.
     */
    suspend fun refreshTokens(): Result<Unit> {
        return try {
            val currentRefreshToken = tokenManager.refreshToken.firstOrNull()
                ?: return Result.failure(Exception("No refresh token available"))

            val response = apiService.refreshToken(RefreshRequest(currentRefreshToken))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    tokenManager.saveTokens(
                        accessToken = body.accessToken ?: "",
                        refreshToken = body.refreshToken ?: currentRefreshToken,
                        firebaseToken = body.firebaseToken
                    )

                    // Re-sign into Firebase with new token
                    body.firebaseToken?.let { fbToken ->
                        try {
                            firebaseAuthManager.signInWithCustomToken(fbToken)
                        } catch (_: Exception) { }
                    }

                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Token refresh failed"))
                }
            } else {
                Result.failure(Exception("Token refresh failed (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logout: revoke tokens on server, sign out Firebase, clear local storage.
     */
    suspend fun logout(): Result<Unit> {
        return try {
            // Best-effort server logout
            try {
                apiService.logout()
            } catch (_: Exception) { }

            // Sign out Firebase
            firebaseAuthManager.signOut()

            // Clear all local data
            tokenManager.clearAll()

            Result.success(Unit)
        } catch (e: Exception) {
            // Even on error, ensure local cleanup
            firebaseAuthManager.signOut()
            tokenManager.clearAll()
            Result.failure(e)
        }
    }

    /**
     * Change password.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val response = apiService.changePassword(
                mapOf(
                    "currentPassword" to currentPassword,
                    "newPassword" to newPassword
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.errorBody()?.string() ?: "Password change failed")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve the Firebase school code from Indexes/School_codes/{mongoSchoolId}.
     * Returns the Firebase RTDB key for the school.
     */
    private suspend fun resolveSchoolCode(mongoSchoolId: String): String? {
        return try {
            val path = "${Constants.Firebase.SCHOOL_CODES_INDEX}/$mongoSchoolId"
            firebaseService.readValue<String>(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve the active academic session from Schools/{schoolCode}/Config/ActiveSession.
     */
    private suspend fun resolveActiveSession(schoolCode: String): String? {
        return try {
            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/Config/ActiveSession"
            firebaseService.readValue<String>(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Register FCM token with the server.
     */
    suspend fun registerFcmToken(fcmToken: String, deviceId: String): Result<Unit> {
        return try {
            val response = apiService.registerFcmToken(
                com.schoolsync.teacher.data.model.FcmRegisterRequest(fcmToken, deviceId)
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("FCM registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
