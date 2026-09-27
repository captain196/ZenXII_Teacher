package com.schoolsync.teacher.data.local.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C1 — collapse-key correctness and malformed-row rejection (pure JVM). */
class PendingMarkTest {

    private fun base() = PendingMark(
        id = "id-1", schoolId = "SCH_X", session = "2026-27",
        className = "Class 1st", section = "A", dateIso = "2026-09-14",
        createdAt = 1L
    )

    @Test fun collapse_key_matches_same_section_date_session() {
        val a = base()
        val b = base().copy(id = "id-2")
        assertEquals(a.collapseKey(), b.collapseKey())
    }

    @Test fun collapse_key_differs_by_date() {
        assertNotEquals(base().collapseKey(), base().copy(dateIso = "2026-09-15").collapseKey())
    }

    @Test fun collapse_key_differs_by_section() {
        assertNotEquals(base().collapseKey(), base().copy(section = "B").collapseKey())
    }

    @Test fun collapse_key_differs_by_session() {
        assertNotEquals(base().collapseKey(), base().copy(session = "2025-26").collapseKey())
    }

    @Test fun valid_row_passes() = assertTrue(base().isStructurallyValid())

    @Test fun blank_id_fails() = assertFalse(base().copy(id = "").isStructurallyValid())

    @Test fun blank_session_fails() = assertFalse(base().copy(session = "").isStructurallyValid())

    @Test fun bad_date_fails() = assertFalse(base().copy(dateIso = "14-09-2026").isStructurallyValid())

    @Test fun blank_class_or_section_fails() {
        assertFalse(base().copy(className = "").isStructurallyValid())
        assertFalse(base().copy(section = "").isStructurallyValid())
    }

    @Test fun unknown_state_fails() = assertFalse(base().copy(state = "WEIRD").isStructurallyValid())

    @Test fun nonpositive_created_at_fails() = assertFalse(base().copy(createdAt = 0L).isStructurallyValid())
}
