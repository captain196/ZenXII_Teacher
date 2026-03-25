package com.schoolsync.teacher.data.repository

import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.GalleryMedia
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-backed Gallery repository used by [GalleryTeacherViewModel].
 * Reads/writes from Gallery/Albums and Gallery/Media nodes.
 */
@Singleton
class GalleryRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all gallery albums for the school.
     */
    suspend fun getAlbums(): Result<List<GalleryAlbum>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.GALLERY_ALBUMS}"
            val snapshot = firebaseService.readSnapshot(path)

            val albums = mutableListOf<GalleryAlbum>()
            for (child in snapshot.children) {
                val albumId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                val album = GalleryAlbum.fromMap(albumId, data)
                if (album.status == "active") {
                    albums.add(album)
                }
            }

            Result.success(albums.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all media items in an album.
     */
    suspend fun getAlbumMedia(albumId: String): Result<List<GalleryMedia>> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.GALLERY_MEDIA}/$albumId"
            val snapshot = firebaseService.readSnapshot(path)

            val media = mutableListOf<GalleryMedia>()
            for (child in snapshot.children) {
                val mediaId = child.key ?: continue
                @Suppress("UNCHECKED_CAST")
                val data = child.value as? Map<String, Any?> ?: continue
                media.add(GalleryMedia.fromMap(mediaId, data))
            }

            Result.success(media.sortedByDescending { it.uploadedAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new gallery album.
     */
    suspend fun createAlbum(
        title: String,
        description: String = "",
        category: String = ""
    ): Result<String> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull() ?: ""

            val albumId = "${teacherId}_${System.currentTimeMillis()}"
            val now = System.currentTimeMillis()

            val data = mapOf(
                "title" to title,
                "description" to description,
                "category" to category,
                "coverImage" to "",
                "mediaCount" to 0,
                "status" to "active",
                "createdAt" to now,
                "createdBy" to teacherId
            )

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.GALLERY_ALBUMS}/$albumId"
            firebaseService.setValue(path, data)

            Result.success(albumId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload media to an album.
     */
    suspend fun uploadMedia(
        albumId: String,
        url: String,
        type: String = "image",
        caption: String = ""
    ): Result<String> {
        return try {
            val schoolCode = tokenManager.schoolCode.firstOrNull()
                ?: return Result.failure(Exception("School code not available"))
            val teacherId = tokenManager.userId.firstOrNull() ?: ""

            val mediaId = "${albumId}_${System.currentTimeMillis()}"
            val now = System.currentTimeMillis()

            val media = GalleryMedia(
                mediaId = mediaId,
                url = url,
                type = type,
                caption = caption,
                uploadedAt = now,
                uploadedBy = teacherId
            )

            val path = "${Constants.Firebase.SCHOOLS}/$schoolCode/${Constants.Firebase.GALLERY_MEDIA}/$albumId/$mediaId"
            firebaseService.setValue(path, media.toMap())

            Result.success(mediaId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
