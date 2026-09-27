package com.schoolsync.teacher.data.local.offline

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C1 — the durable queue on a REAL device's SQLite + Room runtime (Android 13).
 *
 * The Robolectric suite proved the same logic on a simulated runtime; this proves
 * it on the actual hardware persistence layer: ordering, supersession scope,
 * crash-recovery reset, state transitions, and durability across a close/reopen
 * of a file-backed database (the app's real on-disk queue).
 */
@RunWith(AndroidJUnit4::class)
class PendingMarkDaoInstrumentedTest {

    private lateinit var db: AttendanceQueueDatabase
    private lateinit var dao: PendingMarkDao
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var seq = 0L
    private fun mark(
        id: String, session: String = "2026-27", className: String = "Class 1st",
        section: String = "A", dateIso: String = "2026-09-14",
        state: String = QueueState.PENDING, createdAt: Long = ++seq
    ) = PendingMark(
        id = id, schoolId = "SCH_X", session = session, className = className,
        section = section, dateIso = dateIso, createdAt = createdAt, state = state
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AttendanceQueueDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.pendingMarkDao()
    }

    @After fun tearDown() = db.close()

    @Test fun insert_and_read_back() = runBlocking {
        dao.upsert(mark("a")); assertEquals("a", dao.byId("a")?.id); assertEquals(1, dao.count())
    }

    @Test fun sendable_oldest_first_excludes_terminal() = runBlocking {
        dao.upsert(mark("old", createdAt = 100))
        dao.upsert(mark("new", createdAt = 200))
        dao.upsert(mark("terminal", createdAt = 150, state = QueueState.FAILED_TERMINAL))
        dao.upsert(mark("retry", createdAt = 120, state = QueueState.FAILED_RETRYABLE))
        assertEquals(listOf("old", "retry", "new"), dao.sendable().map { it.id })
    }

    @Test fun supersede_scope_is_exact() = runBlocking {
        dao.upsert(mark("k1", createdAt = 10))
        dao.upsert(mark("k2", createdAt = 20))
        dao.upsert(mark("otherSection", section = "B", createdAt = 15))
        dao.upsert(mark("otherDate", dateIso = "2026-09-15", createdAt = 16))
        assertEquals(1, dao.supersede("k2", "2026-27", "Class 1st", "A", "2026-09-14"))
        assertNull(dao.byId("k1"))
        assertEquals("k2", dao.byId("k2")?.id)
        assertEquals("otherSection", dao.byId("otherSection")?.id)
        assertEquals("otherDate", dao.byId("otherDate")?.id)
    }

    @Test fun supersede_never_drops_terminal() = runBlocking {
        dao.upsert(mark("term", createdAt = 10, state = QueueState.FAILED_TERMINAL))
        dao.upsert(mark("keeper", createdAt = 20))
        assertEquals(0, dao.supersede("keeper", "2026-27", "Class 1st", "A", "2026-09-14"))
        assertEquals("term", dao.byId("term")?.id)
    }

    @Test fun recover_stuck_sending() = runBlocking {
        dao.upsert(mark("s1", state = QueueState.SENDING))
        dao.upsert(mark("s2", state = QueueState.SENDING))
        assertEquals(2, dao.recoverStuckSending(999))
        assertEquals(QueueState.PENDING, dao.byId("s1")?.state)
    }

    @Test fun pending_count_excludes_terminal() = runBlocking {
        dao.upsert(mark("p", state = QueueState.PENDING))
        dao.upsert(mark("s", state = QueueState.SENDING))
        dao.upsert(mark("r", state = QueueState.FAILED_RETRYABLE))
        dao.upsert(mark("t", state = QueueState.FAILED_TERMINAL))
        assertEquals(3, dao.observePendingCount().first())
    }

    @Test fun durable_across_close_reopen_on_disk() = runBlocking {
        // File-backed DB → prove a queued mark survives a DB close/reopen, the
        // on-disk analogue of process death the app relies on.
        val name = "c1_durability_probe.db"
        ctx.deleteDatabase(name)
        var d = Room.databaseBuilder(ctx, AttendanceQueueDatabase::class.java, name).build()
        d.pendingMarkDao().upsert(mark("persist", createdAt = 42))
        d.close()
        d = Room.databaseBuilder(ctx, AttendanceQueueDatabase::class.java, name).build()
        assertEquals("persist", d.pendingMarkDao().byId("persist")?.id)
        d.close()
        ctx.deleteDatabase(name)
    }
}
