package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-backed Homework repository for teacher-side dashboard queries.
 * Used by [DashboardViewModel] to count homework due today.
 */
@Singleton
class HomeworkTeacherRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Count the number of homework assignments due today for a given class/section.
     *
     * @param className Class name (e.g. "8th" or "Class 8th")
     * @param section Section name (e.g. "A" or "Section A")
     * @param todayDate Today's date in the format stored in RTDB (e.g. "25 Mar 2026")
     * @return count of homework items due today
     */
    suspend fun getHomeworkDueTodayCount(
        className: String,
        section: String,
        todayDate: String
    ): Int {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull() ?: return 0
            val session = tokenManager.session.firstOrNull() ?: return 0

            val cls = Constants.classKey(className)
            val sec = Constants.sectionKey(section)

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/$cls/$sec/${Constants.Firebase.HOMEWORK}"
            val snapshot = firebaseService.readSnapshot(path)

            var count = 0
            for (child in snapshot.children) {
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                val dueDate = data["dueDate"]?.toString()
                    ?: data["due_date"]?.toString() ?: ""
                val status = data["status"]?.toString() ?: "active"
                if (dueDate == todayDate && status == "active") {
                    count++
                }
            }

            count
        } catch (_: Exception) {
            0
        }
    }
}
