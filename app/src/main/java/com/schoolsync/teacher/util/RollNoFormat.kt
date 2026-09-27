package com.schoolsync.teacher.util

/**
 * Canonical Roll Number **display** formatting for the Teacher app.
 *
 * Phase 11 (P11-D1). Roll Number is class+section-scoped free text on the
 * canonical Firestore student document (`students.rollNo: String`) — the admin
 * panel has never constrained it to digits, and Phase 9.2 deliberately made
 * bulk import accept alphanumeric values such as `A12`, `7B`, `18A`.
 *
 * Several view models keep an `Int` roll number for sorting/keying and derive
 * it with `rollNo.toIntOrNull() ?: Int.MIN_VALUE`. That sentinel is a **sort
 * key, never a display value** — rendering it produced the live `-2147483648`
 * defect on the roster card, the student detail panel, the attendance matrix
 * and the marks grid. Every one of those models already carries the untouched
 * Firestore string as `rollNoRaw`; this helper is the single place that turns
 * it into text.
 *
 * Rules:
 *   - `"18"`  -> `"18"`   (or `"18"` padded, when [padNumericTo] is set)
 *   - `"A12"` -> `"A12"`  (never coerced to a number)
 *   - `""`    -> [ROLL_NO_UNSET]
 *
 * Do **not** reintroduce `Int` roll numbers into UI text, and do not invent a
 * fallback value (0, a row index, a sequential number) — a student without a
 * roll number must read as "not set", not as somebody else's roll number.
 */
const val ROLL_NO_UNSET: String = "—"

/**
 * @param raw          the untouched Firestore `rollNo` string (`rollNoRaw`).
 * @param padNumericTo left-pad with `'0'` to this width, but **only** when the
 *                     value is entirely numeric. Preserves the existing
 *                     two-digit look of the attendance list without mangling
 *                     alphanumeric roll numbers. `0` (default) disables it.
 */
fun formatRollNo(raw: String, padNumericTo: Int = 0): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ROLL_NO_UNSET
    return if (padNumericTo > 0 && trimmed.all { it.isDigit() }) {
        trimmed.padStart(padNumericTo, '0')
    } else {
        trimmed
    }
}
