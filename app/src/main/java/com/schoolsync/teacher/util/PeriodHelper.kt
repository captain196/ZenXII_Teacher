package com.schoolsync.teacher.util

/**
 * Canonical period -> month label conversion. Mirrors
 * Fee_firestore_txn::periodToMonth on the PHP side and the parent app's
 * util.PeriodHelper.
 *
 * Strip ONLY the trailing year token ("April 2026" -> "April",
 * "Yearly Fees 2026-27" -> "Yearly Fees"); keep multi-word labels intact.
 */
private val YEAR_SUFFIX = Regex("""\s+\d{4}(-\d{2,4})?$""")

fun periodToMonth(period: String?): String =
    YEAR_SUFFIX.replace((period ?: "").trim(), "").trim()
