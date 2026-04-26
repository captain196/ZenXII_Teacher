package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Mirror of the canonical section document the admin panel writes to the
 * Firestore `sections` collection.
 *
 * Two field families coexist (see StudentDoc.kt for the rationale):
 *
 *   - Canonical camelCase: `classTeacherId`, `createdAt`, …
 *   - Legacy snake_case / Title Case: `created_at`, `classTeacher` (the
 *     teacher's display name, written by older RTDB writers)
 *
 * Both are accepted so deserialisation never drops data and the
 * `[CustomClassMapper] No setter/field` warnings stay silent.
 */
data class SectionDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val classTeacherId: String = "",
    val classTeacher: String = "",       // legacy: display name string
    val studentCount: Int = 0,
    val subjects: List<SubjectAssignment> = emptyList(),
    val createdAt: Any? = null,
    val created_at: Any? = null,         // legacy snake_case alias
    val updatedAt: Any? = null
)

data class SubjectAssignment(
    val name: String = "",
    val teacherId: String = ""
)
