package com.schoolsync.teacher.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language authority.
 *
 * Device-local storage is what decides what gets rendered; Firestore
 * (`students.prefLang`, `userDevices.lang`) is a mirror written for the server's
 * benefit — push fan-out needs to know the language, and a reinstall needs
 * something to restore from. The app never blocks on a network read to decide
 * what to draw: a cold start with no network in Tamil must be Tamil.
 *
 * Language deliberately does **not** live in Firebase custom claims. A claim
 * change only reaches a client after an ID-token refresh, i.e. a re-login, and a
 * language toggle that makes you sign out is not a toggle.
 *
 * ## Why SharedPreferences and not DataStore
 *
 * [wrap] is called from `attachBaseContext`, which runs on the main thread during
 * Activity/Application creation and cannot suspend. `runBlocking { dataStore.data.first() }`
 * there is an ANR on a low-end device on every cold start. This one key lives in
 * its own SharedPreferences file; everything else in the app stays on DataStore.
 *
 * ## Why ContextWrapper and not AppCompatDelegate.setApplicationLocales
 *
 * Below API 33 — which is where the regional-language user base actually is —
 * that API is a backport requiring `AppCompatActivity` and a `Theme.AppCompat`
 * parent. The staff app's MainActivity is likewise a plain `ComponentActivity` with its own
 * `PaymentResultWithDataListener` under `android:Theme.Material.Light.NoActionBar`,
 * with its own dark-mode system. Migrating would put a visual-regression surface
 * across 237 files on the table to save about forty lines here.
 */
object LocaleManager {

    private const val PREFS = "zenxii_locale"
    private const val KEY_LANG = "app_lang"

    /**
     * Supported languages, each labelled by its **endonym** — the name of the
     * language in that language. A parent who reads only Tamil cannot find the
     * word "Tamil" in a list; they can find "தமிழ்".
     */
    val SUPPORTED: List<Pair<String, String>> = listOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "mr" to "मराठी",
        "gu" to "ગુજરાતી",
        "ta" to "தமிழ்",
        "te" to "తెలుగు"
    )

    private val SUPPORTED_TAGS: Set<String> = SUPPORTED.map { it.first }.toSet()

    fun isSupported(tag: String?): Boolean = tag != null && tag in SUPPORTED_TAGS

    /** Endonym for a tag, or the tag itself if we don't ship it. */
    fun labelFor(tag: String): String =
        SUPPORTED.firstOrNull { it.first == tag }?.second ?: tag

    // ── Stored preference ───────────────────────────────────────────────────

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The user's explicit in-app choice, or "" when they have never chosen.
     *
     * The empty string is meaningful and must be preserved: it is what makes
     * [wrap] a passthrough, which in turn is what lets the `en-XA` pseudolocale
     * work for layout testing. Forcing a concrete default here would silently
     * disable that.
     */
    fun storedTag(ctx: Context): String = prefs(ctx).getString(KEY_LANG, "") ?: ""

    fun hasExplicitChoice(ctx: Context): Boolean = storedTag(ctx).isNotEmpty()

    /**
     * Persist an explicit choice. Uses `commit()`, not `apply()`: the caller
     * immediately calls `Activity.recreate()`, and an async write can lose the
     * race against the new Activity's `attachBaseContext` reading it back.
     */
    fun setLanguage(ctx: Context, tag: String) {
        if (!isSupported(tag)) return
        prefs(ctx).edit().putString(KEY_LANG, tag).commit()
    }

    /**
     * Adopt a language the server already knows about, but only if the user has
     * not chosen one on this device. This is the reinstall / account-switch
     * restore path: the local preference is gone, `students.prefLang` is not.
     * An explicit local choice always wins over the server.
     */
    fun adoptFromServerIfUnset(ctx: Context, serverTag: String?) {
        if (hasExplicitChoice(ctx)) return
        if (!isSupported(serverTag)) return
        prefs(ctx).edit().putString(KEY_LANG, serverTag).commit()
    }

    // ── Resolution ──────────────────────────────────────────────────────────

    /**
     * What the app should actually render in, following the agreed order:
     * explicit in-app choice → device OS locale if we ship it → English.
     *
     * There is no school-level default in v1: the admin panel has no UI to set
     * one, so `schools.defaultLang` stays reserved-but-unused rather than
     * pretending to be configurable.
     */
    fun effectiveTag(ctx: Context): String {
        val stored = storedTag(ctx)
        if (stored.isNotEmpty()) return stored
        return deviceTag() ?: "en"
    }

    /** The device's OS language, if it is one of the six we ship. */
    fun deviceTag(): String? {
        val locales = LocaleList.getDefault()
        for (i in 0 until locales.size()) {
            val tag = locales.get(i).language   // "hi", not "hi-IN"
            if (tag in SUPPORTED_TAGS) return tag
        }
        return null
    }

    // ── The wrapper ─────────────────────────────────────────────────────────

    /**
     * Wrap a base [Context] in the user's chosen locale. Call from
     * `attachBaseContext` in every Activity, the Application, and any Service
     * that builds user-visible text (notifications).
     *
     * Returns [base] untouched when no explicit choice exists, which lets the
     * platform's own locale — including the `en-XA` pseudolocale — flow through
     * unmodified.
     */
    fun wrap(base: Context): Context {
        val tag = storedTag(base)
        if (tag.isEmpty()) return base

        val locale = Locale.forLanguageTag(tag)
        // Also covers formatters that take no Context (SimpleDateFormat and
        // friends constructed with Locale.getDefault()). Display formatting is
        // routed through DisplayFormat, but third-party libraries are not.
        Locale.setDefault(locale)

        val cfg = Configuration(base.resources.configuration)
        cfg.setLocales(LocaleList(locale))   // API 24; minSdk is 24, so no legacy branch
        return base.createConfigurationContext(cfg)
    }
}
