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
     * write `dueDate` as end-of-day IST ISO `"yyyy-MM-ddT23:59:59+05:30"` (via
     * normalizeDueDate). The previous query did an exact `whereEqualTo("dueDate",
     * "yyyy-MM-dd")`, which could never match that shape — so the dashboard's
     * "HW due today" count was always 0. We now fetch the section's active
     * homework and count those whose dueDate's IST date-part equals today.
     * This avoids needing a new composite index (range query on dueDate) and is
     * efficient: a single section's active homework set is small. Both the ISO
     * end-of-day form and any legacy date-only `"yyyy-MM-dd"` doc are matched
     * since both begin with the same 10-char IST date prefix.
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
            // Today's calendar date in IST — matches the date prefix the
            // writer pins via normalizeDueDate ("...T23:59:59+05:30").
            val istFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            }
            val todayIso = istFmt.format(Date())
            // Session scoping — mirror the homework list (HomeworkFirestoreRepository
            // getSession/getHomework). Without this, a stale prior-session homework
            // that happens to be dated today would inflate the count. Blank/absent
            // session ⇒ null ⇒ don't filter (no behavioural change for callers
            // without a seeded session). Applied client-side so no new composite
            // index is needed, consistent with the date-prefix matching below.
            val session = tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }

            val rows = firestoreService.queryDocuments(Constants.Firestore.HOMEWORK) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("status", "active")
            }
            rows.documents.count { doc ->
                val due = doc.getString("dueDate").orEmpty()
                val sessionOk = session == null || doc.getString("session") == session
                // First 10 chars = the IST calendar date the homework is due.
                sessionOk && due.startsWith(todayIso)
            }
        } catch (_: Exception) {
            0
        }
    }
}
