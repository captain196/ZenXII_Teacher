package com.schoolsync.teacher.data.local.offline

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C1 — durable queue DAO behaviour on an in-memory Room DB (JVM via Robolectric):
 * ordering, supersession scope, crash recovery, state transitions, persistence.
 *
 * Pinned to SDK 34: Robolectric 4.11.1 has no SDK-35 sandbox, and Room/SQLite
 * behaviour here is identical across 34/35.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingMarkDaoTest {

    private lateinit var db: AttendanceQueueDatabase
    private lateinit var dao: PendingMarkDao

    private var seq = 0L
    private fun mark(
        id: String,
        session: String = "2026-27",
        className: String = "Class 1st",
        section: String = "A",
        dateIso: String = "2026-09-14",
        state: String = QueueState.PENDING,
        createdAt: Long = ++seq
    ) = PendingMark(
        id = id, schoolId = "SCH_X", session = session, className = className,
        section = section, dateIso = dateIso, createdAt = createdAt, state = state
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttendanceQueueDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.pendingMarkDao()
    }

    @After fun tearDown() = db.close()

    @Test fun insert_and_read_back() = runBlocking {
        dao.upsert(mark("a"))
        assertEquals("a", dao.byId("a")?.id)
        assertEquals(1, dao.count())
    }

    @Test fun sendable_is_oldest_first_and_excludes_terminal() = runBlocking {
        dao.upsert(mark("old", createdAt = 100))
        dao.upsert(mark("new", createdAt = 200))
        dao.upsert(mark("terminal", createdAt = 150, state = QueueState.FAILED_TERMINAL))
        dao.upsert(mark("retry", createdAt = 120, state = QueueState.FAILED_RETRYABLE))
        val ids = dao.sendable().map { it.id }
        assertEquals(listOf("old", "retry", "new"), ids)   // terminal excluded, oldest→newest
    }

    @Test fun supersede_drops_older_same_key_keeps_keeper_and_others() = runBlocking {
        dao.upsert(mark("k1", createdAt = 10))
        dao.upsert(mark("k2", createdAt = 20))                 // same collapse key, keeper
        dao.upsert(mark("otherSection", section = "B", createdAt = 15))
        dao.upsert(mark("otherDate", dateIso = "2026-09-15", createdAt = 16))
        val dropped = dao.supersede("k2", "2026-27", "Class 1st", "A", "2026-09-14")
        assertEquals(1, dropped)                              // only k1
        assertNull(dao.byId("k1"))
        assertEquals("k2", dao.byId("k2")?.id)
        assertEquals("otherSection", dao.byId("otherSection")?.id)
        assertEquals("otherDate", dao.byId("otherDate")?.id)
    }

    @Test fun supersede_never_drops_terminal_audit_rows() = runBlocking {
        dao.upsert(mark("term", createdAt = 10, state = QueueState.FAILED_TERMINAL))
        dao.upsert(mark("keeper", createdAt = 20))
        val dropped = dao.supersede("keeper", "2026-27", "Class 1st", "A", "2026-09-14")
        assertEquals(0, dropped)
        assertEquals("term", dao.byId("term")?.id)           // terminal survives
    }

    @Test fun recover_stuck_sending_resets_to_pending() = runBlocking {
        dao.upsert(mark("s1", state = QueueState.SENDING))
        dao.upsert(mark("s2", state = QueueState.SENDING))
        dao.upsert(mark("p", state = QueueState.PENDING))
        val recovered = dao.recoverStuckSending(999)
        assertEquals(2, recovered)
        assertEquals(QueueState.PENDING, dao.byId("s1")?.state)
        assertEquals(QueueState.PENDING, dao.byId("s2")?.state)
    }

    @Test fun update_state_sets_error_and_attempts() = runBlocking {
        dao.upsert(mark("a"))
        dao.updateState("a", QueueState.FAILED_RETRYABLE, "network", 3, 555)
        val r = dao.byId("a")!!
        assertEquals(QueueState.FAILED_RETRYABLE, r.state)
        assertEquals("network", r.lastError)
        assertEquals(3, r.attempts)
    }

    @Test fun delete_removes_row() = runBlocking {
        dao.upsert(mark("a"))
        dao.delete("a")
        assertNull(dao.byId("a"))
    }

    @Test fun clear_terminal_only() = runBlocking {
        dao.upsert(mark("t", state = QueueState.FAILED_TERMINAL))
        dao.upsert(mark("p", state = QueueState.PENDING))
        assertEquals(1, dao.clearTerminal())
        assertNull(dao.byId("t"))
        assertEquals("p", dao.byId("p")?.id)
    }

    @Test fun pending_count_flow_counts_active_only() = runBlocking {
        dao.upsert(mark("p", state = QueueState.PENDING))
        dao.upsert(mark("s", state = QueueState.SENDING))
        dao.upsert(mark("r", state = QueueState.FAILED_RETRYABLE))
        dao.upsert(mark("t", state = QueueState.FAILED_TERMINAL))
        assertEquals(3, dao.observePendingCount().first())   // terminal excluded
    }

    @Test fun upsert_replaces_same_id() = runBlocking {
        dao.upsert(mark("a", section = "A"))
        dao.upsert(mark("a", section = "B"))
        assertEquals(1, dao.count())
        assertEquals("B", dao.byId("a")?.section)
    }
}
