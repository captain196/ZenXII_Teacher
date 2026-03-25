package com.schoolsync.teacher.data.model.rtdb

data class NotifBadgeRtdb(
    val total: Int = 0,
    val attendance: Int = 0,
    val homework: Int = 0,
    val fees: Int = 0,
    val circular: Int = 0,
    val chat: Int = 0,
    val exam: Int = 0,
    val transport: Int = 0,
    val general: Int = 0
)
