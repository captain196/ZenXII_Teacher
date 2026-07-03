package com.schoolsync.teacher.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("accessToken")
    val accessToken: String?,
    @SerializedName("refreshToken")
    val refreshToken: String?,
    @SerializedName("firebaseToken")
    val firebaseToken: String?,
    @SerializedName("user")
    val user: LoginUser?,
    @SerializedName("message")
    val message: String?
)

data class LoginUser(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("email")
    val email: String?,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("role")
    val role: String,
    @SerializedName("schoolId")
    val schoolId: String,
    @SerializedName("schoolDisplayName")
    val schoolDisplayName: String?,
    @SerializedName("profilePic")
    val profilePic: String?,
    @SerializedName("position")
    val position: String?,
    @SerializedName("department")
    val department: String?,
    @SerializedName("classesAssigned")
    val classesAssigned: List<String>?,
    @SerializedName("subjects")
    val subjects: List<String>?,
    @SerializedName("schoolCode")
    val schoolCode: String?
)

data class RefreshRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class RefreshResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("accessToken")
    val accessToken: String?,
    @SerializedName("refreshToken")
    val refreshToken: String?,
    @SerializedName("firebaseToken")
    val firebaseToken: String?,
    @SerializedName("message")
    val message: String?
)

data class FcmRegisterRequest(
    @SerializedName("fcmToken")
    val fcmToken: String,
    @SerializedName("deviceId")
    val deviceId: String
)
