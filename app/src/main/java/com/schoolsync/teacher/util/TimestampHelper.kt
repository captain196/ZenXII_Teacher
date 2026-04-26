package com.schoolsync.teacher.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Convert a Firestore timestamp-ish field to a Date or null.
 *
 * The PHP backend writes ISO 8601 strings (e.g. "2026-04-08T03:56:34.108Z" or
 * "2026-04-08T03:56:34+00:00"), the Firestore Android SDK writes [Timestamp]
 * objects, and some legacy paths write epoch millis as Number. This helper
 * normalises all three so model fields can be typed as `Any?` and consumers
 * don't have to care which writer touched the document last.
 *
 * Returns null on blank strings, unknown types, or unparseable formats.
 */
fun Any?.toDateOrNull(): Date? = when (this) {
    null -> null
    is Timestamp -> toDate()
    is Date -> this
    is Number -> Date(toLong())
    is String -> if (isBlank()) null else parseIsoString(this)
    else -> null
}

/** Same as [toDateOrNull] but returns epoch millis. */
fun Any?.toEpochMillisOrNull(): Long? = toDateOrNull()?.time

/**
 * Parse the formats the PHP backend actually writes — both with milliseconds
 * (`.SSS`) and without, and both ISO `XXX` (e.g. `+00:00`) and `Z` zones.
 * Tries each format in turn; returns null if none match.
 */
private fun parseIsoString(value: String): Date? {
    val formats = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in formats) {
        try {
            return SimpleDateFormat(pattern, Locale.US).parse(value)
        } catch (_: Exception) { /* try next */ }
    }
    return null
}
