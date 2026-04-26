package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Mirror of the canonical student document the admin panel writes to the
 * Firestore `students` collection.
 *
 * **Phase 1 canonical class/section shape (2026-04-08):**
 *   - `className`   = "Class 10th"   (display)
 *   - `section`     = "Section A"    (display)
 *   - `classOrder`  = 10             (sortable Int — null for LKG/UKG/Nursery
 *                                     until those get explicit order values)
 *   - `sectionCode` = "A"            (raw token, no prefix)
 *
 * Old docs may still have legacy capitalised duplicates (`Class`, `Section`,
 * `Status`, `Doc`) until the cleanup endpoint sweeps them. The Firestore
 * deserialiser logs `No setter/field for X (case sensitive!)` warnings on
 * those legacy keys, which is **harmless noise** — the canonical camelCase
 * versions still load correctly. ⚠ Do NOT try to silence the warnings by
 * adding `legacyStatus` / `legacySection` / `legacyClass` fields with
 * @PropertyName — Firestore checks property collisions case-INSENSITIVELY
 * and crashes the whole document. Tried 2026-04-08, reverted same day.
 *
 * Adding a new camelCase field is safe: missing fields default to empty/null,
 * so this class always deserialises cleanly even when the admin schema drifts.
 */
data class StudentDoc(
    @DocumentId
    val id: String = "",

    // ── Identity ─────────────────────────────────────────────────────
    val userId: String = "",
    val studentId: String = "",          // alias for userId on some writes
    val schoolId: String = "",
    val schoolCode: String = "",         // legacy alias of schoolId
    val parentDbKey: String = "",
    val session: String = "",

    // ── Personal ─────────────────────────────────────────────────────
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val phoneNumber: String = "",        // legacy alias of phone
    val dob: String = "",
    val gender: String = "",
    val bloodGroup: String = "",
    val religion: String = "",
    val nationality: String = "",
    val category: String = "",

    // ── Class & Section (canonical Phase 1 shape) ────────────────────
    val className: String = "",          // "Class 10th"
    val section: String = "",            // "Section A"
    val classOrder: Int? = null,         // 10 | -2 (LKG) | null (unknown)
    val sectionCode: String = "",        // "A" (raw token)
    val sectionKey: String = "",         // "Class 10th/Section A" (compound)
    val rollNo: String = "",
    val admissionDate: String = "",

    // ── Family ──────────────────────────────────────────────────────
    val fatherName: String = "",
    val motherName: String = "",
    val fatherOccupation: String = "",
    val motherOccupation: String = "",
    val guardContact: String = "",
    val guardRelation: String = "",

    // `address` is sometimes a flat String and sometimes a nested map
    // ({street, city, state, pincode}) depending on which admin controller
    // wrote the doc. Using Any? lets deserialisation accept either shape —
    // declaring it as String causes the whole document to fail with
    // "Could not deserialize object. Failed to convert value of type
    //  java.util.HashMap to String (found in field 'address')".
    val address: Any? = null,

    // ── Previous education ──────────────────────────────────────────
    val preClass: String = "",
    val preSchool: String = "",
    val preMarks: String = "",

    // ── Documents / status / misc ───────────────────────────────────
    val profilePic: String = "",
    val status: String = "",
    val monthFee: Any? = null,           // number or string depending on writer
    val documents: Any? = null,          // legacy nested doc map
    val sessions: Any? = null,           // per-session enrollment map
    val History: Any? = null,            // legacy capitalised nested data — accepted but unused

    // createdAt/updatedAt may arrive as Firestore Timestamp OR ISO String
    // depending on which writer touched the doc. Kept as Any? so deserialization
    // never fails on shape drift; not currently consumed by the Teacher app.
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
