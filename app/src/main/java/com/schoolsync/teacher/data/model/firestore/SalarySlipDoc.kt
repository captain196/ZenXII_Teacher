package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SalarySlipDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val month: String = "",                // display label e.g. "April"
    val monthKey: String = "",             // sortable ISO "2026-04"
    val year: String = "",
    val runId: String = "",
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
    val daysAbsent: Int = 0,
    val lwpDays: Int = 0,
    val paidLeaveDays: Int = 0,
    val overtimeHours: Double = 0.0,
    val overtimePay: Double = 0.0,
    val arrears: Double = 0.0,
    val status: String = "Draft",          // Draft, Finalized, Paid (admin casing)
    val bankRefNo: String = "",
    val disbursedAt: Any? = null,
    val generatedAt: Any? = null
)
