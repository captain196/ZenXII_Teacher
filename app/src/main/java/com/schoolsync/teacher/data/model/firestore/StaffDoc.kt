package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Firestore staff document. Doc id: {schoolId}_{userId}.
 *
 * Phase A (2026-04-08) added statutory + profile fields.
 * Fields missing from Firestore default to empty/null so this
 * class always deserialises safely even for legacy docs.
 */
data class StaffDoc(
    @DocumentId
    val id: String = "",

    // ── Identity ─────────────────────────────────────────
    val userId: String = "",
    val staffId: String = "",
    val name: String = "",
    val schoolId: String = "",
    val schoolCode: String = "",

    // ── Personal ─────────────────────────────────────────
    val email: String = "",
    val phone: String = "",
    val altPhone: String = "",
    val gender: String = "",
    val dob: String = "",
    val profilePic: String = "",
    val maritalStatus: String = "",

    // Legacy spaced keys — use @get:PropertyName + @set:PropertyName
    @get:PropertyName("Father Name") @set:PropertyName("Father Name")
    var fatherName: String = "",

    // ── Employment ───────────────────────────────────────
    val role: String = "",
    val position: String = "",
    val designation: String = "",
    val department: String = "",
    val qualification: String = "",
    val subjects: List<String> = emptyList(),
    val classesAssigned: List<String> = emptyList(),
    val joiningDate: String = "",

    @get:PropertyName("Date Of Joining") @set:PropertyName("Date Of Joining")
    var dateOfJoining: String = "",

    @get:PropertyName("Employment Type") @set:PropertyName("Employment Type")
    var employmentType: String = "",

    val teaching_subjects: List<String> = emptyList(),
    val staff_roles: List<String> = emptyList(),
    val primary_role: String = "",

    // ── Statutory IDs ────────────────────────────────────
    val panNumber: String = "",
    val aadharNumber: String = "",
    val pfNumber: String = "",
    val esiNumber: String = "",

    // ── Contact / Address ────────────────────────────────
    val emergencyContact: Map<String, Any>? = null,

    @get:PropertyName("Address") @set:PropertyName("Address")
    var addressMap: Map<String, Any>? = null,

    val permanentAddress: Map<String, Any>? = null,

    // ── Qualifications ───────────────────────────────────
    val qualificationDetails: Map<String, Any>? = null,

    // ── Bank / Salary ────────────────────────────────────
    val bankDetails: Map<String, Any>? = null,
    val salaryDetails: Map<String, Any>? = null,

    // ── Status / Meta ────────────────────────────────────
    val status: String = "",
    val session: String = "",
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
