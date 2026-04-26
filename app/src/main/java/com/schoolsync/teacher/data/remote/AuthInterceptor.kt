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

        // Get Firebase ID token for authenticated requests
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
                .addHeader("Authorization", "Bearer $idToken")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
