package com.schoolsync.teacher.data.model

/**
 * Exam information.
 * Path: Schools/{schoolCode}/{session}/{class}/{section}/Exams/{examName}
 */
data class ExamInfo(
    val examId: String = "",
    val examName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val className: String = "",
    val section: String = "",
    val subjects: Map<String, ExamSubjectDetail> = emptyMap(),
    val status: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(examId = "")
}

/**
 * Subject-level details within an exam definition.
 * Contains max marks for theory/practical used for validation.
 */
data class ExamSubjectDetail(
    val subjectName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val maxTheory: Double = 0.0,
    val maxPractical: Double = 0.0,
    val maxTotal: Double = 0.0,
    val room: String = ""
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(subjectName = "")

    fun toMarksTemplate(): MarksTemplate {
        return MarksTemplate(
            subject = subjectName,
            maxTheory = maxTheory,
            maxPractical = maxPractical,
            maxTotal = maxTotal
        )
    }
}
