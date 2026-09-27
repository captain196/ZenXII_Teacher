package com.schoolsync.teacher.data.local.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * C1 — durable queue DAO. All mutations are single SQL statements so the queue
 * cannot be left half-written across a crash.
 */
@Dao
interface PendingMarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: PendingMark)

    @Query("SELECT * FROM pending_mark WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PendingMark?

    /** Everything, newest activity first — for the UI status list. */
    @Query("SELECT * FROM pending_mark ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingMark>>

    /** Rows still worth sending, OLDEST first (submission order preserved). */
    @Query(
        "SELECT * FROM pending_mark WHERE state IN ('PENDING','FAILED_RETRYABLE') " +
        "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun sendable(): List<PendingMark>

    /** Rows the teacher still has in flight or waiting (for the badge count). */
    @Query("SELECT COUNT(*) FROM pending_mark WHERE state IN ('PENDING','SENDING','FAILED_RETRYABLE')")
    fun observePendingCount(): Flow<Int>

    /**
     * Supersession: older queued rows for the same section+date+session that are
     * NOT the keeper and are still safe to drop (never terminal — a terminal row
     * is an audit fact the teacher must see). Different students / dates /
     * sessions have different collapse keys and are never touched.
     */
    @Query(
        "DELETE FROM pending_mark WHERE id != :keepId AND session = :session " +
        "AND className = :className AND section = :section AND dateIso = :dateIso " +
        "AND state IN ('PENDING','FAILED_RETRYABLE')"
    )
    suspend fun supersede(
        keepId: String, session: String, className: String, section: String, dateIso: String
    ): Int

    @Query("UPDATE pending_mark SET state = :state, lastError = :lastError, attempts = :attempts, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: String, state: String, lastError: String, attempts: Int, now: Long)

    /**
     * Crash recovery: a row left SENDING by a killed process is reset to PENDING
     * so it is retried. Safe because the server write is merge-idempotent — a
     * re-send of an already-applied mark is a no-op, and the same UUID is reused.
     */
    @Query("UPDATE pending_mark SET state = 'PENDING', updatedAt = :now WHERE state = 'SENDING'")
    suspend fun recoverStuckSending(now: Long): Int

    @Query("DELETE FROM pending_mark WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_mark WHERE state = 'FAILED_TERMINAL'")
    suspend fun clearTerminal(): Int

    @Query("SELECT COUNT(*) FROM pending_mark")
    suspend fun count(): Int
}
