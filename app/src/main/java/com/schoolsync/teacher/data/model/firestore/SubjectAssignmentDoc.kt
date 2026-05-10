package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Firestore subjectAssignments collection — single source of truth for teacher-subject-class assignments.
 *
 * Doc ID format: {schoolId}_{session}_{className}_{section}_{subjectCode}
 * (section may be "_ALL_" for class-wide assignments)
 *
 * Collection is owned by the admin panel via Subject_assignment_service.
 *
 * NOTE on `isClassTeacher`: Kotlin's `is`-prefix Boolean property convention
 * makes Firestore's CustomClassMapper miss the field — its reflective lookup
 * expects `getIsClassTeacher` / `setIsClassTeacher`, but Kotlin generates
 * `isClassTeacher()` and no setter (val). Without [PropertyName] every load
 * logs `No setter/field for isClassTeacher` and the value silently stays
 * `false`. The annotation forces an exact-name binding on the getter.
 */
data class SubjectAssignmentDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val subjectCode: String = "",
    val subjectName: String = "",
    val category: String = "",
    val periodsPerWeek: Int = 0,
    val teacherId: String = "",
    val teacherName: String = "",
    @get:PropertyName("isClassTeacher")
    val isClassTeacher: Boolean = false,
    val updatedAt: String = "",
    val archived: Boolean = false,
)
