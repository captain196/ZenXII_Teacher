package com.schoolsync.teacher.data.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TA-D2 — NETWORK FAILURE MUST NEVER BECOME TENANT SUSPENSION.
 *
 * A teacher once saw the full-screen notice
 *
 *     "Service unavailable — your school's ZenXii service is currently on hold."
 *
 * for a tenant whose authoritative record said `lifecycleState: "active"`.
 * Telling a paying school its account is suspended when it is not is the most
 * damaging thing this state machine can do, so the separation between the
 * three states is asserted here rather than left to inspection:
 *
 *   ACTIVE     authoritative data positively confirms the tenant is entitled
 *   HOLD       authoritative data positively confirms it is not
 *   UNKNOWN    the app could not determine it — fail-open, never a lock-out
 *
 * Two distinct defects fed this, and both are guarded below:
 *
 *   1. `FirestoreService.observeDocument()` used `cancel(error)`, which raises
 *      a CancellationException that `.catch {}` deliberately never intercepts,
 *      so the repository's fail-open `emit(Unknown)` could not run at all.
 *
 *   2. `mapSnapshot()` trusted a snapshot without asking whether it came from
 *      Firestore's OFFLINE CACHE. `.get()` silently falls back to that cache
 *      when the server is unreachable, so a stale denying value could lock a
 *      user out on data nothing had re-confirmed — a network failure wearing a
 *      suspension's clothes.
 */
class NetworkFailureIsNotSuspensionTest {

    // ── 1. The resolver: UNKNOWN is not, and cannot become, a hold ────

    @Test
    fun `unknown is fail-open and is not a commercial hold`() {
        assertTrue("Unknown must let the app run", SchoolAccess.Unknown.canUseApp)
        assertFalse(SchoolAccess.Unknown.isReadOnly)
        assertNotEquals(SchoolAccess.CommercialHold, SchoolAccess.Unknown)
        assertTrue(
            "Unknown must carry no user-facing accusation",
            SchoolAccess.Unknown.message.isBlank() && SchoolAccess.Unknown.title.isBlank(),
        )
    }

    /**
     * The absent-data case. A tenant whose mirror has not been written, or
     * whose fields could not be read, must resolve to normal operation — not
     * to a hold. This is the backward-compatibility rule every pre-feature
     * tenant depends on.
     */
    @Test
    fun `absent or unreadable lifecycle fields never produce a hold`() {
        for (institution in listOf(null, "", "   ")) {
            for (commercial in listOf(null, "", "   ")) {
                val a = SchoolLifecycle.resolve(institution, commercial, null)
                assertNotEquals(
                    "institution=$institution commercial=$commercial must not be a hold",
                    SchoolAccess.CommercialHold,
                    a,
                )
                assertTrue("must remain usable", a.canUseApp)
            }
        }
    }

    /** Only a POSITIVE denying signal may hold the tenant. */
    @Test
    fun `a hold requires authoritative denying data`() {
        // Positive denial → hold. This must keep working.
        assertEquals(
            SchoolAccess.CommercialHold,
            SchoolLifecycle.resolve("ACTIVE", "suspended", false),
        )
        assertEquals(
            SchoolAccess.CommercialHold,
            SchoolLifecycle.resolve("ACTIVE", "active", true),   // adminDisabled
        )

        // The real tenant's real values must resolve to Operational.
        assertEquals(
            "lifecycleState=active with no other signal is a working school",
            SchoolAccess.Operational,
            SchoolLifecycle.resolve(null, "active", null),
        )
    }

    // ── 2. Failure classification never lands on school state ────────

    @Test
    fun `every transport failure classifies as NETWORK, not SCHOOL_STATE`() {
        val transportFailures = listOf(
            java.net.ConnectException("Failed to connect to localhost/127.0.0.1:8080"),
            java.net.SocketTimeoutException("timeout"),
            java.net.UnknownHostException("firestore.googleapis.com"),
            java.io.IOException("stream closed"),
            RuntimeException("UNAVAILABLE: network down"),
            RuntimeException("DEADLINE_EXCEEDED"),
            RuntimeException("client is OFFLINE"),
        )

        for (e in transportFailures) {
            val kind = SchoolLifecycle.classify(e, SchoolAccess.Unknown)
            assertEquals(
                "${e::class.simpleName} must be NETWORK",
                FailureKind.NETWORK,
                kind,
            )
            assertNotEquals(
                "a transport failure must never be explained as school state",
                FailureKind.SCHOOL_STATE,
                kind,
            )
        }
    }

    /**
     * The message a user sees for a transport failure must talk about
     * connectivity — never about the school's account standing.
     */
    @Test
    fun `a network failure never renders suspension wording`() {
        val msg = SchoolLifecycle.messageFor(FailureKind.NETWORK, SchoolAccess.Unknown)

        for (forbidden in listOf("on hold", "suspended", "unavailable", "account matter", "closed")) {
            assertFalse(
                "network message must not imply a suspension — got: $msg",
                msg.contains(forbidden, ignoreCase = true),
            )
        }
        assertTrue(
            "network message should point at connectivity — got: $msg",
            msg.contains("offline", ignoreCase = true) || msg.contains("connection", ignoreCase = true),
        )
    }

    // ── 3. The source guarantees behind the two fixes ────────────────

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (!File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile ?: break
        return dir
    }

    private fun source(rel: String): String {
        val f = File(repoRoot(), rel)
        assertTrue("locator stale — missing ${f.path}", f.exists())
        return f.readText()
    }

    /** Code only: a `//` line is prose describing the fix, not the fix. */
    private fun codeOnly(s: String) =
        s.lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }
            .joinToString("\n")

    @Test
    fun `the repository fails open on every read failure`() {
        val src = codeOnly(
            source("app/src/main/java/com/schoolsync/teacher/data/lifecycle/SchoolLifecycleRepository.kt")
        )

        // Exception, absent doc and absent schoolId all land on Unknown.
        assertTrue(
            "a thrown read must fall back to Unknown",
            Regex("""catch[^{]*\{[^}]*SchoolAccess\.Unknown""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(src),
        )
        assertTrue(
            "a missing document must fall back to Unknown",
            src.contains("?: SchoolAccess.Unknown"),
        )
        assertTrue(
            "the live flow must fall back to Unknown too",
            src.contains("catch { emit(SchoolAccess.Unknown) }"),
        )
    }

    /**
     * THE TA-D2 GUARD. A restrictive verdict derived from Firestore's offline
     * cache must be downgraded to Unknown, because nothing has re-confirmed it.
     */
    @Test
    fun `a restrictive verdict from cached data is downgraded to unknown`() {
        val src = codeOnly(
            source("app/src/main/java/com/schoolsync/teacher/data/lifecycle/SchoolLifecycleRepository.kt")
        )

        assertTrue(
            "mapSnapshot must consult snapshot.metadata.isFromCache — otherwise an " +
                "offline device can lock a teacher out on a stale value (TA-D2)",
            src.contains("metadata.isFromCache"),
        )
        assertTrue(
            "a cached restrictive verdict must become Unknown, not a lock-out",
            Regex("""isFromCache\s*&&\s*!resolved\.canUseApp""").containsMatchIn(src),
        )
    }

    /**
     * The `cancel(error)` → `close(error)` fix. `cancel()` raises a
     * CancellationException that Flow's `.catch {}` never intercepts, so the
     * fail-open emission downstream could not run.
     */
    @Test
    fun `observeDocument closes rather than cancels so fail-open can run`() {
        val src = codeOnly(
            source("app/src/main/java/com/schoolsync/teacher/data/firebase/FirestoreService.kt")
        )

        val start = src.indexOf("fun observeDocument(collection: String, docId: String)")
        assertTrue("locator stale", start >= 0)
        val body = src.substring(start, minOf(start + 900, src.length))

        assertTrue("must propagate via close(error)", body.contains("close(error)"))
        assertEquals(
            "cancel() raises CancellationException, which .catch{} never intercepts — " +
                "the downstream fail-open emit(Unknown) would silently never run (TA-D2)",
            0,
            Regex("""cancel\(""").findAll(body).count(),
        )
    }
}
