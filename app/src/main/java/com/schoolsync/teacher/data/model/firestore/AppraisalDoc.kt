package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Mirrors the admin Hr.php save_appraisal canonical camelCase shape.
 * Status: "Draft" | "Submitted" | "Reviewed" (admin casing).
 * Teachers only see Submitted/Reviewed appraisals; Drafts are filtered out.
 */
data class AppraisalDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val department: String = "",
    val appraisalType: String = "",        // Annual / Probation / Promotion / Mid-Year / Special
    val period: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val teachingQuality: Double = 0.0,
    val punctuality: Double = 0.0,
    val studentFeedback: Double = 0.0,
    val initiative: Double = 0.0,
    val teamwork: Double = 0.0,
    val overallRating: Double = 0.0,
    val strengths: String = "",
    val areasOfImprovement: String = "",
    val recommendation: String = "none",   // none / promotion / increment / training / pip
    val comments: String = "",
    val goals: String = "",
    val status: String = "Draft",
    val updatedAt: Any? = null
)
