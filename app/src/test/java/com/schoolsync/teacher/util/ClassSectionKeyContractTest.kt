package com.schoolsync.teacher.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * THE KEY THIS APP BUILDS MUST BE THE KEY THE SCHOOL WROTE.
 *
 * Timetable reads narrow on `sectionKey`, built locally from a class and
 * section. The admin panel writes that field through one normaliser
 * (Entity_firestore_sync::normalizeClassSection) and the timetable projection
 * rebuilds it from canonical parts before it reaches the collection this app
 * queries. A key built to a different rule matches nothing, and the teacher is
 * shown "No timetable yet" for a class that has one.
 *
 * The expectations are the output of running that PHP normaliser on each input,
 * not a restatement of this app's own logic.
 */
class ClassSectionKeyContractTest {

    private val canonicalClass = listOf(
        "8" to "Class 8th",
        "Class 8" to "Class 8th",      // prefix without the ordinal
        "Class 8th" to "Class 8th",
        "1" to "Class 1st",
        "2" to "Class 2nd",
        "3" to "Class 3rd",
        "11" to "Class 11th",
        "12" to "Class 12th",
        "13" to "Class 13th",
        "Nursery" to "Class Nursery",
        "LKG" to "Class LKG",
    )

    @Test
    fun classKeyMatchesTheCanonicalNormaliser() {
        for ((input, expected) in canonicalClass) {
            assertEquals(
                "classKey(\"$input\") must equal what the admin panel stored",
                expected,
                Constants.classKey(input)
            )
        }
    }

    @Test
    fun sectionKeyMatchesTheCanonicalNormaliser() {
        assertEquals("Section A", Constants.sectionKey("A"))
        assertEquals("Section A", Constants.sectionKey("Section A"))
    }

    @Test
    fun theSectionKeyAQueryUsesIsTheOneTheProjectionWrites() {
        assertEquals(
            "Class 8th/Section A",
            Constants.classKey("8") + "/" + Constants.sectionKey("A")
        )
    }

    /** Blank stays blank — a class-wide assignment must not become "Class ". */
    @Test
    fun blanksStayBlank() {
        assertEquals("", Constants.classKey(""))
        assertEquals("", Constants.sectionKey(""))
    }

    /**
     * The contract is "match the normaliser", not "be tidy". Fed a label that
     * is only the word Class, the admin's normaliser produces "Class Class",
     * and a school whose class is named that has documents carrying it. An app
     * that cleaned the string up to look sensible would query for something
     * nobody stored — which is the whole defect this contract exists to stop.
     */
    @Test
    fun evenTheOddCasesMatchTheNormaliser() {
        assertEquals("Class Class", Constants.classKey("Class "))
        assertEquals("Class Class", Constants.classKey("Class"))
        assertEquals("Class Classical Studies", Constants.classKey("Classical Studies"))
        assertEquals("Class 8th", Constants.classKey("class 8"))
        assertEquals("Class 8th", Constants.classKey(" 8 "))
    }
}
