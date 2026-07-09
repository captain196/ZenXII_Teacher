package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a single student's marks for one subject in one exam.
 *
 * Collection: `marks`
 * Doc ID: `{schoolId}_{examId}_{idToken(className)}_{idToken(section)}_{idToken(subject)}_{studentId}`
 *   where the class/section/subject tokens are the collision-safe SHA-1 tokens
 *   produced by [com.schoolsync.teacher.util.Constants.idToken] (byte-identical
 *   to the admin panel's `Exam_result_store::marksDocId`).
 *
 * `componentMarks` is the CANONICAL per-component list the admin report cards /
 * tabulation read from ( `[{name, value}]` ); `theory`/`practical`/`total` are
 * kept as derived convenience fields. Both must be written so admin-side readers
 * see the component columns.
 */
data class MarksDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examId: String = "",
    val sectionKey: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val componentMarks: List<Map<String, Any>> = emptyList(),  // CANONICAL [{name, value}]
    val theory: Double = 0.0,
    val practical: Double = 0.0,
    val total: Double = 0.0,
    val maxMarks: Double = 0.0,           // = subject.maxTotal (from the exam schedule)
    val absent: Boolean = false,
    val savedBy: String = "",
    val savedAt: Any? = null,
    val status: String = "draft"         // "draft", "submitted", "verified", "locked"
)
