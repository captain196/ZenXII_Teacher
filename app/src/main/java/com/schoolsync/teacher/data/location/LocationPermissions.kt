package com.schoolsync.teacher.data.location

import android.Manifest

/**
 * LocationPermissions — pure permission-state logic (NO UI).
 *
 * The actual runtime request + dialogs live in the UI layer (Phase 13,
 * Compose `rememberLauncherForActivityResult`). This object only holds the
 * permission set and interprets a result into a clear state — kept here so
 * the decision logic is testable and free of Android UI plumbing.
 */
object LocationPermissions {

    /**
     * We request FINE (for an accurate fix) and COARSE (Android 12 "approximate
     * location" path — the user may grant only this; the server then rejects on
     * poor accuracy, and the UI nudges the user to enable Precise location).
     */
    val REQUIRED: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    enum class Status {
        GRANTED,             // at least one location permission granted
        DENIED,              // denied, but the system will still show a rationale → can re-ask
        PERMANENTLY_DENIED,  // "Don't ask again" — must deep-link to app Settings
    }

    /**
     * Interpret a permission outcome. The UI supplies:
     *   - [granted]: did the user grant FINE or COARSE?
     *   - [showRationale]: shouldShowRequestPermissionRationale AFTER the denial.
     *
     * When denied AND no rationale should be shown, Android is signalling the
     * permanently-denied / "Don't ask again" state → the UI must route the user
     * to system Settings instead of re-prompting.
     */
    fun interpret(granted: Boolean, showRationale: Boolean): Status = when {
        granted -> Status.GRANTED
        showRationale -> Status.DENIED
        else -> Status.PERMANENTLY_DENIED
    }
}
