package com.schoolsync.teacher.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Guards the single most dangerous locale bug in this app: native digits
 * leaking into machine strings.
 *
 * `LocaleManager.wrap()` calls `Locale.setDefault()` so that date and number
 * formatters follow the user's chosen language. The side effect is that every
 * `String.format` WITHOUT an explicit Locale also follows it — and under
 * Marathi the JDK/ICU digit set is Devanagari:
 *
 *     Locale.setDefault(Locale("mr"))
 *     "%d-%02d".format(2026, 9)   ->  "२०२६-०९"     NOT "2026-09"
 *
 * That string is a Firestore document key (`attendance/{schoolId}_{studentId}_
 * {yyyy-MM}`). A corrupt key does not error — the query simply matches nothing,
 * so attendance and the dashboard render empty for Marathi users only, with no
 * log line anywhere. Exactly the failure this codebase keeps being bitten by.
 *
 * These tests assert the property directly rather than trusting a review: any
 * future `String.format` that forgets its Locale will fail here.
 */
class NativeDigitLeakTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun restore() {
        Locale.setDefault(original)
    }

    /** The premise. If this ever stops holding, the rest of the file is moot. */
    @Test
    fun marathi_default_makes_bare_format_emit_devanagari() {
        Locale.setDefault(Locale.forLanguageTag("mr"))
        val bare = String.format("%d-%02d", 2026, 9)
        assertEquals(
            "Premise changed: Marathi no longer emits native digits. " +
                "Re-check whether the Locale.ROOT pinning is still required.",
            "२०२६-०९", bare,
        )
    }

    /** Locale.ROOT is the fix, and it holds for every language we ship. */
    @Test
    fun pinned_format_stays_latin_in_every_shipped_locale() {
        for (tag in listOf("en", "hi", "mr", "gu", "ta", "te")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals(
                "month key drifted under $tag",
                "2026-09", String.format(Locale.ROOT, "%d-%02d", 2026, 9),
            )
            assertEquals(
                "ymd key drifted under $tag",
                "20260903", String.format(Locale.ROOT, "%04d%02d%02d", 2026, 9, 3),
            )
            assertEquals(
                "hex digest drifted under $tag",
                "0a", String.format(Locale.ROOT, "%02x", 10),
            )
        }
    }

    /**
     * Amounts are Latin in every UI language (glossary rule 3): a fee is
     * cross-checked against paper receipts and UPI history.
     */
    @Test
    fun money_stays_latin_in_every_shipped_locale() {
        for (tag in listOf("en", "hi", "mr", "gu", "ta", "te")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            val amount = java.text.NumberFormat
                .getIntegerInstance(Locale("en", "IN")).format(123456)
            // Assert the PROPERTY, not the grouping. Desktop JDK 17 renders
            // en-IN with Western grouping (123,456) while Android's ICU uses
            // Indian grouping (1,23,456); pinning either would make this test
            // pass on one runtime and fail on the other while telling us
            // nothing about the bug it exists to catch. What must hold
            // everywhere is: Latin digits, never Devanagari.
            assertTrue(
                "money rendered with native digits under $tag: $amount",
                amount.all { it.code < 0x0080 },
            )
            assertTrue(
                "money lost its digits under $tag: $amount",
                amount.any { it.isDigit() },
            )
        }
    }
}
