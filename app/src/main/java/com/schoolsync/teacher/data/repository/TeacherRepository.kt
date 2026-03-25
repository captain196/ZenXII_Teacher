package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.DayTimetable
import com.schoolsync.teacher.data.model.TimetableEntry
import com.schoolsync.teacher.data.model.User
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Teacher profile, assigned classes, and timetable operations.
 */
@Singleton
class TeacherRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    /**
     * Fetch teacher profile from Firebase.
     * Path: Users/Teachers/{schoolCode}/{teacherId}/
     */
    suspend fun getTeacherProfile(): Result<User> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.TEACHERS}/$schoolCode/$teacherId"
            val user = firebaseService.readValue<User>(path)
                ?: return Result.failure(Exception("Teacher profile not found"))

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listen to teacher profile changes in real-time.
     */
    fun observeTeacherProfile(): Flow<User?> {
        return kotlinx.coroutines.flow.flow {
            val schoolCode = tokenManager.schoolCode.firstOrNull() ?: return@flow
            val teacherId = tokenManager.userId.firstOrNull() ?: return@flow
            val path = "${Constants.Firebase.TEACHERS}/$schoolCode/$teacherId"

            firebaseService.listen(path).collect { snapshot ->
                emit(snapshot.getValue(User::class.java))
            }
        }
    }

    /**
     * Fetch classes assigned to this teacher.
     * Path: Schools/{schoolCode}/{session}/Academic/Subject_Assignments/
     * Filter by teacherId to get this teacher's assignments.
     */
    suspend fun getAssignedClasses(): Result<List<ClassAssignment>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/${Constants.Firebase.SUBJECT_ASSIGNMENTS}"
            val snapshot = firebaseService.readSnapshot(path)

            val assignments = mutableListOf<ClassAssignment>()
            for (child in snapshot.children) {
                val assignment = child.getValue(ClassAssignment::class.java)
                if (assignment != null && assignment.teacherId == teacherId) {
                    assignments.add(assignment.copy(assignmentId = child.key ?: ""))
                }
            }

            Result.success(assignments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listen to assigned classes in real-time.
     */
    fun observeAssignedClasses(): Flow<List<ClassAssignment>> {
        return kotlinx.coroutines.flow.flow {
            val schoolCode = tokenManager.schoolCode.firstOrNull() ?: return@flow
            val session = tokenManager.session.firstOrNull() ?: return@flow
            val teacherId = tokenManager.userId.firstOrNull() ?: return@flow

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/${Constants.Firebase.SUBJECT_ASSIGNMENTS}"
            firebaseService.listen(path).collect { snapshot ->
                val assignments = mutableListOf<ClassAssignment>()
                for (child in snapshot.children) {
                    val assignment = child.getValue(ClassAssignment::class.java)
                    if (assignment != null && assignment.teacherId == teacherId) {
                        assignments.add(assignment.copy(assignmentId = child.key ?: ""))
                    }
                }
                emit(assignments)
            }
        }
    }

    /**
     * Fetch timetable for a specific class/section.
     * Path: Schools/{schoolCode}/{session}/{class}/{section}/Time_table/
     *
     * Returns a list of DayTimetable grouped by day.
     */
    suspend fun getTimetable(
        className: String,
        section: String
    ): Result<List<DayTimetable>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val session = tokenManager.session.firstOrNull()
                ?: return Result.failure(Exception("Session not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/$session/${Constants.classKey(className)}/${Constants.sectionKey(section)}/${Constants.Firebase.TIMETABLE}"
            val snapshot = firebaseService.readSnapshot(path)

            val dayTimetables = mutableListOf<DayTimetable>()
            for (daySnapshot in snapshot.children) {
                val day = daySnapshot.key ?: continue
                val periods = mutableListOf<TimetableEntry>()

                for (periodSnapshot in daySnapshot.children) {
                    val entry = periodSnapshot.getValue(TimetableEntry::class.java)
                    if (entry != null) {
                        periods.add(
                            entry.copy(
                                day = day,
                                periodNumber = periodSnapshot.key?.toIntOrNull() ?: 0,
                                className = className,
                                section = section
                            )
                        )
                    }
                }

                periods.sortBy { it.periodNumber }
                dayTimetables.add(DayTimetable(day = day, periods = periods))
            }

            Result.success(dayTimetables)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get this teacher's timetable across all assigned classes.
     * Filters all class timetables for periods where this teacher is assigned.
     */
    suspend fun getMyTimetable(): Result<List<DayTimetable>> {
        return try {
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val assignmentsResult = getAssignedClasses()
            val assignments = assignmentsResult.getOrNull()
                ?: return Result.failure(
                    assignmentsResult.exceptionOrNull() ?: Exception("Failed to get assignments")
                )

            // Get unique class/section combinations
            val classSections = assignments.map { it.className to it.section }.distinct()

            val allPeriods = mutableListOf<TimetableEntry>()
            for ((className, section) in classSections) {
                val timetableResult = getTimetable(className, section)
                val dayTimetables = timetableResult.getOrNull() ?: continue

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
