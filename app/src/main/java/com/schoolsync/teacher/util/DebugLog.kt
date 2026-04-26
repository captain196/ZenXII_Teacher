package com.schoolsync.teacher.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OEM workaround — iQOO / OnePlus / Xiaomi strip third-party Debug
 * logs from `adb logcat` on OxygenOS/ColorOS/MIUI. Mirrors the parent
 * app's DebugLog util: writes to `cache/debug.log` which adb can
 * pull via `run-as` on debuggable builds.
 *
 * Call [initDebugLog] once from Application.onCreate.
 * Then call [debugLog] from anywhere.
 */
private var appContext: Context? = null
private const val MAX_BYTES = 50_000
private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

fun initDebugLog(ctx: Context) {
    appContext = ctx.applicationContext
}

fun debugLog(message: String) {
    Log.d("SchoolSyncDebug", message)
    val ctx = appContext ?: return
    try {
        val file = File(ctx.cacheDir, "debug.log")
        if (file.exists() && file.length() > MAX_BYTES) {
            file.writeText(file.readText().takeLast(MAX_BYTES / 2))
        }
        file.appendText("${timeFmt.format(Date())}  $message\n")
    } catch (_: Exception) { /* silent */ }
}
