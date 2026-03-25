package com.schoolsync.teacher.data.remote

import com.schoolsync.teacher.data.model.FcmRegisterRequest
import com.schoolsync.teacher.data.model.LoginRequest
import com.schoolsync.teacher.data.model.LoginResponse
import com.schoolsync.teacher.data.model.RefreshRequest
import com.schoolsync.teacher.data.model.RefreshResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Map<String, Any>>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/register-fcm")
    suspend fun registerFcmToken(@Body request: FcmRegisterRequest): Response<Map<String, Any>>

    @GET("api/users/me")
    suspend fun getProfile(): Response<Map<String, Any>>
}
