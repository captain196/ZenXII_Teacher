package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class SalarySlipDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val month: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val empId: String = "",
    val department: String = "",
    val earnings: Map<String, Double> = emptyMap(),
    val deductions: Map<String, Double> = emptyMap(),
    val grossEarnings: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val netPayable: Double = 0.0,
    val workingDays: Int = 0,
    val presentDays: Int = 0,
    val lwpDays: Int = 0,
    val overtimeHours: Double = 0.0,
    val overtimePay: Double = 0.0,
    val arrears: Double = 0.0,
    val status: String = "draft",          // draft, generated, disbursed
    val bankRefNo: String = "",
    @ServerTimestamp
    val disbursedAt: Timestamp? = null,
    @ServerTimestamp
    val generatedAt: Timestamp? = null
)
