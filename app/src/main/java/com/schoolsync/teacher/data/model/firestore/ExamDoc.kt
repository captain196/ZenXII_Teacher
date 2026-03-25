package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents an exam definition in Firestore.
 *
 * Collection: `exams`
 * Doc ID: auto-generated or admin-set.
 */
data class ExamDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examName: String = "",
    val examType: String = "",           // "Unit Test", "Mid-Term", "Final"
    val gradingScale: String = "",       // "percentage", "a_f", "o_e", "10_point", "pass_fail"
    val passingPercent: Int = 33,
    val maxTheory: Double = 80.0,
    val maxPractical: Double = 20.0,
    val maxTotal: Double = 100.0,
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "",             // "Draft", "Published", "Completed"
    val weight: Double = 0.0,
    val applicableClasses: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
