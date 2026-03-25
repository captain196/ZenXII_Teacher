package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.GalleryAlbumDoc
import com.schoolsync.teacher.data.model.firestore.GalleryMediaDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for gallery data from Firestore.
 * Collections: `galleryAlbums`, `galleryMedia`
 */
@Singleton
class GalleryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    suspend fun getAlbums(): Result<List<GalleryAlbumDoc>> {
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val albums = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "active")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlbumMedia(albumId: String): Result<List<GalleryMediaDoc>> {
        return try {
            val media = firestoreService.queryDocumentsAs<GalleryMediaDoc>(
                "galleryMedia"
            ) { ref ->
                ref.whereEqualTo("albumId", albumId)
                    .orderBy("uploadedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(media)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAlbum(
        title: String,
        description: String = "",
        eventId: String = "",
        createdBy: String
    ): Result<String> {
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        val albumId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = hashMapOf(
            "schoolId" to schoolCode,
            "title" to title,
            "description" to description,
            "coverUrl" to "",
            "mediaCount" to 0,
            "eventId" to eventId,
            "createdBy" to createdBy,
            "status" to "active",
            "createdAt" to firestoreService.serverTimestamp()
        )

        return try {
            firestoreService.setDocument("galleryAlbums", albumId, data)
            Result.success(albumId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMedia(
        albumId: String,
        url: String,
        type: String = "image",
        caption: String = "",
        uploadedBy: String
    ): Result<String> {
        val schoolCode = tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        val mediaId = "${albumId}_${System.currentTimeMillis()}"
        val data = hashMapOf(
            "schoolId" to schoolCode,
            "albumId" to albumId,
            "url" to url,
            "type" to type,
            "caption" to caption,
            "uploadedBy" to uploadedBy,
            "uploadedAt" to firestoreService.serverTimestamp()
        )

        return try {
            firestoreService.setDocument("galleryMedia", mediaId, data)
            Result.success(mediaId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
