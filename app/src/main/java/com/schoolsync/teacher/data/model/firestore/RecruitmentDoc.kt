package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Mirrors admin Hr.php save_job canonical camelCase shape on the `hrJobs`
 * collection. Admin's UI keeps status as "Open"/"Closed"/"On_Hold"; we read
 * the lowercase `statusLower` mirror for queries.
 */
data class RecruitmentDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val department: String = "",
    val departmentId: String = "",
    val qualification: String = "",
    val experience: String = "",
    val salaryRange: String = "",          // formatted "₹X – ₹Y"
    val salaryRangeMin: Double = 0.0,
    val salaryRangeMax: Double = 0.0,
    val vacancies: Int = 0,
    val jobDescription: String = "",
    val status: String = "Open",            // admin casing for display
    val statusLower: String = "open",       // canonical lowercase for queries
    val postedDate: String = "",            // YYYY-MM-DD
    val closingDate: String = "",
    val postedAt: Any? = null,              // ISO string for sort
    val createdAt: Any? = null
)
