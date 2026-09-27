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
    // Day counts are FRACTIONAL by design and must stay Double.
    //
    // The admin panel produces half-days in three independent ways:
    //   • attendance mark 'M' (half day worked)      -> daysAbsent 0.5
    //   • half-day leave (paid or LWP)               -> lwpDays 0.5
    //   • Calendar Half-Day (school-declared)        -> workingDays 21.5
    //
    // These were previously Int. Firestore's CustomClassMapper converts a
    // stored double into an Int field via Number.intValue() — silently, with
    // no exception — so 21.5 arrived as 21 and 0.5 arrived as 0. Worse, the
    // payslip screen gates the LWP row on `> 0`, so a genuine 0.5-day loss of
    // pay vanished from the teacher's own slip while their net pay was still
    // reduced. Nothing surfaced the discrepancy.
    //
    // Double matches the canonical server contract (Hr::_buildPayslipDoc now
    // writes these as floats) and the pattern already used for leave days in
    // LeaveApplicationDoc.numberOfDays. Render via formatDays() so a whole
    // number still displays as "22", not "22.0".
    val workingDays: Double = 0.0,
    val presentDays: Double = 0.0,
    val daysAbsent: Double = 0.0,
    val lwpDays: Double = 0.0,
    val paidLeaveDays: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val overtimePay: Double = 0.0,
    val arrears: Double = 0.0,
    val status: String = "Draft",          // Draft, Finalized, Paid (admin casing)
    val bankRefNo: String = "",
    val disbursedAt: Any? = null,
    val generatedAt: Any? = null
)
