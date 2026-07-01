package com.schoolsync.teacher.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches a Firebase ID token as Bearer token
 * to any remaining REST requests (if any).
 */
@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip if the call site already set Authorization explicitly via a
        // Retrofit @Header param — otherwise OkHttp would carry BOTH values
        // and downstream PHP merges them into a comma-joined string that no
        // longer parses as a valid bearer token (caused 401 on
        // /auth/clear_must_change).
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val idToken = runBlocking {
            try {
                FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)
                    ?.await()
                    ?.token
            } catch (_: Exception) {
                null
            }
        }

        val request = if (idToken != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $idToken")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
