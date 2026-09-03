package com.schoolsync.teacher.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User-facing formatting. Month and weekday names follow the app's language;
 * **digits and currency never do.**
 *
 * The counterpart to [DateParse]. Ask of any formatter: *does its output get
 * compared, stored, sent, or used as a key?* Yes → [DateParse]. No → here.
 *
 * ## Why digits stay Latin
 *
 * ICU's default numbering system for several Indic locales is not Latin, so
 * `SimpleDateFormat("dd MMM yyyy", Locale("hi"))` will happily render
 * `१५ मार्च २०२६`. That is linguistically correct and operationally wrong: fee
 * amounts, receipt numbers, roll numbers and marks are cross-checked by parents
 * against paper receipts, UPI apps and WhatsApp messages from the school office.
 * Devanagari digits on a fee figure is a support call, not a feature. Every
 * formatter below therefore has its number format pinned to `Locale.ROOT`.
 *
 * Note that Kotlin's `String.format` extension without an explicit `Locale` uses
 * `Locale.getDefault(FORMAT)` and **will** emit native digits the moment the app
 * language changes. Route numeric display through this object instead.
 *
 * ## Why formatters are built per call
 *
 * Changing language calls `Activity.recreate()`, but the process — and any
 * `object` singleton in it — survives. A formatter cached at class-init would
 * hold the language the app started in and silently keep rendering in it.
 * `SimpleDateFormat` is also mutable and not thread-safe, so a shared instance
 * corrupts output under concurrency.
 */
object DisplayFormat {

    // ── Patterns ────────────────────────────────────────────────────────────

    const val DATE = "dd MMM yyyy"              // 15 Mar 2026
    const val DATE_TIME = "dd MMM yyyy, hh:mm a" // 15 Mar 2026, 09:30 AM
    const val TIME = "h:mm a"                    // 9:30 AM
    const val DAY_MONTH = "dd MMM"               // 15 Mar
    const val WEEKDAY_DAY_MONTH = "EEE, d MMM"   // Sun, 15 Mar
    const val FULL_DATE = "EEEE, d MMMM yyyy"    // Sunday, 15 March 2026

    /**
     * The locale to render in. Reads the platform default, which
     * `LocaleManager.wrap()` has already set for the whole process, so this
     * stays correct after a language switch without needing a Context.
     */
    private fun displayLocale(): Locale = Locale.getDefault()

    /**
     * A date formatter in the app's language, with **Latin digits pinned**.
     *
     * Overriding `numberFormat` is what forces Latin digits: date fields are
     * rendered through the DateFormat's NumberFormat, so replacing it with a
     * ROOT one defeats the locale's native numbering system. Grouping is off so
     * years render `2026`, never `2,026`.
     */
    private fun formatter(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, displayLocale()).apply {
            numberFormat = NumberFormat.getIntegerInstance(Locale.ROOT).apply {
                isGroupingUsed = false
            }
        }

    // ── Dates ───────────────────────────────────────────────────────────────

    fun date(d: Date?): String = d?.let { formatter(DATE).format(it) } ?: ""

    fun dateTime(d: Date?): String = d?.let { formatter(DATE_TIME).format(it) } ?: ""

    fun time(d: Date?): String = d?.let { formatter(TIME).format(it) } ?: ""

    fun dayMonth(d: Date?): String = d?.let { formatter(DAY_MONTH).format(it) } ?: ""

    fun weekdayDayMonth(d: Date?): String =
        d?.let { formatter(WEEKDAY_DAY_MONTH).format(it) } ?: ""

    fun fullDate(d: Date?): String = d?.let { formatter(FULL_DATE).format(it) } ?: ""

    /** Format with an arbitrary pattern, still in-language and Latin-digit. */
    fun pattern(d: Date?, pattern: String): String =
        d?.let { formatter(pattern).format(it) } ?: ""

    /**
     * Render a server-written date string for display, in one step.
     * Returns the original string when it can't be parsed — showing the raw
     * value beats showing an empty cell, and it makes bad server data visible.
     */
    fun serverDate(dateStr: String?, pattern: String = DATE): String {
        val parsed = DateParse.parse(dateStr) ?: return dateStr.orEmpty()
        return pattern(parsed, pattern)
    }

    // ── Numbers and money ───────────────────────────────────────────────────

    /**
     * Indian Rupees, always `en-IN`: `₹1,23,456.50`.
     *
     * The school's fee does not change denomination or grouping because an
     * admin reads Tamil. Only the label around the number is translated.
     */
    fun currency(amount: Number?): String {
        if (amount == null) return ""
        val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return nf.format(amount)
    }

    /** Rupees with no decimals — for whole-rupee amounts like fee heads. */
    fun currencyWhole(amount: Number?): String {
        if (amount == null) return ""
        val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
        return nf.format(amount)
    }

    /** Grouped integer in Latin digits, Indian grouping: `1,23,456`. */
    fun number(n: Number?): String {
        if (n == null) return ""
        return NumberFormat.getIntegerInstance(Locale("en", "IN")).format(n)
    }

    /** One-decimal percentage, Latin digits: `87.5%`. */
    fun percent(value: Float?, decimals: Int = 1): String {
        if (value == null) return ""
        val nf = NumberFormat.getNumberInstance(Locale.ROOT).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
        return "${nf.format(value)}%"
    }
}
