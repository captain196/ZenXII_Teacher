package com.schoolsync.teacher.data.remote

import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.RefreshRequest
import com.schoolsync.teacher.data.model.RefreshResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that:
 * 1. Attaches Bearer token to all requests except public paths
 * 2. On 401, attempts a token refresh and retries the original request once
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val gson: Gson
) : Interceptor {

    companion object {
        private val PUBLIC_PATHS = listOf(
            "/api/auth/login",
            "/api/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        if (isPublicPath(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Attach access token
        val accessToken = runBlocking { tokenManager.accessToken.firstOrNull() }
        val authenticatedRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response = chain.proceed(authenticatedRequest)

        // If 401, try to refresh the token
        if (response.code == 401) {
            response.close()

            val newAccessToken = runBlocking { attemptTokenRefresh() }
            if (newAccessToken != null) {
                // Retry with new token
                val retryRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $newAccessToken")
                    .build()
                return chain.proceed(retryRequest)
            }

            // Refresh failed — clear session, return original 401
            runBlocking { tokenManager.clearAll() }
            return chain.proceed(originalRequest)
        }

        return response
    }

    private fun isPublicPath(request: Request): Boolean {
        val path = request.url.encodedPath
        return PUBLIC_PATHS.any { path.contains(it) }
    }

    /**
     * Performs a synchronous token refresh using a raw OkHttpClient
     * (to avoid interceptor recursion).
     * Returns the new access token on success, null on failure.
     */
    private suspend fun attemptTokenRefresh(): String? {
        val refreshToken = tokenManager.refreshToken.firstOrNull() ?: return null
        val baseUrl = tokenManager.baseUrl

        val client = OkHttpClient.Builder().build()
        val body = gson.toJson(RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${baseUrl}api/auth/refresh")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val refreshResponse = gson.fromJson(responseBody, RefreshResponse::class.java)
                if (refreshResponse.success && refreshResponse.accessToken != null) {
                    tokenManager.saveTokens(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken ?: refreshToken,
                        firebaseToken = refreshResponse.firebaseToken
                    )
                    refreshResponse.accessToken
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
