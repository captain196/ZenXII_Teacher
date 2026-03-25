package com.schoolsync.teacher.data.model

data class HomeworkStatusEntry(
    val studentId: String = "",
    val studentName: String = "",
    val status: String = "pending", // pending, submitted, incomplete, complete
    val remark: String = "",
    val submittedAt: Long = 0L,
    val reviewedBy: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(studentId = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "studentName" to studentName,
        "status" to status,
        "remark" to remark,
        "submittedAt" to submittedAt,
        "reviewedBy" to reviewedBy
    )

    companion object {
        fun fromMap(studentId: String, data: Map<String, Any?>): HomeworkStatusEntry =
            HomeworkStatusEntry(
                studentId = studentId,
                studentName = data["studentName"]?.toString() ?: "",
                status = data["status"]?.toString() ?: "pending",
                remark = data["remark"]?.toString() ?: "",
                submittedAt = (data["submittedAt"] as? Number)?.toLong() ?: 0L,
                reviewedBy = data["reviewedBy"]?.toString() ?: ""
            )
    }
}
