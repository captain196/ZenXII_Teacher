package com.schoolsync.teacher.data.model

import com.google.firebase.database.ServerValue

/**
 * Marks entry for a student in a specific exam/subject.
 * Path: Schools/{schoolCode}/{session}/Results/Marks/{examId}/{class}/{section}/{subject}/{studentId}
 * Fields: Theory, Practical, Total, Absent, SavedAt
 */
data class MarksData(
    val Theory: Double = 0.0,
    val Practical: Double = 0.0,
    val Total: Double = 0.0,
    val Absent: Boolean = false,
    val SavedAt: Any? = null
) {
    /** No-arg constructor for Firebase deserialization */
    constructor() : this(Theory = 0.0)

    /**
     * Convert to a Firebase-writable map with server timestamp.
     */
    fun toFirebaseMap(): Map<String, Any> {
        return mapOf(
            "Theory" to Theory,
            "Practical" to Practical,
            "Total" to Total,
            "Absent" to Absent,
            "SavedAt" to ServerValue.TIMESTAMP
        )
    }

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
