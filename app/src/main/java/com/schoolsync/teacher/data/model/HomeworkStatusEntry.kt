package com.schoolsync.teacher.data.model

data class HomeworkStatusEntry(
    val studentId: String = "",
    val studentName: String = "",
    val status: String = "pending", // pending, submitted, reviewed, complete, incomplete
    val remark: String = "",
    val submittedAt: Long = 0L,
    val reviewedBy: String = "",
    // Phase HW: fields needed for teacher to see student's work
    val text: String = "",              // student's written response
    val files: List<String> = emptyList(), // student's uploaded files
    val score: Int = -1,                // teacher's score (-1 = not graded)
    val maxMarks: Int = 0
) {
    constructor() : this(studentId = "")
}
