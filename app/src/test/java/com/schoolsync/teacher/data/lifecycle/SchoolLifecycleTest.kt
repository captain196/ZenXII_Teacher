package com.schoolsync.teacher.data.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime verification of the app-side lifecycle decision logic.
 *
 * This is the app half of the parity contract with Lifecycle_resolver.php and
 * firestore.rules. It EXECUTES the composition (a compile pass proves none of
 * this), over the full 7 commercial x 6 institution matrix, so a drift between
 * what the app tells the user and what the server actually permits shows up
 * here rather than in front of a parent.
 */
class SchoolLifecycleTest {

    private val allowedCommercial = listOf("trialing", "active", "expiring_soon", "grace")
    private val deniedCommercial = listOf("past_due", "suspended", "expired", "provisioning_incomplete")
    private val allInstitution = listOf("PROSPECT", "ONBOARDING", "ACTIVE", "CLOSING", "CLOSED", "ARCHIVED")

    // ── Backward compatibility ────────────────────────────────────────

    @Test
    fun `absent institution state is treated as ACTIVE`() {
        for (raw in listOf(null, "", "   ", "nonsense", "SUSPENDED", "DRAFT")) {
            assertEquals(
                "an unknown institution state must fall back to ACTIVE",
                InstitutionState.ACTIVE,
                InstitutionState.from(raw)
            )
        }
    }

    @Test
    fun `a tenant with no lifecycle fields at all is fully operational`() {
        // Every tenant that predates the feature looks exactly like this.
        assertEquals(SchoolAccess.Operational, SchoolLifecycle.resolve(null, null, null))
        assertTrue(SchoolLifecycle.resolve(null, null, null).canUseApp)
        assertFalse(SchoolLifecycle.resolve(null, null, null).isReadOnly)
    }

    @Test
    fun `institution state parsing is case and whitespace tolerant`() {
        assertEquals(InstitutionState.CLOSED, InstitutionState.from(" closed "))
        assertEquals(InstitutionState.ARCHIVED, InstitutionState.from("Archived"))
    }

    // ── The full composition matrix ───────────────────────────────────

    @Test
    fun `operational only when both axes permit`() {
        for (commercial in allowedCommercial) {
            assertEquals(
                "commercial=$commercial + institution=ACTIVE must be fully operational",
                SchoolAccess.Operational,
                SchoolLifecycle.resolve("ACTIVE", commercial, false)
            )
        }
    }

    @Test
    fun `every denying commercial state produces a commercial hold, not a closure message`() {
        for (commercial in deniedCommercial) {
            val access = SchoolLifecycle.resolve("ACTIVE", commercial, false)
            assertEquals(
                "commercial=$commercial must read as a service hold",
                SchoolAccess.CommercialHold,
                access
            )
            assertNotEquals(
                "a billing hold must never be worded as a school closure",
                SchoolAccess.ClosedReadOnly.message,
                access.message
            )
        }
    }

    @Test
    fun `institution closure outranks a billing problem in the explanation`() {
        // Both true: the school closed AND the invoice lapsed. The parent-facing
        // truth is that the school closed.
        assertEquals(SchoolAccess.ClosedReadOnly, SchoolLifecycle.resolve("CLOSED", "suspended", false))
        assertEquals(SchoolAccess.Archived, SchoolLifecycle.resolve("ARCHIVED", "expired", false))
    }

    @Test
    fun `admin disabled toggle alone produces a commercial hold`() {
        assertEquals(
            SchoolAccess.CommercialHold,
            SchoolLifecycle.resolve("ACTIVE", "active", true)
        )
    }

    @Test
    fun `the whole matrix resolves without exception and never contradicts itself`() {
        for (institution in allInstitution) {
            for (commercial in allowedCommercial + deniedCommercial) {
                for (disabled in listOf(true, false)) {
                    val a = SchoolLifecycle.resolve(institution, commercial, disabled)
                    // An unusable school can never be reported as writable.
                    if (!a.canUseApp) {
                        assertTrue(
                            "$institution/$commercial/$disabled: unusable state must be read-only",
                            a.isReadOnly
                        )
                    }
                    // Any restricted state must carry an explanation.
                    if (a != SchoolAccess.Operational && a !is SchoolAccess.Closing && a != SchoolAccess.Unknown) {
                        assertTrue(
                            "$institution/$commercial/$disabled: restricted state needs a message",
                            a.message.isNotBlank() && a.title.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    // ── CLOSED is read-only, not off ──────────────────────────────────

    @Test
    fun `closed school stays reachable but read-only`() {
        val a = SchoolLifecycle.resolve("CLOSED", "active", false)
        assertTrue("records must outlive operations", a.canUseApp)
        assertTrue(a.isReadOnly)
    }

    @Test
    fun `archived school is not reachable from the app`() {
        val a = SchoolLifecycle.resolve("ARCHIVED", "active", false)
        assertFalse(a.canUseApp)
    }

    @Test
    fun `closing school keeps working and can name its effective date`() {
        val a = SchoolLifecycle.resolve("CLOSING", "active", false)
        assertTrue(a is SchoolAccess.Closing)
        assertTrue("a closing school still operates", a.canUseApp)
        assertFalse("a closing school is not read-only", a.isReadOnly)

        val dated = SchoolAccess.Closing("2027-03-31")
        assertTrue("the notice must name the date", dated.message.contains("2027-03-31"))
    }

    @Test
    fun `onboarding and prospect are not open to app users`() {
        for (state in listOf("ONBOARDING", "PROSPECT")) {
            val a = SchoolLifecycle.resolve(state, "active", false)
            assertEquals(SchoolAccess.NotYetOpen, a)
            assertFalse(a.canUseApp)
        }
    }

    // ── Failure classification: the four conditions stay distinct ─────

    @Test
    fun `network failures are never reported as a school or permission problem`() {
        val operational = SchoolAccess.Operational
        for (t in listOf(
            RuntimeException("UNAVAILABLE: network down"),
            RuntimeException("DEADLINE_EXCEEDED"),
            RuntimeException("client is OFFLINE"),
        )) {
            assertEquals(FailureKind.NETWORK, SchoolLifecycle.classify(t, operational))
        }
    }

    @Test
    fun `authentication failures are distinguished from authorization`() {
        assertEquals(
            FailureKind.AUTHENTICATION,
            SchoolLifecycle.classify(RuntimeException("UNAUTHENTICATED: bad TOKEN"), SchoolAccess.Operational)
        )
    }

    @Test
    fun `permission denied on an operating school is a real authorization problem`() {
        assertEquals(
            FailureKind.AUTHORIZATION,
            SchoolLifecycle.classify(
                RuntimeException("PERMISSION_DENIED: insufficient permissions"),
                SchoolAccess.Operational
            )
        )
    }

    @Test
    fun `permission denied on a restricted school is explained as school state`() {
        // The central UX fix: on a closed or suspended school a denial is a
        // CONSEQUENCE of the school's state, not a personal permission issue.
        for (access in listOf(SchoolAccess.ClosedReadOnly, SchoolAccess.CommercialHold, SchoolAccess.Archived)) {
            assertEquals(
                "denial under $access must be explained as school state",
                FailureKind.SCHOOL_STATE,
                SchoolLifecycle.classify(RuntimeException("PERMISSION_DENIED"), access)
            )
        }
    }

    @Test
    fun `no failure message ever leaks a raw firestore error`() {
        for (kind in FailureKind.values()) {
            for (access in listOf(
                SchoolAccess.Operational, SchoolAccess.ClosedReadOnly,
                SchoolAccess.CommercialHold, SchoolAccess.Archived, SchoolAccess.NotYetOpen,
            )) {
                val msg = SchoolLifecycle.messageFor(kind, access)
                assertTrue("$kind/$access produced an empty message", msg.isNotBlank())
                for (leak in listOf("PERMISSION_DENIED", "UNAVAILABLE", "UNAUTHENTICATED", "Firestore", "Exception")) {
                    assertFalse("$kind/$access leaked '$leak' to the user", msg.contains(leak))
                }
            }
        }
    }

    @Test
    fun `unknown state fails open so an unreadable mirror cannot lock users out`() {
        assertTrue(SchoolAccess.Unknown.canUseApp)
        assertFalse(SchoolAccess.Unknown.isReadOnly)
    }

    // ── TA-D1: plain socket failures, not just Firebase's own exceptions ──
    //
    // classify()'s NETWORK check previously matched only Firebase's own
    // vocabulary (FirebaseNetworkException, gRPC status text). A raw
    // java.net.ConnectException from the app's REST/bearer-auth layer has
    // neither "Network" in its class name nor any of those substrings in its
    // message — real, observed text was literally
    // "Failed to connect to localhost/127.0.0.1:8080" — so it fell through to
    // UNKNOWN, and a caller that skipped classify() (reading e.message
    // straight into UI state, as RegularizationViewModel did) leaked the raw
    // host:port text verbatim to a teacher.

    @Test
    fun `a plain socket connect failure classifies as network, not unknown`() {
        val e = java.net.ConnectException("Failed to connect to localhost/127.0.0.1:8080")
        assertEquals(FailureKind.NETWORK, SchoolLifecycle.classify(e, SchoolAccess.Operational))
    }

    @Test
    fun `every IOException subtype classifies as network regardless of wording`() {
        val exceptions = listOf(
            java.net.ConnectException("Failed to connect to localhost/127.0.0.1:8080"),
            java.net.SocketTimeoutException("timeout"),
            java.net.UnknownHostException("api.example.invalid"),
            java.io.IOException("stream closed"),
        )
        for (e in exceptions) {
            assertEquals(
                "${e::class.simpleName} must classify as NETWORK",
                FailureKind.NETWORK,
                SchoolLifecycle.classify(e, SchoolAccess.Operational),
            )
        }
    }

    @Test
    fun `a socket exception message never reaches the user via messageFor`() {
        val e = java.net.ConnectException("Failed to connect to localhost/127.0.0.1:8080")
        val kind = SchoolLifecycle.classify(e, SchoolAccess.Operational)
        val msg = SchoolLifecycle.messageFor(kind, SchoolAccess.Operational)
        assertFalse("the rendered message must not contain the raw host", msg.contains("127.0.0.1"))
        assertFalse("the rendered message must not contain the raw port", msg.contains("8080"))
        assertFalse("the rendered message must not name a Java exception", msg.contains("Exception"))
    }

    @Test
    fun `classify never returns a redundant tautological branch for truly unknown exceptions`() {
        // Regression guard for the dead `if (throwable == null) UNKNOWN else UNKNOWN`
        // this fix also removed: both a null throwable and a genuinely
        // unclassifiable one must still resolve to UNKNOWN, not throw.
        assertEquals(FailureKind.UNKNOWN, SchoolLifecycle.classify(null, SchoolAccess.Operational))
        assertEquals(
            FailureKind.UNKNOWN,
            SchoolLifecycle.classify(RuntimeException("some other application error"), SchoolAccess.Operational),
        )
    }
}
