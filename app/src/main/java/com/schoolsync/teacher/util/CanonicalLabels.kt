package com.schoolsync.teacher.util

import android.content.Context
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

/**
 * Translates canonical English **data** values into display text, at render
 * time only.
 *
 * ## The problem this solves
 *
 * A handful of English strings in ZenXii are simultaneously wire values and UI
 * labels. `"Monday"` is the `day` field on a timetable document *and* part of
 * its document id (`{schoolId}_{session}_{sectionKey}_{day}`). `"January"` is a
 * key. `"Present"` is a stored attendance status. They look like copy, so they
 * are the single easiest thing to translate by accident — and doing so writes
 * `"सोमवार"` into a Firestore key, which corrupts data silently and may not
 * surface for weeks.
 *
 * ## The invariant
 *
 * > The canonical string is never transformed in the data layer.
 *
 * Every function here takes the canonical value and returns display text. Call
 * them from composables and other render sites. **Never** call them in a
 * repository, and never store what they return.
 *
 * ## Passthrough is mandatory
 *
 * Unknown keys return the input unchanged rather than blank or a placeholder.
 * Schools define their own fee heads, and RBAC modules get added server-side
 * without an app release. Those must render their English name, which is
 * readable, rather than an empty cell, which looks broken.
 */
object CanonicalLabels {

    private val DAYS: Map<String, Int> = mapOf(
        "monday" to R.string.day_monday,
        "tuesday" to R.string.day_tuesday,
        "wednesday" to R.string.day_wednesday,
        "thursday" to R.string.day_thursday,
        "friday" to R.string.day_friday,
        "saturday" to R.string.day_saturday,
        "sunday" to R.string.day_sunday
    )

    private val MONTHS: Map<String, Int> = mapOf(
        "january" to R.string.month_january,
        "february" to R.string.month_february,
        "march" to R.string.month_march,
        "april" to R.string.month_april,
        "may" to R.string.month_may,
        "june" to R.string.month_june,
        "july" to R.string.month_july,
        "august" to R.string.month_august,
        "september" to R.string.month_september,
        "october" to R.string.month_october,
        "november" to R.string.month_november,
        "december" to R.string.month_december
    )

    private val ATTENDANCE: Map<String, Int> = mapOf(
        "present" to R.string.attendance_status_present,
        "absent" to R.string.attendance_status_absent,
        "leave" to R.string.attendance_status_leave,
        "holiday" to R.string.attendance_status_holiday,
        "late" to R.string.attendance_status_late,
        "vacation" to R.string.attendance_status_vacation
    )

    // Lesson-plan status. These four ARE the wire values written to Firestore
    // (`planned` / `completed` / `skipped` / `rescheduled`) and the picker used
    // to render them raw. The value written stays the key; only what is shown
    // resolves here.
    private val LESSON_STATUS: Map<String, Int> = mapOf(
        "planned" to R.string.lesson_status_planned,
        "completed" to R.string.lesson_status_completed,
        "skipped" to R.string.lesson_status_skipped,
        "rescheduled" to R.string.lesson_status_rescheduled
    )

    // Red-flag severity and type. Like the lesson statuses above, these ARE the
    // values written to Firestore; only the rendering resolves here.
    private val FLAG_SEVERITY: Map<String, Int> = mapOf(
        "low" to R.string.flag_severity_low,
        "medium" to R.string.flag_severity_medium,
        "high" to R.string.flag_severity_high
    )

    private val FLAG_TYPE: Map<String, Int> = mapOf(
        "homework" to R.string.flag_type_homework,
        "behavior" to R.string.flag_type_behavior,
        "performance" to R.string.flag_type_performance
    )

    // Three-letter weekday headers. The timetable grid rendered day.take(3) on
    // the canonical English name — which slices a grapheme cluster in half on
    // any Indic script — so short forms are explicit resources.
    private val DAYS_SHORT: Map<String, Int> = mapOf(
        "monday" to R.string.day_short_monday,
        "tuesday" to R.string.day_short_tuesday,
        "wednesday" to R.string.day_short_wednesday,
        "thursday" to R.string.day_short_thursday,
        "friday" to R.string.day_short_friday,
        "saturday" to R.string.day_short_saturday,
        "sunday" to R.string.day_short_sunday
    )

    // Three-letter month labels. Callers used month.take(3) on the server's
    // English month name, which renders Latin regardless of app language.
    private val MONTHS_SHORT: Map<String, Int> = mapOf(
        "january" to R.string.month_short_january,
        "february" to R.string.month_short_february,
        "march" to R.string.month_short_march,
        "april" to R.string.month_short_april,
        "may" to R.string.month_short_may,
        "june" to R.string.month_short_june,
        "july" to R.string.month_short_july,
        "august" to R.string.month_short_august,
        "september" to R.string.month_short_september,
        "october" to R.string.month_short_october,
        "november" to R.string.month_short_november,
        "december" to R.string.month_short_december
    )

    /**
     * Look up [key] in [map], falling back to the key itself.
     *
     * Lower-cased with [java.util.Locale.ROOT], not the display locale — the
     * Turkish dotless-i problem means `"I".lowercase()` under a Turkish locale
     * produces "ı", which would miss every lookup. Lookup keys are machine
     * values, so they get machine casing.
     */
    private fun lookup(ctx: Context, map: Map<String, Int>, key: String?): String {
        if (key.isNullOrBlank()) return ""
        val res = map[key.trim().lowercase(java.util.Locale.ROOT)]
        return if (res != null) ctx.localizedString(res) else key
    }

    /**
     * Notice categories. The stored value is the English string — it is the
     * filter comparison at NoticesViewModel:151 and the value written at :144 —
     * so this maps it to a label for DISPLAY ONLY. Never write the result back.
     */
    private val NOTICE_CATEGORIES = mapOf(
        "all" to R.string.notice_cat_all,
        "general" to R.string.notice_cat_general,
        "academic" to R.string.notice_cat_academic,
        "event" to R.string.notice_cat_event,
        "holiday" to R.string.notice_cat_holiday,
        "exam" to R.string.notice_cat_exam,
        "fee" to R.string.notice_cat_fee,
        "policy" to R.string.notice_cat_policy,
        "administrative" to R.string.notice_cat_administrative,
        "emergency" to R.string.notice_cat_emergency,
        "recruitment" to R.string.notice_cat_recruitment,
    )

    /** `"Recruitment"` → the category name in the app's language. */
    fun noticeCategory(ctx: Context, canonical: String?): String =
        lookup(ctx, NOTICE_CATEGORIES, canonical)

    /** `"Monday"` → the weekday name in the app's language. */
    fun day(ctx: Context, canonical: String?): String = lookup(ctx, DAYS, canonical)

    /** `"January"` → the month name in the app's language. */
    fun month(ctx: Context, canonical: String?): String = lookup(ctx, MONTHS, canonical)

    /** `"Present"` → the status name in the app's language. */
    fun attendanceStatus(ctx: Context, canonical: String?): String =
        lookup(ctx, ATTENDANCE, canonical)

    /**
     * A `Calendar.MONTH` int (0-based) → month name in the app's language.
     * The canonical English list below is the SOURCE, mirroring
     * `englishDayName()` in DateUtils — it stays English because month names
     * are used as Firestore path segments and query keys.
     */
    /** Lesson-plan status, from its canonical wire value. */
    fun lessonStatus(ctx: Context, canonical: String?): String =
        lookup(ctx, LESSON_STATUS, canonical)

    /** Red-flag severity, from its canonical wire value. */
    fun flagSeverity(ctx: Context, canonical: String?): String =
        lookup(ctx, FLAG_SEVERITY, canonical)

    /** Red-flag type, from its canonical wire value. */
    fun flagType(ctx: Context, canonical: String?): String =
        lookup(ctx, FLAG_TYPE, canonical)

    /** Three-letter weekday, from the canonical English day name. */
    fun dayShort(ctx: Context, canonical: String?): String =
        lookup(ctx, DAYS_SHORT, canonical)

    /** Three-letter month, from the canonical English month name. */
    fun monthShort(ctx: Context, canonical: String?): String =
        lookup(ctx, MONTHS_SHORT, canonical)

    fun monthByIndex(ctx: Context, calendarMonth: Int): String {
        val canonical = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        ).getOrNull(calendarMonth) ?: return ""
        return month(ctx, canonical)
    }
}
