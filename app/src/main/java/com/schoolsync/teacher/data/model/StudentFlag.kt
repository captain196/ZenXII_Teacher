package com.schoolsync.teacher.data.model

data class StudentFlag(
    val flagId: String = "",
    val type: String = "",           // homework, behavior, performance
    val message: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val severity: String = "low",    // low, medium, high
    val timestamp: Long = 0L,
    val status: String = "active",   // active, resolved
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val hwId: String? = null,
    val studentName: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type,
        "message" to message,
        "subject" to subject,
        "teacherId" to teacherId,
        "teacherName" to teacherName,
        "severity" to severity,
        "timestamp" to timestamp,
        "status" to status,
        "hwId" to hwId,
        "studentName" to studentName
    )

    companion object {
        fun fromMap(flagId: String, data: Map<String, Any?>): StudentFlag = StudentFlag(
            flagId = flagId,
            type = data["type"]?.toString() ?: "",
            message = data["message"]?.toString() ?: "",
            subject = data["subject"]?.toString() ?: "",
            teacherId = data["teacherId"]?.toString() ?: "",
            teacherName = data["teacherName"]?.toString() ?: "",
            severity = data["severity"]?.toString() ?: "low",
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
            status = data["status"]?.toString() ?: "active",
            resolvedAt = (data["resolvedAt"] as? Number)?.toLong(),
            resolvedBy = data["resolvedBy"]?.toString(),
            hwId = data["hwId"]?.toString(),
            studentName = data["studentName"]?.toString() ?: ""
        )
    }
}
