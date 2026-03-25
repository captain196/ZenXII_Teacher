package com.schoolsync.teacher.data.model.rtdb

data class ChatMetaRtdb(
    val type: String = "direct",          // direct, group
    val participants: Map<String, ParticipantInfo> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastTimestamp: Long = 0L,
    val schoolId: String = "",
    val contextStudentId: String = "",
    val contextStudentName: String = ""
)

data class ParticipantInfo(
    val name: String = "",
    val role: String = "",
    val avatar: String = ""
)
