package com.schoolsync.teacher.data.model

/**
 * Marks entry for a student in a specific exam/subject.
 * Fields: Theory, Practical, Total, Absent, SavedAt
 */
data class MarksData(
    val Theory: Double = 0.0,
    val Practical: Double = 0.0,
    val Total: Double = 0.0,
    val Absent: Boolean = false,
    val SavedAt: Any? = null
) {
    /** No-arg constructor retained for callers that build an empty instance. */
    constructor() : this(Theory = 0.0)

    companion object {
        /**
         * Create a MarksData marked as absent.
         */
        fun absent(): MarksData {
            return MarksData(
                Theory = 0.0,
                Practical = 0.0,
                Total = 0.0,
                Absent = true
            )
        }

        /**
         * Create and validate marks against max thresholds.
         * @throws IllegalArgumentException if theory or practical exceeds max
         */
        fun create(
            theory: Double,
            practical: Double,
            maxTheory: Double,
            maxPractical: Double,
            absent: Boolean = false
        ): MarksData {
            if (absent) return absent()

            require(theory >= 0) { "Theory marks cannot be negative" }
            require(practical >= 0) { "Practical marks cannot be negative" }
            require(theory <= maxTheory) {
                "Theory marks ($theory) exceed maximum ($maxTheory)"
            }
            require(practical <= maxPractical) {
                "Practical marks ($practical) exceed maximum ($maxPractical)"
            }

            return MarksData(
                Theory = theory,
                Practical = practical,
                Total = theory + practical,
                Absent = false
            )
        }
    }
}

/**
 * Exam subject template that defines max marks for each component.
 * Read from the exam definition to validate marks entries.
 */
data class MarksTemplate(
    val subject: String = "",
    val maxTheory: Double = 0.0,
    val maxPractical: Double = 0.0,
    val maxTotal: Double = 0.0
) {
    constructor() : this(subject = "")
}

/**
 * Student marks row for display in the marks entry UI.
 */
data class StudentMarksEntry(
    val studentId: String,
    val studentName: String,
    val rollNo: String,
    val existingMarks: MarksData?,
    val isModified: Boolean = false
)
