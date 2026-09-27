package com.schoolsync.teacher.data.lifecycle

import java.io.IOException

/**
 * School access state — the app-side half of the ZenX institution lifecycle.
 *
 * ──────────────────────────────────────────────────────────────────────
 *  WHY THIS EXISTS
 * ──────────────────────────────────────────────────────────────────────
 * Before this, the app had no idea a school could be suspended or closed.
 * Every such condition arrived as a raw Firestore PERMISSION_DENIED, which
 * several repositories then swallowed as "no data" — so a suspended school
 * rendered as an empty screen and a closed one looked like a bug.
 *
 * A permission error is NOT a lifecycle signal. It cannot distinguish
 * "the school closed", "the subscription lapsed", "your session expired"
 * and "you genuinely lack access". This type makes that distinction
 * explicit and is derived from the authoritative signal instead:
 * `tenantPublic/{schoolId}`, the thin client-readable mirror the backend
 * already maintains. No second mirror, no RTDB.
 *
 * ──────────────────────────────────────────────────────────────────────
 *  TWO AXES, MOST-RESTRICTIVE-WINS
 * ──────────────────────────────────────────────────────────────────────
 * COMMERCIAL  (`lifecycleState` + `adminDisabled`) — is the tenant entitled
 *             to use ZenX?
 * INSTITUTION (`institutionState`) — does the school still operate?
 *
 * They are orthogonal and composed most-restrictive-wins, mirroring
 * Lifecycle_resolver.php and firestore.rules exactly. A school can be fully
 * paid up and closed, or unpaid and still teaching, and the user deserves a
 * different sentence in each case.
 */

/** Institution axis, mirroring `schools.institution.state` server-side. */
enum class InstitutionState {
    PROSPECT, ONBOARDING, ACTIVE, CLOSING, CLOSED, ARCHIVED;

    companion object {
        /**
         * ABSENT ⇒ ACTIVE. This is the backward-compatibility rule that makes
         * the whole axis inert for tenants that predate the feature; it must
         * match Lifecycle_resolver::normalize_institution() exactly.
         */
        fun from(raw: String?): InstitutionState =
            when (raw?.trim()?.uppercase()) {
                "PROSPECT"   -> PROSPECT
                "ONBOARDING" -> ONBOARDING
                "CLOSING"    -> CLOSING
                "CLOSED"     -> CLOSED
                "ARCHIVED"   -> ARCHIVED
                else         -> ACTIVE
            }
    }
}

/**
 * What the user may actually do, and what to tell them.
 *
 * `canUseApp` gates navigation into the app. `isReadOnly` lets a screen stay
 * useful (records remain visible) while refusing submissions — the CLOSED
 * case, where retention deliberately outlives operations.
 */
sealed class SchoolAccess(
    val canUseApp: Boolean,
    val isReadOnly: Boolean,
    val title: String,
    val message: String,
) {
    /** Normal operation. */
    object Operational : SchoolAccess(true, false, "", "")

    /** Closure announced; everything still works until the effective date. */
    data class Closing(val effectiveDate: String?) : SchoolAccess(
        canUseApp = true,
        isReadOnly = false,
        title = "School closing",
        message = effectiveDate
            ?.let { "This school is closing on $it. Everything continues to work until then, but no new admissions are being accepted." }
            ?: "This school is preparing to close. Everything continues to work for now, but no new admissions are being accepted.",
    )

    /** Operations ended; records remain readable. */
    object ClosedReadOnly : SchoolAccess(
        canUseApp = true,
        isReadOnly = true,
        title = "School closed",
        message = "This school has closed. You can still view past records and download documents, but new entries can no longer be made.",
    )

    /** Retention only — access is administrator-mediated. */
    object Archived : SchoolAccess(
        canUseApp = false,
        isReadOnly = true,
        title = "Records archived",
        message = "This school's records have been archived. Please contact your school administrator if you need access to them.",
    )

    /** The school has not gone live yet. */
    object NotYetOpen : SchoolAccess(
        canUseApp = false,
        isReadOnly = true,
        title = "School not open yet",
        message = "This school is still being set up and is not open for day-to-day use yet.",
    )

    /**
     * COMMERCIAL restriction — deliberately worded so it can never be
     * mistaken for the school having closed. This is an account matter
     * between the school and ZenX, not a change in the school itself.
     */
    object CommercialHold : SchoolAccess(
        canUseApp = false,
        isReadOnly = true,
        title = "Service unavailable",
        message = "Your school's ZenXii service is currently on hold. Please contact your school administrator — this is an account matter and your records are safe.",
    )

    /**
     * State could not be determined (offline, mirror not yet written).
     * FAIL OPEN: the server and the security rules remain the real gate, so
     * an unreadable mirror must not lock a legitimate user out of an app
     * that would otherwise work.
     */
    object Unknown : SchoolAccess(true, false, "", "")
}

/**
 * Why a data load failed. Keeps four genuinely different conditions apart
 * so the UI never shows one when it means another.
 */
enum class FailureKind {
    /** No or poor connectivity — retryable, not the user's fault. */
    NETWORK,

    /** Session expired or the account was disabled — needs re-login. */
    AUTHENTICATION,

    /** School lifecycle or commercial state — explained by [SchoolAccess]. */
    SCHOOL_STATE,

    /** A genuine permission boundary for this specific user/action. */
    AUTHORIZATION,

    /** Anything else. */
    UNKNOWN,
}

object SchoolLifecycle {

    /**
     * Compose both axes exactly as the server does.
     *
     * The institution axis is reported FIRST when it is restrictive: telling a
     * parent "this school has closed" is truer and more useful than "the
     * subscription lapsed", even when both are true.
     */
    fun resolve(
        institutionState: String?,
        commercialState: String?,
        adminDisabled: Boolean?,
    ): SchoolAccess {
        when (InstitutionState.from(institutionState)) {
            InstitutionState.ARCHIVED   -> return SchoolAccess.Archived
            InstitutionState.CLOSED     -> return SchoolAccess.ClosedReadOnly
            InstitutionState.PROSPECT,
            InstitutionState.ONBOARDING -> return SchoolAccess.NotYetOpen
            InstitutionState.CLOSING,
            InstitutionState.ACTIVE     -> { /* fall through to the commercial axis */ }
        }

        // Commercial axis. Mirrors Lifecycle_resolver::COMMERCIAL_ALLOW.
        val commercial = commercialState?.trim()?.lowercase()
        val commercialAllows = commercial in ALLOWED_COMMERCIAL
        if (adminDisabled == true || (commercial != null && commercial.isNotEmpty() && !commercialAllows)) {
            return SchoolAccess.CommercialHold
        }

        return if (InstitutionState.from(institutionState) == InstitutionState.CLOSING) {
            SchoolAccess.Closing(null)
        } else {
            SchoolAccess.Operational
        }
    }

    /** Commercial states that permit access. Mirrors the server allow-list. */
    private val ALLOWED_COMMERCIAL = setOf("trialing", "active", "expiring_soon", "grace")

    /**
     * Classify a data-load failure.
     *
     * [schoolAccess] is consulted FIRST: when the school itself is restricted,
     * a PERMISSION_DENIED is a CONSEQUENCE of that state, not an independent
     * authorization problem, and must be explained as such. Only when the
     * school is operating does a denial mean "you personally may not do this".
     */
    fun classify(throwable: Throwable?, schoolAccess: SchoolAccess): FailureKind {
        val name = throwable?.let { it::class.java.simpleName } ?: ""
        val text = (throwable?.message ?: "").uppercase()

        // TA-D1: name/text matching alone only catches Firebase's OWN
        // exception vocabulary (FirebaseNetworkException, gRPC status text).
        // A plain socket failure on the app's REST/bearer-auth layer — e.g.
        // java.net.ConnectException("Failed to connect to host:port") — has
        // neither "Network" in its class name nor any of those substrings in
        // its message, so it fell through to UNKNOWN, and callers that skip
        // classify() entirely (reading e.message straight into UI state) leaked
        // the raw host:port text verbatim. Every checked-exception subtype of
        // IOException (ConnectException, SocketTimeoutException,
        // UnknownHostException, ...) is unambiguously a connectivity failure
        // regardless of wording, so it is classified on TYPE first.
        if (throwable is IOException ||
            name.contains("Network", ignoreCase = true) ||
            text.contains("UNAVAILABLE") ||
            text.contains("OFFLINE") ||
            text.contains("DEADLINE_EXCEEDED") ||
            text.contains("TIMEOUT") ||
            text.contains("FAILED TO CONNECT") ||
            text.contains("UNABLE TO RESOLVE HOST")
        ) return FailureKind.NETWORK

        if (name.contains("FirebaseAuth", ignoreCase = true) ||
            text.contains("UNAUTHENTICATED") ||
            text.contains("TOKEN")
        ) return FailureKind.AUTHENTICATION

        if (text.contains("PERMISSION_DENIED")) {
            return if (schoolAccess.canUseApp && !schoolAccess.isReadOnly) {
                FailureKind.AUTHORIZATION
            } else {
                FailureKind.SCHOOL_STATE
            }
        }

        return FailureKind.UNKNOWN
    }

    /** A user-facing sentence for a failure. Never exposes internals. */
    fun messageFor(kind: FailureKind, schoolAccess: SchoolAccess): String = when (kind) {
        FailureKind.NETWORK ->
            "You appear to be offline. Please check your connection and try again."
        FailureKind.AUTHENTICATION ->
            "Your session has expired. Please sign in again."
        FailureKind.SCHOOL_STATE ->
            schoolAccess.message.ifEmpty { "This school is not currently available." }
        FailureKind.AUTHORIZATION ->
            "You don't have access to this section. Please contact your school administrator."
        FailureKind.UNKNOWN ->
            "Something went wrong. Please try again."
    }
}
