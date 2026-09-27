package com.schoolsync.teacher.data.local.offline

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * C1 — isolated Room database for the offline attendance queue ONLY.
 *
 * Deliberately its own database (not merged into any existing store) so this
 * feature adds nothing to, and can never corrupt, unrelated persistence. Schema
 * v1. Any future column change must ship a Room Migration — never
 * fallbackToDestructiveMigration, which would silently drop un-synced marks.
 */
@Database(
    entities = [PendingMark::class],
    version = 1,
    exportSchema = false
)
abstract class AttendanceQueueDatabase : RoomDatabase() {
    abstract fun pendingMarkDao(): PendingMarkDao

    companion object {
        const val NAME = "attendance_queue.db"
    }
}
