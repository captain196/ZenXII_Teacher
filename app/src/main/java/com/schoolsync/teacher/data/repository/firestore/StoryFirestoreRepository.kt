package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for stories from Firestore (teacher-side, read + write).
 * Collection: `stories`
 */
@Singleton
class StoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all active (non-expired) stories for the school.
     */
    suspend fun getActiveStories(): Result<List<StoryDoc>> {
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val now = System.currentTimeMillis()
            val stories = firestoreService.queryDocumentsAs<StoryDoc>(
                "stories"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereGreaterThan("expiresAt", now)
                    .orderBy("expiresAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get stories uploaded by this teacher.
     */
    suspend fun getMyStories(): Result<List<StoryDoc>> {
        val teacherId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val stories = firestoreService.queryDocumentsAs<StoryDoc>(
                "stories"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("teacherId", teacherId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload a new story (expires after 24 hours).
     */
    suspend fun uploadStory(
        mediaUrl: String,
        type: String = "image",
        caption: String = "",
        teacherName: String,
        teacherPic: String = ""
    ): Result<String> {
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = tokenManager.userId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))

        val storyId = "${schoolCode}_${teacherId}_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "schoolId" to schoolCode,
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "teacherPic" to teacherPic,
            "mediaUrl" to mediaUrl,
            "type" to type,
            "caption" to caption,
            "createdAt" to firestoreService.serverTimestamp(),
            "expiresAt" to (now + 86_400_000L),  // 24 hours
            "viewCount" to 0
        )

        return try {
            firestoreService.setDocument("stories", storyId, data)
            Result.success(storyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a story.
     */
    suspend fun deleteStory(storyId: String): Result<Unit> {
        return try {
            firestoreService.deleteDocument("stories", storyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
