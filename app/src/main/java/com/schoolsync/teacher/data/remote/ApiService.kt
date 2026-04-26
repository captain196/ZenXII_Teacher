package com.schoolsync.teacher.data.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Retrofit API service for admin panel endpoints.
 *
 * Phase 8b (2026-04-09): added `teacherNotify` so the teacher app
 * can trigger parent push notifications after marking A/T via the
 * admin's `_fire_single_student_event` pipeline.
 */
interface ApiService {

    /**
     * Notify the admin panel that a student was marked Absent or
     * Tardy so it fires the FCM push to the parent's device.
     *
     * The teacher app writes attendance directly to Firestore
     * (canonical), but the push pipeline lives in PHP — this
     * endpoint bridges the gap.
     */
    @FormUrlEncoded
    @POST("attendance/teacher_notify")
    suspend fun teacherNotify(
        @Field("student_id") studentId: String,
        @Field("mark") mark: String,
        @Field("class") className: String,
        @Field("section") section: String,
        @Field("day") day: Int,
        @Field("month") month: String = ""
    ): Response<Map<String, Any>>
}
