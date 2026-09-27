package com.schoolsync.teacher.data.repository.firestore

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard (Student Attendance cross-app workstream, 2026-09-13).
 *
 * The Teacher app must NEVER re-introduce a direct-Firestore attendance writer.
 * Attendance is written ONLY through the server-authoritative endpoint
 * (POST attendance/save), which enforces the stage-gate, holiday and lock rules
 * and the audit trail. The former direct writers markAttendance() /
 * updateAttendanceSummary() bypassed that path and had zero callers; they were
 * removed. This test fails if either reappears on AttendanceFirestoreRepository,
 * preventing a silent regression back to a second source of truth.
 */
class AttendanceNoDirectWriterTest {

    @Test
    fun `AttendanceFirestoreRepository exposes no direct-Firestore attendance writers`() {
        val forbidden = setOf("markAttendance", "updateAttendanceSummary")
        val methodNames = AttendanceFirestoreRepository::class.java.declaredMethods
            .map { it.name }
            .toSet()
        val offenders = forbidden.intersect(methodNames)
        assertTrue(
            "Teacher app must not write attendance directly to Firestore; found reintroduced " +
                "writer(s): $offenders. Attendance writes must go through POST attendance/save " +
                "(the server-authoritative single source of truth).",
            offenders.isEmpty()
        )
    }
}
