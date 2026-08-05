package com.schoolsync.teacher.data.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Server-side mobile auth endpoints. All requests authenticate with a
 * Firebase ID token in `Authorization: Bearer <token>`.
 *
 * Paths begin with a leading slash so they resolve against the host root
 * of BASE_URL (e.g. http://10.119.9.181/auth/...) rather than against
 * the /Grader/school/ subpath used for the legacy PHP REST endpoints.
 */
interface AuthApi {

    /**
     * Finalise an admin-driven password reset. The caller's Firebase Auth
     * password is updated server-side and the `must_change_password` custom
     * claim is cleared. The corresponding Firestore profile doc's
     * `mustChangePassword` field is also set to false.
     *
     * Requires the user to have `must_change_password: true` on their
     * Firebase Auth claims — calls without the flag return 400.
     */
    @FormUrlEncoded
    @POST("/auth/clear_must_change")
    suspend fun clearMustChange(
        @Header("Authorization") bearer: String,
        @Field("new_password") newPassword: String,
    ): Response<ClearMustChangeResponse>
}

data class ClearMustChangeResponse(
    val status: String? = null,
    val message: String? = null,
    val uid: String? = null,
)
