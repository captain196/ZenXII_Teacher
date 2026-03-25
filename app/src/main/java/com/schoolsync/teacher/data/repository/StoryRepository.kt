package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.Story
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-backed Story repository used by [StoriesTeacherViewModel].
 * Reads/writes from Social/Stories and Social/StoryViews nodes.
 */
@Singleton
class StoryRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch stories uploaded by the current teacher.
     */
    suspend fun getMyStories(): Result<List<Story>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.SOCIAL_STORIES}"
            val snapshot = firebaseService.readSnapshot(path)

            val stories = mutableListOf<Story>()
            for (child in snapshot.children) {
                val storyId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                val storyTeacherId = data["teacherId"]?.toString() ?: ""
                if (storyTeacherId == teacherId) {
                    stories.add(Story.fromMap(storyId, data))
                }
            }

            Result.success(stories.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the view count for a specific story.
     */
    suspend fun getViewCount(storyId: String): Int {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull() ?: return 0
            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.SOCIAL_STORY_VIEWS}/$storyId"
            val snapshot = firebaseService.readSnapshot(path)
            snapshot.childrenCount.toInt()
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Upload a new story (expires after 24 hours).
     */
    suspend fun uploadStory(
        mediaUrl: String,
        type: String = "image",
        caption: String = ""
    ): Result<String> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull()
                ?: return Result.failure(Exception("User ID not available"))
            val teacherName = tokenManager.userName.firstOrNull() ?: ""

            val now = System.currentTimeMillis()
            val storyId = "${teacherId}_$now"

            val story = Story(
                storyId = storyId,
                mediaUrl = mediaUrl,
                type = type,
                caption = caption,
                createdAt = now,
                expiresAt = now + 86_400_000L,
                teacherName = teacherName
            )

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.SOCIAL_STORIES}/$storyId"
            firebaseService.setValue(path, story.toMap())

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
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.SOCIAL_STORIES}/$storyId"
            firebaseService.removeValue(path)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
