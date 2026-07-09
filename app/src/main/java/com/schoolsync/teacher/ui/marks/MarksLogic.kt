package com.schoolsync.teacher.ui.marks

/**
 * Pure, Android/Firebase-free marks helpers, extracted out of MarksViewModel /
 * MarksScreen so they can be unit-tested hermetically (RESIDUAL-6). Nothing here
 * touches the framework, so a plain JVM test exercises them directly.
 */

/** Render a Double mark without a trailing ".0" (7.0 → "7", 7.5 → "7.5"). */
internal fun formatMark(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/**
 * Input filter for a marks text field: accept empty, or up to 5 chars of digits
 * with at most one decimal point. Accepts "", "40", "37.5"; rejects "4.5.6"
 * (two dots), "12x" (non-numeric), and anything longer than 5 chars.
 */
internal fun isValidMarkInput(input: String): Boolean =
    input.isEmpty() || (input.length <= 5 && input.matches(Regex("^\\d*\\.?\\d*$")))

/**
 * Build the canonical `componentMarks` list the admin report cards read component
 * columns from ([{name, value}]). Theory only when the subject has no practical
 * (maxPractical == 0 → hasPractical == false); Theory + Practical otherwise.
 */
internal fun buildComponentMarks(
    theory: Double,
    practical: Double,
    hasPractical: Boolean
): List<Map<String, Any>> {
    val components = mutableListOf<Map<String, Any>>(mapOf("name" to "Theory", "value" to theory))
    if (hasPractical) {
        components.add(mapOf("name" to "Practical", "value" to practical))
    }
    return components
}

/**
 * MED-6: the combined total exceeds the subject's maxTotal. Only meaningful when
 * the student is present — an absent student has no numeric total to validate.
 */
internal fun exceedsMaxTotal(total: Double?, maxTotal: Int, isAbsent: Boolean): Boolean =
    !isAbsent && total != null && total > maxTotal
