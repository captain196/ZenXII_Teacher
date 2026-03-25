package com.schoolsync.teacher.data.model

data class StudentFeeStatus(
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val totalPending: Double = 0.0,
    val isDefaulter: Boolean = false,
    val examBlocked: Boolean = false,
    val resultWithheld: Boolean = false,
    val lastPaymentDate: String = "",
    val fatherName: String = "",
    val phone: String = ""
)

data class ClassFeeOverview(
    val className: String = "",
    val section: String = "",
    val totalStudents: Int = 0,
    val paidCount: Int = 0,
    val defaulterCount: Int = 0,
    val totalCollected: Double = 0.0,
    val totalPending: Double = 0.0,
    val examBlockedCount: Int = 0,
    val resultWithheldCount: Int = 0
) {
    val clearCount: Int get() = totalStudents - defaulterCount
    val collectionPercent: Int get() = if (totalStudents > 0) ((paidCount.toDouble() / totalStudents) * 100).toInt() else 0
    val examReadyCount: Int get() = totalStudents - examBlockedCount
}

data class FeeDefaulter(
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val totalDues: Double = 0.0,
    val unpaidMonths: List<String> = emptyList(),
    val examBlocked: Boolean = false,
    val resultWithheld: Boolean = false,
    val fatherName: String = "",
    val phone: String = ""
)
