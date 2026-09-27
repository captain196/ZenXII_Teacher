package com.schoolsync.teacher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * TT-F1 — THE ADDRESS OF A TIMETABLE DOCUMENT IS A CROSS-SYSTEM CONTRACT.
 *
 * `timetables` is written by the admin panel as the compatibility projection of
 * the published canonical timetable, at
 *
 *     {schoolId}_{session}_{Class Nth}_{Section X}_{Day}
 *
 * The Teacher app addresses one of those documents directly for the
 * period-match guard on saving a lesson plan. It used to build the id itself as
 * `"${schoolId}_${session}_${className}/${section}_$day"`, and that is wrong in
 * two independent ways:
 *
 *   • the SLASH. A '/' in a document id is a path separator, so
 *     `collection("timetables").document("a/b")` is a COLLECTION reference and
 *     Firestore rejects it as an invalid document reference. It throws rather
 *     than reporting "not found", the repository's catch reports a failed read,
 *     and every lesson-plan save ended with "The timetable for this day could
 *     not be read" — for every teacher, every time.
 *
 *   • the RAW class and section. The query path already normalises through
 *     classKey/sectionKey, so a caller holding the catalogue's "8"/"A" built an
 *     id no document ever had while the very same values queried fine.
 *
 * These tests pin the id against the admin panel's construction, and pin the
 * property that made the failure so hard to see: the id must never contain a
 * character Firestore treats as structure.
 */
class TimetableDocIdContractTest {

    private val SCHOOL = "SCH_B56BB9A401"
    private val SESSION = "2026-27"

    /**
     * The admin panel's Timetable_projection_service:
     *   safeKey = str_replace('/', '_', sectionKey);
     *   docId   = "{schoolId}_{session}_{safeKey}_{day}";
     * with sectionKey = canonical className + "/" + canonical section.
     */
    private fun adminPanelId(cls: String, sec: String, day: String) =
        "${SCHOOL}_${SESSION}_" + "$cls/$sec".replace("/", "_") + "_$day"

    @Test
    fun `the id matches what the admin panel writes`() {
        assertEquals(
            adminPanelId("Class 9th", "Section A", "Monday"),
            Constants.timetableDocId(SCHOOL, SESSION, "Class 9th", "Section A", "Monday")
        )
    }

    @Test
    fun `the catalogue's short forms address the same document`() {
        val canonical = Constants.timetableDocId(SCHOOL, SESSION, "Class 8th", "Section A", "Friday")

        // The Config/Classes catalogue stores key="8" and label="Class 8th";
        // whichever one a screen happens to be holding must not change which
        // document is read.
        assertEquals(canonical, Constants.timetableDocId(SCHOOL, SESSION, "8", "A", "Friday"))
        assertEquals(canonical, Constants.timetableDocId(SCHOOL, SESSION, "Class 8", "Section A", "Friday"))
        assertEquals(canonical, Constants.timetableDocId(SCHOOL, SESSION, " 8 ", " A ", " Friday "))
    }

    @Test
    fun `the id never contains a path separator`() {
        // This is the property, not an example of it. A '/' here does not
        // produce a missing document — it produces a thrown exception that
        // reads to the user as "your timetable could not be read".
        for (cls in listOf("Class 9th", "9", "Class 9", "LKG", "Nursery", "Class 12th")) {
            for (sec in listOf("Section A", "A", "Section B")) {
                val id = Constants.timetableDocId(SCHOOL, SESSION, cls, sec, "Monday")
                assertFalse("`$id` would be an invalid document reference", id.contains("/"))
            }
        }
    }

    @Test
    fun `non-numeric classes keep their own names`() {
        assertEquals(
            "${SCHOOL}_${SESSION}_Class LKG_Section A_Tuesday",
            Constants.timetableDocId(SCHOOL, SESSION, "LKG", "A", "Tuesday")
        )
    }

    @Test
    fun `the id agrees with the sectionKey the query path filters on`() {
        // The two consumer paths must land on the same class-section. The query
        // filters `sectionKey == "$classKey/$sectionKey"`; the direct read
        // addresses the same pair with the slash replaced.
        val cls = Constants.classKey("9")
        val sec = Constants.sectionKey("A")
        val querySectionKey = "$cls/$sec"

        assertEquals(
            "${SCHOOL}_${SESSION}_" + querySectionKey.replace("/", "_") + "_Wednesday",
            Constants.timetableDocId(SCHOOL, SESSION, "9", "A", "Wednesday")
        )
    }
}
