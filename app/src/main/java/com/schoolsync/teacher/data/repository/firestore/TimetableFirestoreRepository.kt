package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.DayTimetable
import com.schoolsync.teacher.data.model.TimetableEntry
import com.schoolsync.teacher.data.model.firestore.TimetableDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading timetable data from Firestore (teacher-side).
 *
 * Collection: `timetables`
 * One document per day per class/section.
 * Doc ID: `{schoolId}_{session}_{sectionKey}_{day}`
 */
@Singleton
class TimetableFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the timetable for a specific class/section.
     */
    suspend fun getTimetable(className: String, section: String): Result<List<DayTimetable>> {
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val session = tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Session not available"))

        val cls = Constants.classKey(className)
        val sec = Constants.sectionKey(section)
        val sectionKey = "$cls/$sec"

        return try {
            val docs = firestoreService.queryDocumentsAs<TimetableDoc>(
                Constants.Firestore.TIMETABLES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("sectionKey", sectionKey)
            }

            val dayTimetables = docs.map { doc ->
                val periods = doc.periods.map { period ->
                    TimetableEntry(
                        day = doc.day,
                        periodNumber = period.periodNumber,
                        subject = period.subject,
                        teacher = period.teacher,
                        teacherId = period.teacherId,
                        startTime = period.startTime,
                        endTime = period.endTime,
                        room = period.room,
                        className = doc.className,
                        section = doc.section
                    )
                }.sortedBy { it.periodNumber }

                DayTimetable(day = doc.day, periods = periods)
            }

            Result.success(dayTimetables)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get this teacher's timetable across all assigned classes.
     * Fetches all timetables for assigned class/sections and filters by teacherId.
     */
    suspend fun getMyTimetable(
        classSections: List<Pair<String, String>>
    ): Result<List<DayTimetable>> {
        val teacherId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val allPeriods = mutableListOf<TimetableEntry>()

            for ((className, section) in classSections) {
                val result = getTimetable(className, section)
                val dayTimetables = result.getOrNull() ?: continue

                for (dayTimetable in dayTimetables) {
                    for (period in dayTimetable.periods) {
                        if (period.teacherId == teacherId || period.teacher == teacherId) {
                            allPeriods.add(period)
                        }
                    }
                }
            }

            // Group by day
            val grouped = allPeriods.groupBy { it.day }
            val result = grouped.map { (day, periods) ->
                DayTimetable(day = day, periods = periods.sortedBy { it.periodNumber })
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
