package com.schoolsync.teacher.data.repository.firestore

import android.util.Log
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
            // Query by schoolId + sectionKey (schoolId required by security rules)
            // Filter session client-side to avoid 3-field composite index
            val allDocs = firestoreService.queryDocumentsAs<TimetableDoc>(
                Constants.Firestore.TIMETABLES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
            }
            val docs = allDocs.filter { it.session == session }

            val dayTimetables = docs.map { doc ->
                val periods = doc.periods.map { period ->
                    val typeLower = period.type.trim().lowercase()
                    val isBreakPeriod = typeLower == "break" || typeLower == "lunch" ||
                        period.subject.equals("Lunch", ignoreCase = true) ||
                        period.subject.equals("Break", ignoreCase = true)
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
                        section = doc.section,
                        type = period.type.ifBlank { "class" },
                        isBreak = isBreakPeriod
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
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val allPeriods = mutableListOf<TimetableEntry>()
            // Collect ALL period times (not just this teacher's) for time lookup
            val allPeriodTimes = mutableMapOf<String, MutableMap<Int, Pair<String, String>>>() // day → periodNum → (start, end)
            // Dedup breaks across sections — Lunch is school-wide, one row per slot.
            val seenBreaks = mutableSetOf<String>() // "day|periodNumber"

            for ((className, section) in classSections) {
                val result = getTimetable(className, section)
                val dayTimetables = result.getOrNull() ?: continue

                for (dayTimetable in dayTimetables) {
                    val dayMap = allPeriodTimes.getOrPut(dayTimetable.day) { mutableMapOf() }
                    for (period in dayTimetable.periods) {
                        if (period.startTime.isNotBlank()) {
                            dayMap[period.periodNumber] = period.startTime to period.endTime
                        }
                        // Include this teacher's classes AND class-wide breaks/lunch
                        // (break periods are shared across all teachers of a section).
                        val mine = period.teacherId == teacherId || period.teacher == teacherId
                        if (mine) {
                            allPeriods.add(period)
                        } else if (period.isBreak) {
                            val k = "${period.day}|${period.periodNumber}"
                            if (seenBreaks.add(k)) allPeriods.add(period)
                        }
                    }
                }
            }

            // Overlay substitute data: add periods where I'm the substitute,
            // mark periods where someone covers for me
            try {
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val todayDay = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
                    .format(java.util.Date())

                // Stage 0 FZ-3 (2026-05-24) — added schoolId predicate to prevent
                // cross-tenant substitute leakage. Composite index (schoolId, date)
                // already deployed per firestore.indexes.json. Server-side rule
                // tightening is FZ-2-CRITICAL follow-up; for now, client-side
                // scoping closes the data-leakage path observed in app traffic.
                val subsSnapshot = firestoreService.queryDocuments("substitutes") { ref ->
                    ref.whereEqualTo("schoolId", schoolCode)
                        .whereEqualTo("date", todayStr)
                }

                for (doc in subsSnapshot.documents) {
                    val sub = doc.data ?: continue
                    val docSchool = sub["schoolId"]?.toString() ?: ""
                    if (docSchool != schoolCode) continue
                    val status = sub["status"]?.toString() ?: ""
                    if (status == "cancelled") continue

                    val absentId = sub["absent_teacher_id"]?.toString() ?: ""
                    val absentName = sub["absent_teacher_name"]?.toString() ?: ""

                    // New format: assignments[] array with per-period substitute info
                    @Suppress("UNCHECKED_CAST")
                    val assignments = sub["assignments"] as? List<Map<String, Any>>

                    if (assignments != null && assignments.isNotEmpty()) {
                        for (assign in assignments) {
                            val pn = (assign["periodNumber"] as? Number)?.toInt() ?: continue
                            val aSubId = assign["substitute_teacher_id"]?.toString() ?: ""
                            val aSubName = assign["substitute_teacher_name"]?.toString() ?: "Substitute"
                            val aSubject = assign["subject"]?.toString() ?: ""
                            val aClass = assign["className"]?.toString() ?: ""
                            val aSection = assign["section"]?.toString() ?: ""

                            if (absentId == teacherId) {
                                for (i in allPeriods.indices) {
                                    val p = allPeriods[i]
                                    if (p.day.equals(todayDay, true) && p.periodNumber == pn) {
                                        allPeriods[i] = p.copy(teacher = "Covered by $aSubName", teacherId = aSubId)
                                    }
                                }
                            }
                            if (aSubId == teacherId) {
                                val exists = allPeriods.any { it.day.equals(todayDay, true) && it.periodNumber == pn }
                                if (!exists) {
                                    val times = allPeriodTimes[todayDay]?.get(pn)
                                    allPeriods.add(TimetableEntry(
                                        day = todayDay, periodNumber = pn,
                                        subject = "$aSubject (Sub)", teacher = "Covering for $absentName",
                                        teacherId = teacherId,
                                        startTime = times?.first ?: "",
                                        endTime = times?.second ?: "",
                                        className = aClass, section = aSection
                                    ))
                                }
                            }
                        }
                    } else {
                        // Legacy flat format fallback
                        val subId = sub["substitute_teacher_id"]?.toString() ?: ""
                        val subName = sub["substitute_teacher_name"]?.toString() ?: "Substitute"
                        val subject = sub["subject"]?.toString() ?: ""
                        val classSection = sub["class_section"]?.toString() ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val periods = (sub["periods"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

                        if (absentId == teacherId) {
                            for (i in allPeriods.indices) {
                                val p = allPeriods[i]
                                if (p.day.equals(todayDay, true) && periods.contains(p.periodNumber)) {
                                    allPeriods[i] = p.copy(teacher = "Covered by $subName", teacherId = subId)
                                }
                            }
                        }
                        if (subId == teacherId) {
                            val cs = classSection.replace("'", "").trim()
                            val pClass: String
                            val pSection: String
                            if (cs.contains("Section")) {
                                pClass = cs.substringBefore("Section").trim()
                                pSection = "Section " + cs.substringAfter("Section").trim()
                            } else {
                                val lastSpace = cs.trimEnd().lastIndexOf(' ')
                                if (lastSpace > 0 && cs.length - lastSpace <= 3) {
                                    pClass = cs.substring(0, lastSpace).trim()
                                    pSection = "Section " + cs.substring(lastSpace).trim()
                                } else {
                                    pClass = cs
                                    pSection = ""
                                }
                            }
                            periods.forEach { pn ->
                                val exists = allPeriods.any { it.day.equals(todayDay, true) && it.periodNumber == pn }
                                if (!exists) {
                                    val times = allPeriodTimes[todayDay]?.get(pn)
                                    allPeriods.add(TimetableEntry(
                                        day = todayDay, periodNumber = pn,
                                        subject = "$subject (Sub)", teacher = "Covering for $absentName",
                                        teacherId = teacherId,
                                        startTime = times?.first ?: "",
                                        endTime = times?.second ?: "",
                                        className = pClass, section = pSection
                                    ))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("TT_SUB_OVERLAY", "Substitute overlay failed: ${e.message}", e) }

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
