package com.schoolsync.teacher.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.schoolsync.teacher.util.DisplayFormat

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

@SuppressLint("HardwareIds")
fun Context.getDeviceId(): String =
    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

fun String.safeToInt(default: Int = 0): Int = toIntOrNull() ?: default
fun String.safeToDouble(default: Double = 0.0): Double = toDoubleOrNull() ?: default

fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}d ago"
        else -> DisplayFormat.date(Date(this))
    }
}

fun Long.toFormattedDate(): String =
    DisplayFormat.date(Date(this))

fun Long.toFormattedDateTime(): String =
    DisplayFormat.dateTime(Date(this))

fun Long.toTimeOnly(): String =
    DisplayFormat.time(Date(this))

/** Display-only. Localized month name, Latin digits pinned. */
fun getCurrentMonth(): String =
    com.schoolsync.teacher.util.DisplayFormat.pattern(Date(), "MMMM yyyy")

fun getCurrentSession(): String {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    return if (month >= Calendar.APRIL) "$year-${(year + 1) % 100}" else "${year - 1}-${year % 100}"
}

fun getDaysInMonth(month: Int, year: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month)
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

fun getDayOfWeek(day: Int, month: Int, year: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(year, month, day)
    return cal.get(Calendar.DAY_OF_WEEK)
}

// Pinned to en-IN: an amount must not regroup because the UI language
// changed. Prefer DisplayFormat.currencyWhole() in new code.
fun Int.toRupees(): String = DisplayFormat.currencyWhole(this)

fun Double.toPercent(): String = DisplayFormat.percent(this.toFloat())

fun Map<String, Any?>.getString(key: String, default: String = ""): String =
    (this[key] as? String) ?: default

fun Map<String, Any?>.getInt(key: String, default: Int = 0): Int =
    when (val v = this[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }

fun Map<String, Any?>.getLong(key: String, default: Long = 0L): Long =
    when (val v = this[key]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: default
        else -> default
    }

fun Map<String, Any?>.getBoolean(key: String, default: Boolean = false): Boolean =
    (this[key] as? Boolean) ?: default
