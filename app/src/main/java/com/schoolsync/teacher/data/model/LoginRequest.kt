package com.schoolsync.teacher.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("deviceId")
    val deviceId: String
)
