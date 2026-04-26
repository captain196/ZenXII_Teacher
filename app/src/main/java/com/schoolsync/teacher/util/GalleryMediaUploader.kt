package com.schoolsync.teacher.util

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Uploads gallery media (image or video) to Firebase Cloud Storage and
 * returns the public download URL. Mirrors the StoryMediaUploader pattern
 * for the gallery domain.
 *
 * Storage path: `galleryMedia/{schoolId}/{albumId}/{epochMillis}.{ext}`
 *
 * Constraints (enforced by [validate]):
 *   - Image: max 10 MB, content-type starts with "image/"
 *   - Video: max 50 MB, content-type starts with "video/"
 */
object GalleryMediaUploader {

    private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024

    /**
     * Cheap server-free pre-checks. Returns null when OK, otherwise an
     * error message suitable for surfacing in a toast / dialog.
     */
    fun validate(context: Context, uri: Uri, declaredType: String): String? {
        val cr = context.contentResolver
        val mime = cr.getType(uri).orEmpty().lowercase()
        if (declaredType == "image" && !mime.startsWith("image/")) {
            return "Selected file is not an image (got $mime)."
        }
        if (declaredType == "video" && !mime.startsWith("video/")) {
            return "Selected file is not a video (got $mime)."
        }
        val size: Long? = try {
            cr.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { len -> len > 0 } }
        } catch (_: Exception) { null }
        if (size != null) {
            val cap = if (declaredType == "image") MAX_IMAGE_BYTES else MAX_VIDEO_BYTES
            if (size > cap) {
                val capMb = cap / (1024 * 1024)
                return "${declaredType.replaceFirstChar { it.uppercase() }} too large (max $capMb MB)."
            }
        }
        return null
    }

    /**
     * Suspending upload that returns the download URL. Throws on failure.
     */
    suspend fun uploadSuspending(
        context: Context,
        uri: Uri,
        schoolId: String,
        albumId: String,
        declaredType: String   // "image" | "video"
    ): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.endsWith("/jpeg") || mime.endsWith("/jpg") -> "jpg"
            mime.endsWith("/png")                          -> "png"
            mime.endsWith("/webp")                         -> "webp"
            mime.endsWith("/heic") || mime.endsWith("/heif") -> "heic"
            mime.endsWith("/mp4")                          -> "mp4"
            mime.endsWith("/quicktime")                    -> "mov"
            mime.endsWith("/3gpp")                         -> "3gp"
            else -> if (declaredType == "image") "jpg" else "mp4"
        }
        val path = "galleryMedia/${schoolId}/${albumId}/${System.currentTimeMillis()}.${ext}"
        val ref = FirebaseStorage.getInstance().reference.child(path)
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Best-effort rollback for orphan cleanup on Firestore-write failure.
     */
    suspend fun deleteByPath(storagePath: String): Boolean = try {
        FirebaseStorage.getInstance().reference.child(storagePath).delete().await()
        true
    } catch (_: Exception) {
        false
    }
}
