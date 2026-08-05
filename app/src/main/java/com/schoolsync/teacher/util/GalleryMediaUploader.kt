package com.schoolsync.teacher.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Uploads gallery media (image or video) to Firebase Cloud Storage and
 * returns the public download URL. Mirrors the StoryMediaUploader pattern
 * for the gallery domain.
 *
 * Storage path: `galleryMedia/{schoolId}/{albumId}/{epochMillis}.{ext}`
 *
 * Constraints (enforced by [validate]):
 *   - Image: max 3 MB, content-type starts with "image/"
 *   - Video: max 25 MB, content-type starts with "video/"
 */
object GalleryMediaUploader {

    private const val MAX_IMAGE_BYTES = 3L * 1024 * 1024
    private const val MAX_VIDEO_BYTES = 25L * 1024 * 1024

    /**
     * Result of a successful Storage upload. [storagePath] is retained so the
     * caller can [deleteByPath] to roll back an orphan when the follow-up
     * Firestore write fails.
     */
    data class UploadResult(val downloadUrl: String, val storagePath: String)

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
        // Videos are size-checked AFTER compression (see validateSize) — checking
        // the raw pick here rejected essentially every real phone clip, since
        // 25 MB is only a few seconds of 1080p60.
        if (declaredType == "image") return validateSize(context, uri, declaredType)
        return null
    }

    /**
     * Size-cap check, split out of [validate] so video can be measured after
     * transcoding rather than at pick time.
     *
     * Fails CLOSED when the size cannot be read: previously an unknown length
     * skipped the check, letting an oversized file reach Storage only to be
     * rejected by the rules as an opaque PERMISSION_DENIED, which reads to the
     * user as "video upload is broken" rather than "your video is too big".
     */
    fun validateSize(context: Context, uri: Uri, declaredType: String): String? {
        val cap = if (declaredType == "image") MAX_IMAGE_BYTES else MAX_VIDEO_BYTES
        val capMb = cap / (1024 * 1024)
        val size: Long? = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use { it.length.takeIf { len -> len > 0 } }
        } catch (_: Exception) { null }
        if (size == null) {
            return "Couldn't read the file size. Please pick the ${declaredType} again."
        }
        if (size > cap) {
            val actualMb = size / (1024 * 1024)
            return "${declaredType.replaceFirstChar { it.uppercase() }} is ${actualMb} MB — " +
                "the limit is $capMb MB. Please trim it and try again."
        }
        return null
    }

    /**
     * Suspending upload that returns the download URL plus the storage path
     * (for rollback). Throws on failure.
     */
    suspend fun uploadSuspending(
        context: Context,
        uri: Uri,
        schoolId: String,
        albumId: String,
        declaredType: String,   // "image" | "video"
        /**
         * The bytes to actually upload. Defaults to [uri]; callers pass the
         * transcoded file for video. MIME/extension are still derived from
         * [uri] because a compressed output is a file:// URI, for which
         * ContentResolver.getType() returns null.
         */
        sourceUri: Uri = uri
    ): UploadResult {
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
        // Set contentType EXPLICITLY. putFile() without metadata falls back to
        // ContentResolver.getType(uri), which returns null for file:// URIs — so
        // any cached/compressed file path silently becomes application/octet-stream.
        // Chrome's ORB then blocks the object, which is exactly why the admin
        // panel's Firebase library sniffs MIME server-side (Firebase.php:453).
        // Explicit metadata is also the precondition for adding a contentType
        // gate to the galleryMedia storage rule (storage.rules:130).
        val contentType = mime.ifBlank { mimeForExtension(ext, declaredType) }
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        ref.putFile(sourceUri, metadata).await()
        return UploadResult(ref.downloadUrl.await().toString(), path)
    }

    /** Poster frame + duration for an uploaded video. */
    data class PosterResult(val thumbnailUrl: String, val duration: String, val storagePath: String?)

    /**
     * Generate a poster frame (~1s in) + duration label for a video and upload
     * the poster to Storage. Writing `thumbnail`/`duration` into the galleryMedia
     * doc is a CROSS-SYSTEM contract — without it the Parent app and admin gallery
     * show a blank tile for every teacher-uploaded video. Best-effort: returns
     * whatever it could produce (poster may be blank if extraction fails), never
     * throws, so a poster hiccup can't fail the upload.
     */
    suspend fun uploadVideoPoster(
        context: Context,
        uri: Uri,
        schoolId: String,
        albumId: String
    ): PosterResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val durationLabel = formatDuration(durationMs)

            val frame: Bitmap? = retriever.getFrameAtTime(
                1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime
            if (frame == null) return PosterResult("", durationLabel, null)

            val baos = ByteArrayOutputStream()
            frame.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val bytes = baos.toByteArray()

            val path = "galleryMedia/${schoolId}/${albumId}/thumb_${System.currentTimeMillis()}.jpg"
            val ref = FirebaseStorage.getInstance().reference.child(path)
            // putBytes has no Uri to infer from, so WITHOUT explicit metadata this
            // poster was ALWAYS stored as application/octet-stream — and every
            // teacher-uploaded video's poster rendered broken in the web gallery.
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            ref.putBytes(bytes, metadata).await()
            val url = ref.downloadUrl.await().toString()
            return PosterResult(url, durationLabel, path)
        } catch (e: Exception) {
            // Was swallowed entirely — a poster failure left a blank tile with no
            // trace. Still best-effort (never fails the upload), but now visible.
            android.util.Log.w("GalleryMediaUploader", "Video poster generation failed", e)
            return PosterResult("", "", null)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Fallback contentType when ContentResolver gave us nothing (file:// URIs
     * always do). Derived from the extension we already resolved, so the stored
     * object's Content-Type always agrees with its file extension.
     */
    private fun mimeForExtension(ext: String, declaredType: String): String = when (ext) {
        "jpg"  -> "image/jpeg"
        "png"  -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "mp4"  -> "video/mp4"
        "mov"  -> "video/quicktime"
        "3gp"  -> "video/3gpp"
        else   -> if (declaredType == "image") "image/jpeg" else "video/mp4"
    }

    /** milliseconds → "m:ss" (e.g. 95000 → "1:35"). Blank when unknown. */
    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return ""
        val totalSec = (ms / 1000L).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
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
