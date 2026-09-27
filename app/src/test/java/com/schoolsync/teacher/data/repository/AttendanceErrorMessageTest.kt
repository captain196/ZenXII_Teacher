package com.schoolsync.teacher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TA-D1 — a raw exception message must never reach a teacher's screen.
 *
 * OBSERVED ON A REAL DEVICE: "My Correction Requests" rendered
 *
 *     Failed to connect to localhost/127.0.0.1:8080
 *
 * in red, centred, as the entire content of the screen. That string is
 * `java.net.ConnectException.message` from OkHttp. It names a host and a port,
 * offers a teacher nothing they can act on, and exposes internal wiring.
 *
 * ROOT CAUSE: `AttendanceApiRepository` caught transport failures and rethrew
 * them as `AttendanceApiError.Network(e.message ?: "Network error")` at five
 * separate sites — promoting the raw throwable text into a "user-facing"
 * error type. `AttendanceViewModel.loadMyCorrections()` then assigned that
 * straight to `requestsError`, which the screen renders verbatim.
 *
 * The raw detail was never lost: every one of those sites already called
 * `Log.e(TAG, ...)` first. Only the DISPLAYED text changed.
 *
 * NOT AFFECTED — and deliberately so: the other `AttendanceApiError` subtypes
 * (Locked, Holiday, Conflict, Forbidden, ReasonRequired, NotFound) carry
 * server-authored sentences meant for the user, are produced by `toError()`,
 * and are thrown before this fallback is reached.
 */
class AttendanceErrorMessageTest {

    // ── The message itself ────────────────────────────────────────────

    @Test
    fun `the fallback message never exposes internals`() {
        val msg = AttendanceApiRepository.UNREACHABLE_MESSAGE

        for (leak in listOf(
            "localhost", "127.0.0.1", "8080", "http", "Exception",
            "OkHttp", "socket", "Failed to connect", "null",
        )) {
            assertFalse(
                "the message shown to a teacher must not contain '$leak' — got: $msg",
                msg.contains(leak, ignoreCase = true),
            )
        }
    }

    /**
     * The product is ZenXii (double i) — "ZenX" is the repository/codebase
     * shorthand and must never appear in text a teacher reads.
     */
    @Test
    fun `the fallback message uses the product name ZenXii, not the internal shorthand`() {
        val msg = AttendanceApiRepository.UNREACHABLE_MESSAGE

        assertTrue("should name the product — got: $msg", msg.contains("ZenXii"))
        assertFalse(
            "'ZenX' without the second i is the internal shorthand, not the product name — got: $msg",
            Regex("""ZenX(?!ii)""").containsMatchIn(msg),
        )
    }

    @Test
    fun `the fallback message tells the user what happened and what to do`() {
        val msg = AttendanceApiRepository.UNREACHABLE_MESSAGE

        assertTrue("must be a real sentence, not a code", msg.length > 20)
        assertTrue(
            "must name the actionable next step (connection / try again) — got: $msg",
            msg.contains("connection", ignoreCase = true) || msg.contains("internet", ignoreCase = true),
        )
        assertTrue(
            "must invite a retry — got: $msg",
            msg.contains("try again", ignoreCase = true),
        )
        assertFalse(
            "'Something went wrong' explains nothing; prefer a specific cause",
            msg.trim().equals("Something went wrong.", ignoreCase = true),
        )
    }

    // ── The source property that produced the leak ────────────────────

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return dir
    }

    private fun sourceOf(relative: String): String {
        val f = File(repoRoot(), relative)
        assertTrue("locator is stale — not found: ${f.path}", f.exists())
        return f.readText()
    }

    /** Lines that are actual code — a `//` line is prose about the fix. */
    private fun codeOnly(src: String): String =
        src.lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }
            .joinToString("\n")

    @Test
    fun `the repository no longer promotes a raw throwable message into a user-facing error`() {
        val src = codeOnly(
            sourceOf("app/src/main/java/com/schoolsync/teacher/data/repository/AttendanceApiRepository.kt")
        )

        assertEquals(
            "AttendanceApiError.Network(e.message ...) puts the raw transport text on screen " +
                "(TA-D1). Throw AttendanceApiRepository.UNREACHABLE_MESSAGE instead — the real " +
                "throwable is already sent to Log.e on every one of these paths.",
            0,
            Regex("""AttendanceApiError\.Network\(\s*e\.message""").findAll(src).count(),
        )

        assertTrue(
            "the transport-failure sites should use the shared user-facing constant",
            src.contains("AttendanceApiError.Network(UNREACHABLE_MESSAGE)"),
        )
    }

    @Test
    fun `the view model does not render an untyped throwable message`() {
        val src = codeOnly(
            sourceOf("app/src/main/java/com/schoolsync/teacher/ui/attendance/AttendanceViewModel.kt")
        )

        assertEquals(
            "requestsError = e.message puts a raw exception on screen (TA-D1). Only an " +
                "AttendanceApiError message is written for a teacher to read.",
            0,
            Regex("""requestsError\s*=\s*e\.message""").findAll(src).count(),
        )
    }

    /**
     * The typed errors are the ones that SHOULD reach the user, so the fix
     * must not have flattened them into the generic sentence.
     */
    @Test
    fun `server authored messages still pass through untouched`() {
        val locked = AttendanceApiError.Locked("This month is locked.")
        val holiday = AttendanceApiError.Holiday("That date is a holiday.")

        assertEquals("This month is locked.", locked.message)
        assertEquals("That date is a holiday.", holiday.message)
        assertTrue(locked is AttendanceApiError)
    }
}
