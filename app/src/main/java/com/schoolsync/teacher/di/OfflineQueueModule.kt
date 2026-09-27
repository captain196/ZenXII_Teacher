package com.schoolsync.teacher.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.schoolsync.teacher.data.local.offline.AttendanceQueueDatabase
import com.schoolsync.teacher.data.local.offline.PendingMarkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * C1 — DI wiring for the offline attendance queue. Isolated in its own module so
 * it never touches the (heavily parallel-edited) AppModule. The queue DB is a
 * standalone Room database; WorkManager is the process-wide instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object OfflineQueueModule {

    @Provides
    @Singleton
    fun provideAttendanceQueueDatabase(
        @ApplicationContext ctx: Context
    ): AttendanceQueueDatabase =
        Room.databaseBuilder(ctx, AttendanceQueueDatabase::class.java, AttendanceQueueDatabase.NAME)
            // No fallbackToDestructiveMigration: un-synced marks must never be
            // silently dropped by a schema bump. A real Migration is required.
            .build()

    @Provides
    @Singleton
    fun providePendingMarkDao(db: AttendanceQueueDatabase): PendingMarkDao = db.pendingMarkDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)
}
