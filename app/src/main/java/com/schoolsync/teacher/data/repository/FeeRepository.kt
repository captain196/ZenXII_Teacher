package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.model.ClassFeeOverview
import com.schoolsync.teacher.data.model.FeeDefaulter
import com.schoolsync.teacher.data.model.StudentFeeStatus
import com.schoolsync.teacher.data.repository.firestore.FeeFirestoreRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fee repository used by [FeesTeacherViewModel].
 *
 * Firestore-only (per project policy — zero RTDB). Delegates to
 * [FeeFirestoreRepository] for raw doc reads and [StudentRepository] to enrich
 * rows with roster fields (rollNo, fatherName, phone) that live on the
 * `students` collection rather than `feeDefaulters`.
 *
 * Teachers see a read-only view of fee status for their assigned classes.
 * Admin writes to `feeDefaulters` for every student after each payment /
 * discount / scholarship event, so this collection is authoritative.
 */
@Singleton
class FeeRepository @Inject constructor(
    private val feeFirestoreRepository: FeeFirestoreRepository,
    private val studentRepository: StudentRepository
) {

    /**
     * Aggregated fee overview for one class/section.
     */
    suspend fun getClassFeeOverview(
        className: String,
        section: String
    ): Result<ClassFeeOverview> {
        return try {
            val defaulters = feeFirestoreRepository
                .getClassDefaulters(className, section)
                .getOrElse { return Result.failure(it) }

            // Total roster size (from students collection) is a stronger
            // denominator than counting defaulter docs, because paid students
            // may or may not have a feeDefaulters entry depending on sync history.
            val roster = studentRepository.getStudentsForClass(className, section)
                .getOrElse { emptyList() }
            val totalStudents = maxOf(roster.size, defaulters.size)

            var defaulterCount = 0
            var totalPending = 0.0
            var examBlockedCount = 0
            var resultWithheldCount = 0

            for (d in defaulters) {
                if (d.totalDues > 0) {
                    defaulterCount++
                    totalPending += d.totalDues
                }
                if (d.examBlocked) examBlockedCount++
                if (d.resultWithheld) resultWithheldCount++
            }

            val paidCount = (totalStudents - defaulterCount).coerceAtLeast(0)

            // Sum collected from receipts for the class/section in this session.
            // Non-fatal: if the receipts query fails, fall back to 0 rather
            // than failing the whole overview.
            val totalCollected = feeFirestoreRepository
                .getClassReceipts(className, section)
                .getOrNull()
                ?.sumOf { it.amount }
                ?: 0.0

            Result.success(
                ClassFeeOverview(
                    className = className,
                    section = section,
                    totalStudents = totalStudents,
                    paidCount = paidCount,
                    defaulterCount = defaulterCount,
                    totalCollected = totalCollected,
                    totalPending = totalPending,
                    examBlockedCount = examBlockedCount,
                    resultWithheldCount = resultWithheldCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Per-student fee status for the given class/section.
     * Merges the full roster (so paid students appear too) with defaulter
     * doc fields (dues, flags, lastPaymentDate).
     */
    suspend fun getStudentFeeStatuses(
        className: String,
        section: String
    ): Result<List<StudentFeeStatus>> {
        return try {
            val defaultersList = feeFirestoreRepository
                .getClassDefaulters(className, section)
                .getOrElse { return Result.failure(it) }
            val defaultersById = defaultersList.associateBy { it.studentId }

            val roster = studentRepository.getStudentsForClass(className, section)
                .getOrElse { emptyList() }

            // Primary source = roster (so paid students with no feeDefaulters
            // doc still show up). For each, look up the defaulter row to
            // enrich fee fields. Students with no defaulter doc are treated
            // as paid (totalPending=0, no flags).
            val statuses = roster.map { student ->
                val d = defaultersById[student.studentId]
                StudentFeeStatus(
                    studentId       = student.studentId,
                    studentName     = student.name.ifBlank { d?.studentName ?: "" },
                    rollNo          = student.rollNo,
                    totalPending    = d?.totalDues ?: 0.0,
                    isDefaulter     = (d?.totalDues ?: 0.0) > 0,
                    examBlocked     = d?.examBlocked ?: false,
                    resultWithheld  = d?.resultWithheld ?: false,
                    lastPaymentDate = d?.lastPaymentDate ?: "",
                    fatherName      = student.fatherName,
                    phone           = student.phone
                )
            }.toMutableList()

            // Fold in any defaulter doc whose student is missing from the roster
            // (e.g. a withdrawn student still owing dues — the UI may still
            // want to see them). Fetched without enrichment.
            val rosterIds = roster.map { it.studentId }.toSet()
            for (d in defaultersList) {
                if (d.studentId in rosterIds) continue
                statuses.add(
                    StudentFeeStatus(
                        studentId       = d.studentId,
                        studentName     = d.studentName,
                        rollNo          = "",
                        totalPending    = d.totalDues,
                        isDefaulter     = d.totalDues > 0,
                        examBlocked     = d.examBlocked,
                        resultWithheld  = d.resultWithheld,
                        lastPaymentDate = d.lastPaymentDate,
                        fatherName      = "",
                        phone           = ""
                    )
                )
            }

            Result.success(
                statuses.sortedBy { it.rollNo.toIntOrNull() ?: Int.MAX_VALUE }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Defaulters-only list (students with totalDues > 0), sorted by dues desc.
     */
    suspend fun getDefaulterList(
        className: String,
        section: String
    ): Result<List<FeeDefaulter>> {
        return try {
            val defaulters = feeFirestoreRepository
                .getClassDefaulters(className, section)
                .getOrElse { return Result.failure(it) }

            val roster = studentRepository.getStudentsForClass(className, section)
                .getOrElse { emptyList() }
            val rosterById = roster.associateBy { it.studentId }

            val result = defaulters
                .filter { it.totalDues > 0 }
                .map { d ->
                    val student = rosterById[d.studentId]
                    FeeDefaulter(
                        studentId      = d.studentId,
                        studentName    = (student?.name ?: "").ifBlank { d.studentName },
                        rollNo         = student?.rollNo ?: "",
                        totalDues      = d.totalDues,
                        unpaidMonths   = d.unpaidMonths,
                        examBlocked    = d.examBlocked,
                        resultWithheld = d.resultWithheld,
                        fatherName     = student?.fatherName ?: "",
                        phone          = student?.phone ?: ""
                    )
                }
                .sortedByDescending { it.totalDues }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
