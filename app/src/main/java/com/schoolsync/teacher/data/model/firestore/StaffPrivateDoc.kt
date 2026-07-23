package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Owner-readable sensitive PII split out of [StaffDoc] by admin-panel audit
 * fix C1. Collection: staffPrivate. Doc id: {schoolId}_{staffId} (same id as
 * the staff doc). Firestore rules permit a staff member to read only their OWN
 * staffPrivate doc (docId == {school_id}_{uid}, uid == staffId).
 *
 * After the data migration these six fields become null on the staff doc and
 * live here instead. Fields default to empty/null so this class always
 * deserialises safely, including before the migration when the doc is absent.
 */
data class StaffPrivateDoc(
    @DocumentId
    val id: String = "",

    val panNumber: String = "",
    val aadharNumber: String = "",
    val pfNumber: String = "",
    val esiNumber: String = "",

    val bankDetails: Map<String, Any>? = null,
    val salaryDetails: Map<String, Any>? = null
)
