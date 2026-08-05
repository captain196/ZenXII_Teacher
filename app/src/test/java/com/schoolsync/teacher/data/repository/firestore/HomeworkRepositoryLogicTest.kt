package com.schoolsync.teacher.data.repository.firestore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure, framework-free helpers on
 * [HomeworkFirestoreRepository]'s companion:
 *   - mintHwId          (id FORMAT invariants)
 *   - normalizeDueDate  (write-side dueDate normalisation)
 *   - sanitizeReviewStatus (review-status whitelist)
 *
 * No Firebase/Android at call time, so these run under a plain JVM test.
 */
class HomeworkRepositoryLogicTest {

    // ── mintHwId — structural invariants only (uses SecureRandom + clock) ─

    @Test
    fun mintHwId_hasSchoolPrefixEpochAndHexSuffix() {
        val school = "SCH_123456"
        val id = HomeworkFirestoreRepository.mintHwId(school)
        // {schoolCode}_{epochMs}_{8 hex}. schoolCode itself contains an
        // underscore, so match the whole shape with a regex anchored on it.
        val re = Regex("^${Regex.escape(school)}_\\d+_[0-9a-f]{8}$")
        assertTrue("id did not match expected shape: $id", re.matches(id))
    }

    @Test
    fun mintHwId_epochSegmentIsPositiveLong() {
        val id = HomeworkFirestoreRepository.mintHwId("SCH_1")
        // strip the "SCH_1_" prefix, then take the segment before the hex suffix.
        val afterPrefix = id.removePrefix("SCH_1_")
        val epochPart = afterPrefix.substringBefore('_')
        val ms = epochPart.toLongOrNull()
        assertTrue("epoch segment not a positive long: $epochPart", ms != null && ms > 0L)
    }

    @Test
    fun mintHwId_hexSuffixIsExactlyEightLowercaseHex() {
        val id = HomeworkFirestoreRepository.mintHwId("SCH_9")
        val suffix = id.substringAfterLast('_')
        assertEquals(8, suffix.length)
        assertTrue("suffix not lowercase hex: $suffix", Regex("^[0-9a-f]{8}$").matches(suffix))
    }

    @Test
    fun mintHwId_twoCallsDiffer_dueToRandomSuffix() {
        val a = HomeworkFirestoreRepository.mintHwId("SCH_X")
        val b = HomeworkFirestoreRepository.mintHwId("SCH_X")
        assertNotEquals(a, b)
    }

    // ── normalizeDueDate ─────────────────────────────────────────────────

    @Test
    fun normalizeDueDate_empty_returnsEmpty() {
        assertEquals("", HomeworkFirestoreRepository.normalizeDueDate(""))
    }

    @Test
    fun normalizeDueDate_whitespaceOnly_returnsEmpty() {
        assertEquals("", HomeworkFirestoreRepository.normalizeDueDate("   "))
    }

    @Test
    fun normalizeDueDate_dateOnly_pinsToEndOfDayIST() {
        assertEquals(
            "2026-05-06T23:59:59+05:30",
            HomeworkFirestoreRepository.normalizeDueDate("2026-05-06")
        )
    }

    @Test
    fun normalizeDueDate_dateOnly_trimsThenNormalises() {
        assertEquals(
            "2026-05-06T23:59:59+05:30",
            HomeworkFirestoreRepository.normalizeDueDate("  2026-05-06  ")
        )
    }

    @Test
    fun normalizeDueDate_isoWithColonOffset_passesThrough() {
        val s = "2026-05-06T23:59:59+05:30"
        assertEquals(s, HomeworkFirestoreRepository.normalizeDueDate(s))
    }

    @Test
    fun normalizeDueDate_isoWithNoColonOffset_passesThrough() {
        val s = "2026-05-06T23:59:59+0530"
        assertEquals(s, HomeworkFirestoreRepository.normalizeDueDate(s))
    }

    @Test
    fun normalizeDueDate_isoZulu_passesThrough() {
        val s = "2026-05-06T10:00:00Z"
        assertEquals(s, HomeworkFirestoreRepository.normalizeDueDate(s))
    }

    @Test
    fun normalizeDueDate_garbage_passesThroughUnchanged() {
        assertEquals("banana", HomeworkFirestoreRepository.normalizeDueDate("banana"))
    }

    /**
     * DOCUMENTS CURRENT BEHAVIOUR (see finding in report): unlike the reader
     * parseDueInstant(), the write-side normalizeDueDate() does NOT recognise
     * the legacy dd-MM-yyyy / dd/MM/yyyy shapes — it returns them verbatim
     * rather than normalising to an end-of-day ISO instant.
     */
    @Test
    fun normalizeDueDate_legacyDdMmYyyy_isLeftUnnormalised_currentBehaviour() {
        assertEquals("06-05-2026", HomeworkFirestoreRepository.normalizeDueDate("06-05-2026"))
        assertEquals("06/05/2026", HomeworkFirestoreRepository.normalizeDueDate("06/05/2026"))
    }

    // ── sanitizeReviewStatus ─────────────────────────────────────────────

    @Test
    fun sanitizeReviewStatus_acceptsWhitelistValues() {
        assertEquals("reviewed", HomeworkFirestoreRepository.sanitizeReviewStatus("reviewed"))
        assertEquals("complete", HomeworkFirestoreRepository.sanitizeReviewStatus("complete"))
        assertEquals("incomplete", HomeworkFirestoreRepository.sanitizeReviewStatus("incomplete"))
        assertEquals("submitted", HomeworkFirestoreRepository.sanitizeReviewStatus("submitted"))
        assertEquals("pending", HomeworkFirestoreRepository.sanitizeReviewStatus("pending"))
    }

    @Test
    fun sanitizeReviewStatus_lowercasesAcceptedValues() {
        assertEquals("complete", HomeworkFirestoreRepository.sanitizeReviewStatus("Complete"))
        assertEquals("incomplete", HomeworkFirestoreRepository.sanitizeReviewStatus("INCOMPLETE"))
    }

    @Test
    fun sanitizeReviewStatus_unknownFallsBackToReviewed() {
        assertEquals("reviewed", HomeworkFirestoreRepository.sanitizeReviewStatus("banana"))
        assertEquals("reviewed", HomeworkFirestoreRepository.sanitizeReviewStatus(""))
    }

    /**
     * DOCUMENTS CURRENT BEHAVIOUR: the whitelist does not trim, so a padded
     * but otherwise-valid value (" reviewed ") is not in the accepted set and
     * falls back to the "reviewed" default (same output here, coincidentally).
     */
    @Test
    fun sanitizeReviewStatus_doesNotTrim_currentBehaviour() {
        assertEquals("reviewed", HomeworkFirestoreRepository.sanitizeReviewStatus(" reviewed "))
    }
}
