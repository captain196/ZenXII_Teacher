package com.schoolsync.teacher.data.model

/**
 * Canonical Firestore shape for `studentFlags/{schoolId}_{flagId}`.
 *
 * All denorm fields (studentName/rollNo/fatherName/className/section) MUST be
 * populated at write time — Admin RBAC, dashboard analytics, and class
 * filters depend on them. Empty values cause flags to vanish into an
 * "Unknown" bucket and become invisible to teachers on the admin panel.
 */
data class StudentFlag(
    val flagId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val fatherName: String = "",
    val className: String = "",
    val section: String = "",
    val type: String = "",            // homework, behavior, performance
    val message: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val severity: String = "low",     // low, medium, high
    val createdAtMs: Long = 0L,
    /** Origin of the flag — drives soft-delete RBAC. */
    val createdByRole: String = "teacher", // 'teacher' | 'admin'
    val status: String = "active",    // active, resolved, deleted
    val resolvedAtMs: Long? = null,
    val resolvedBy: String? = null,
    val deletedAtMs: Long? = null,
    val deletedBy: String? = null,
    val hwId: String? = null
) {
    companion object {
        fun fromMap(flagId: String, data: Map<String, Any?>): StudentFlag = StudentFlag(
            flagId      = flagId,
            studentId   = data["studentId"]?.toString().orEmpty(),
            studentName = data["studentName"]?.toString().orEmpty(),
            rollNo      = data["rollNo"]?.toString().orEmpty(),
            fatherName  = data["fatherName"]?.toString().orEmpty(),
            className   = data["className"]?.toString().orEmpty(),
            section     = data["section"]?.toString().orEmpty(),
            type        = data["type"]?.toString()?.lowercase().orEmpty(),
            message     = data["message"]?.toString().orEmpty(),
            subject     = data["subject"]?.toString().orEmpty(),
            teacherId   = data["teacherId"]?.toString().orEmpty(),
            teacherName = data["teacherName"]?.toString().orEmpty(),
            severity    = (data["severity"]?.toString()?.lowercase()?.takeIf { it.isNotBlank() }) ?: "low",
            createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
            createdByRole = (data["createdByRole"]?.toString()?.lowercase()?.takeIf { it.isNotBlank() }) ?: "teacher",
            status      = (data["status"]?.toString()?.lowercase()?.takeIf { it.isNotBlank() }) ?: "active",
            resolvedAtMs = (data["resolvedAtMs"] as? Number)?.toLong(),
            resolvedBy  = data["resolvedBy"]?.toString(),
            deletedAtMs = (data["deletedAtMs"] as? Number)?.toLong(),
            deletedBy   = data["deletedBy"]?.toString(),
            hwId        = data["hwId"]?.toString()
        )
    }
}
