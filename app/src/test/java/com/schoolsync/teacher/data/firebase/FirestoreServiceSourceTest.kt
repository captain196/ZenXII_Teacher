package com.schoolsync.teacher.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TA-D2 — source-level regression guard.
 *
 * `observeDocument()`'s snapshot-listener error branch called `cancel(error)`
 * instead of `close(error)`. `cancel()` raises a `CancellationException`,
 * which Kotlin Flow's `.catch {}` deliberately never intercepts — catching a
 * cancellation would break structured concurrency — so every caller's
 * fail-open `.catch { emit(SchoolAccess.Unknown) }` (see
 * SchoolLifecycleRepository.observeSchoolAccess()) silently never fired on a
 * listener error: the flow just died with no fallback emission at all,
 * contradicting the class's own documented fail-open contract.
 *
 * The sibling function `observeDocumentAs()`, three lines below, already gets
 * this right and already carries a comment explaining exactly why — this is
 * the one place in the file that didn't match its own neighbour.
 *
 * No Firestore emulator or mocking harness exists in this project to drive a
 * real snapshot-listener error end-to-end in a JVM unit test, and standing
 * one up for a single line is out of scope for this fix. This test instead
 * proves the SOURCE PROPERTY directly: it can never regress back to `cancel(`
 * in this function's error branch without failing here, and it is executed —
 * not just read — on every test run.
 */
class FirestoreServiceSourceTest {

    private fun sourceFile(): File {
        // Walk up from the test's own package to the repo root; robust to
        // which working directory Gradle happens to run tests from.
        var dir = File(".").canonicalFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        val f = File(dir, "app/src/main/java/com/schoolsync/teacher/data/firebase/FirestoreService.kt")
        assertTrue("locator is stale — FirestoreService.kt not found at ${f.path}", f.exists())
        return f
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("locator anchor '$signature' not found — this test is stale", start >= 0)
        val end = source.indexOf("\n    fun ", start + signature.length)
            .let { if (it < 0) source.indexOf("\n    inline fun ", start + signature.length) else it }
        assertTrue("could not find the end of $signature", end > start)
        return source.substring(start, end)
    }

    /** Actual code only — a line whose first non-blank characters are `//` is prose. */
    private fun codeLines(body: String): List<String> =
        body.lines().filterNot { it.trim().startsWith("//") }

    @Test
    fun `observeDocument closes the flow on listener error, it does not cancel it`() {
        val source = sourceFile().readText()
        val body = functionBody(source, "fun observeDocument(collection: String, docId: String)")
        val code = codeLines(body).joinToString("\n")

        assertTrue(
            "observeDocument's error branch must call close(error) so the fail-open " +
                ".catch { emit(Unknown) } downstream can actually run",
            code.contains("close(error)"),
        )
        assertEquals(
            "observeDocument must not CALL cancel(...) on a listener error — cancel() " +
                "raises a CancellationException that .catch{} never intercepts, silently " +
                "killing the flow with no fallback and defeating fail-open (TA-D2). " +
                "(Explanatory comments that merely MENTION cancel() are excluded — " +
                "this checks executable code, not prose.)",
            0,
            Regex("""cancel\(""").findAll(code).count(),
        )
    }

    @Test
    fun `observeDocument now matches its sibling observeDocumentAs, which already did this correctly`() {
        val source = sourceFile().readText()
        val target = functionBody(source, "fun observeDocument(collection: String, docId: String)")
        val sibling = functionBody(source, "inline fun <reified T> observeDocumentAs(")

        assertTrue(target.contains("close(error)"))
        assertTrue(sibling.contains("close(error)"))
    }
}
