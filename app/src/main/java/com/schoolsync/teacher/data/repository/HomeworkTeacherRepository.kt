package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-backed Homework repository for teacher-side dashboard queries.
 * Used by [DashboardViewModel] to count homework due today.
 *
 * The legacy RTDB read path at
 * `Schools/{schoolCode}/{session}/{cls}/{sec}/Homework` was deleted —
 * admin no longer writes there, so the count silently rendered 0.
 * Canonical store is the `homework` collection (camelCase, sectionKey
 * "{cls}/{sec}", ISO `dueDate`).
 */
@Singleton
class HomeworkTeacherRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Count active homework assignments due today for a class/section.
     *
     * Date format note: admin and Teacher's own [HomeworkFirestoreRepository]
     * write `dueDate` as ISO `yyyy-MM-dd`. The previous RTDB-shaped caller
     * passed `dd MMM yyyy`, which would have matched zero docs. We compute
     * today inside the repo so callers can't pass the wrong format.
     */
    suspend fun getHomeworkDueTodayCount(
        className: String,
        section: String,
        @Suppress("UNUSED_PARAMETER") todayDate: String = ""
    ): Int {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull() ?: return 0
            val cls = Constants.classKey(className)
            val sec = Constants.sectionKey(section)
            val sectionKey = "$cls/$sec"
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val rows = firestoreService.queryDocuments(Constants.Firestore.HOMEWORK) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("dueDate", todayIso)
                    .whereEqualTo("status", "active")
            }
            rows.size()
        } catch (_: Exception) {
            0
        }
    }
}
