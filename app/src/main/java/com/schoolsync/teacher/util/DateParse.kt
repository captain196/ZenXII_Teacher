package com.schoolsync.teacher.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Machine-facing date handling. **Everything here is `Locale.ROOT` and stays
 * English, permanently.**
 *
 * The rule that governs this file, and the whole multi-language project:
 *
 * > Parsing, keys and wire values use `Locale.ROOT`. Only rendering localizes.
 *
 * Anything that gets compared, stored, sent to Firestore, or used as part of a
 * document id belongs here. Anything a person reads belongs in [DisplayFormat].
 * A formatter that does both must be split into two calls.
 *
 * This is not a style preference. `SimpleDateFormat("dd MMM yyyy", Locale.getDefault())`
 * cannot parse the server's `"15 Mar 2026"` once the device is in Hindi — it
 * expects `"15 मार्च 2026"` — so the parse returns null and the feature silently
 * empties out. That was a real, live bug in `LibraryViewModel` before this file
 * existed.
 *
 * Formatters are constructed per call. `SimpleDateFormat` is mutable and not
 * thread-safe, so sharing instances across coroutines corrupts output under
 * concurrency in ways that are extremely hard to reproduce.
 */
object DateParse {

    /** The wire format for dates across ZenXii: Firestore fields and doc-id parts. */
    const val ISO_DATE = "yyyy-MM-dd"

    /**
     * Patterns accepted when reading a date the server (or an older client)
     * wrote. Ordered most-likely-first. All strict: a lenient parser happily
     * reads "2026-13-45" as a date in 2027, which is worse than failing.
     */
    private val ACCEPTED = listOf(
        ISO_DATE,
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd MMM yyyy"
    )

    private fun machineFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }

    /**
     * Parse a server-written date string, trying each accepted pattern.
     * Returns null when nothing matches — callers must handle that rather than
     * substituting "today", which silently invents data.
     */
    fun parse(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        for (pattern in ACCEPTED) {
            try {
                return machineFormat(pattern).parse(trimmed) ?: continue
            } catch (_: ParseException) {
                // try the next pattern
            } catch (_: Exception) {
                // defensive: never let a malformed server value crash a screen
            }
        }
        return null
    }

    /** Format a date for storage or comparison — never for display. */
    fun toIso(date: Date): String = machineFormat(ISO_DATE).format(date)

    /** Today, in the wire format. Use this for `whereEqualTo("date", …)`. */
    fun todayIso(): String = toIso(Date())

    /**
     * Parse with one explicit pattern, for callers that know exactly what the
     * server wrote and want a mismatch to fail rather than be coerced by one of
     * the fallbacks above.
     */
    fun parseExact(dateStr: String?, pattern: String): Date? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            machineFormat(pattern).parse(dateStr.trim())
        } catch (_: Exception) {
            null
        }
    }
}
