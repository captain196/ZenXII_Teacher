package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.SectionDoc
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading section data from Firestore.
 * Collection: sections
 *
 * Document IDs follow the pattern: {schoolCode}_{session}_{class}_{section}
 */
@Singleton
class SectionFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch a specific section document.
     * Document ID: {schoolCode}_{session}_{className}_{section}
     */
    suspend fun getSection(className: String, section: String): Result<SectionDoc> {
        val docId = buildSectionDocId(className, section)
            ?: return Result.failure(Exception("School code or session not available"))

        return try {
            val doc = firestoreService.getDocumentAs<SectionDoc>(
                Constants.Firestore.SECTIONS,
                docId
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("Section not found: $docId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all sections belonging to the teacher's school for the current session.
     */
    suspend fun getSectionsBySchool(): Result<List<SectionDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val sections = firestoreService.queryDocumentsAs<SectionDoc>(
                Constants.Firestore.SECTIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
            }
            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all sections for a specific class within the teacher's school.
     */
    suspend fun getSectionsByClass(className: String): Result<List<SectionDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val sections = firestoreService.queryDocumentsAs<SectionDoc>(
                Constants.Firestore.SECTIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", className)
            }
            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a specific section document for real-time updates.
     * Emits `null` when the document does not exist or required identifiers are unavailable.
     */
    fun observeSection(className: String, section: String): Flow<SectionDoc?> {
        return tokenManager.schoolCode
            .map { code -> code?.takeIf { it.isNotBlank() } }
            .map { schoolCode ->
                if (schoolCode == null) return@map null
                val session = getSession() ?: return@map null
                val docId = "${schoolCode}_${session}_${className}_${section}"
                try {
                    firestoreService.getDocumentAs<SectionDoc>(
                        Constants.Firestore.SECTIONS,
                        docId
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Builds the section document ID: {schoolCode}_{session}_{className}_{section}
     */
    private suspend fun buildSectionDocId(className: String, section: String): String? {
        val schoolCode = getSchoolCode() ?: return null
        val session = getSession() ?: return null
        return "${schoolCode}_${session}_${className}_${section}"
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
