package com.schoolsync.teacher.util

import android.content.Context
import com.schoolsync.teacher.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import androidx.annotation.StringRes

/**
 * Convert a thrown [Throwable] into a calm, user-facing message suitable for a
 * snackbar / banner / dialog — in the staff member's chosen language.
 *
 * Ported from the Parent app so both surfaces speak the same way. Without it,
 * `errorMessage = e.message` leaks raw transport text into the UI:
 *
 *   "Failed to save: retrofit2.HttpException HTTP 502 Bad Gateway"
 *   "java.net.SocketTimeoutException: timeout"
 *
 * — unprofessional, unactionable, and permanently English no matter what
 * language the app is in, because the text comes from the JDK rather than from
 * a string resource.
 *
 * Callers should still log the original [t] at Log.e level; this helper is for
 * the UI surface only, not a substitute for logging.
 *
 * [fallback] is used when the throwable doesn't match a known shape. Pick one
 * specific to the call site (e.g. "Couldn't save attendance" rather than a
 * generic message), pass it already-localized, and never pass null — staff must
 * never see "null" or a stack trace.
 */
/**
 * The resource-id form of [friendlyErrorMessage].
 *
 * Prefer this wherever the result is STORED (ViewModel state, an event object)
 * rather than rendered immediately. A resolved String held in state survives
 * Activity.recreate() and therefore survives a language change — found on
 * device: after switching Tamil -> Hindi the whole login screen re-rendered in
 * Hindi except the error, which stayed Tamil. The user most likely to switch
 * language is precisely the one who could not read that error.
 *
 * Returns null when nothing specific matched, so the caller supplies its own
 * fallback id.
 */
@StringRes
fun friendlyErrorRes(t: Throwable): Int? {
    if (t is com.google.firebase.FirebaseNetworkException) return R.string.err_no_internet
    if (t is com.google.firebase.auth.FirebaseAuthException) {
        return when (t.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_EMAIL", "ERROR_INVALID_LOGIN_CREDENTIALS" -> R.string.err_auth_invalid
            "ERROR_USER_DISABLED"          -> R.string.err_auth_disabled
            "ERROR_TOO_MANY_REQUESTS"      -> R.string.err_auth_too_many
            "ERROR_NETWORK_REQUEST_FAILED" -> R.string.err_no_internet
            else -> null
        }
    }
    return when (t) {
        is UnknownHostException    -> R.string.err_no_internet
        is SocketTimeoutException  -> R.string.err_server_timeout
        is HttpException -> when (t.code()) {
            401, 403 -> R.string.err_session_expired
            404      -> R.string.err_not_found
            408      -> R.string.err_server_timeout
            423      -> R.string.err_action_paused
            in 500..599 -> R.string.err_server_unavailable
            else     -> null
        }
        is IOException -> R.string.err_cannot_reach_server
        else -> null
    }
}

fun friendlyErrorMessage(ctx: Context, t: Throwable, fallback: String): String {
    // Firebase Auth first. Its exception messages are raw provider English
    // ("The supplied auth credential is incorrect, malformed or has expired.")
    // and were being surfaced verbatim on the login screen — the single most
    // frequently seen error in the app, permanently untranslated. Map on the
    // error CODE, never on the message text.
    // FirebaseNetworkException is NOT a FirebaseAuthException and NOT an
    // IOException, so it used to fall all the way through to `fallback` — a
    // login with no connectivity reported "Login failed", which points the user
    // at their password instead of their network. Caught on the emulator with
    // DNS disabled.
    if (t is com.google.firebase.FirebaseNetworkException) {
        return ctx.getString(R.string.err_no_internet)
    }
    if (t is com.google.firebase.auth.FirebaseAuthException) {
        return when (t.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_EMAIL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                ctx.getString(R.string.err_auth_invalid)
            "ERROR_USER_DISABLED"    -> ctx.getString(R.string.err_auth_disabled)
            "ERROR_TOO_MANY_REQUESTS" -> ctx.getString(R.string.err_auth_too_many)
            "ERROR_NETWORK_REQUEST_FAILED" -> ctx.getString(R.string.err_no_internet)
            else -> fallback
        }
    }
    return when (t) {
        is UnknownHostException ->
            ctx.getString(R.string.err_no_internet)

        is SocketTimeoutException ->
            ctx.getString(R.string.err_server_timeout)

        is HttpException -> when (t.code()) {
            401, 403 -> ctx.getString(R.string.err_session_expired)
            404      -> ctx.getString(R.string.err_not_found)
            408      -> ctx.getString(R.string.err_server_timeout)
            // 423 Locked — emitted by MY_Controller::_abort_if_session_frozen
            // (year-end rollover) or ::_abort_if_period_locked (accounting
            // period closed). Both surface as 423 with a structured `code`;
            // the staff member's next action is the same either way.
            423      -> ctx.getString(R.string.err_action_paused)
            in 500..599 -> ctx.getString(R.string.err_server_unavailable)
            else        -> fallback
        }

        // IOException is the parent of UnknownHost/SocketTimeout, so it is
        // matched last and catches everything else (SSL handshake, connection
        // reset, and friends).
        is IOException ->
            ctx.getString(R.string.err_cannot_reach_server)

        else -> fallback
    }
}
