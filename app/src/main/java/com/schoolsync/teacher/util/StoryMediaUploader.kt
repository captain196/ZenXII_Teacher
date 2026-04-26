package com.schoolsync.teacher.util

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

/**
 * Uploads story media (image or video) to Firebase Cloud Storage and
 * returns the public download URL.
 *
 * Storage path: `stories/{schoolId}/{teacherId}/{epochMillis}.{ext}`
 *   - epochMillis avoids collisions across rapid uploads
 *   - extension preserved from the source file so MIME-sniffing on
 *     download produces the right Content-Type
 *
 * Constraints (enforced by [validate]):
 *   - Image: max 10 MB, content-type starts with "image/"
 *   - Video: max 50 MB, content-type starts with "video/"
 *
 * Progress is exposed as a hot Flow<UploadProgress> — collect to drive
 * a progress bar in the UI. The flow completes with [UploadProgress.Done]
 * on success or [UploadProgress.Failed] on error.
 */
object StoryMediaUploader {

    sealed class UploadProgress {
        data class InProgress(val percent: Int) : UploadProgress()
        /**
         * Upload succeeded.
         *
         * @param downloadUrl public Firebase Storage download URL (write
         *                    this into the Firestore doc)
         * @param storagePath full Storage object path (e.g.
         *                    "stories/SCH_X/T0001/1713620938421.jpg") —
         *                    pass to [deleteByPath] to roll back if the
         *                    Firestore write later fails.
         */
        data class Done(val downloadUrl: String, val storagePath: String) : UploadProgress()
        data class Failed(val reason: String) : UploadProgress()
    }

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
        // Best-effort size check — content URIs aren't required to expose
        // length, so a null/-1 size means "unknown, skip the check."
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
     * Begin upload. Emits [UploadProgress] events; flow terminates when
     * the upload reaches a final state (Done or Failed).
     */
    fun upload(
        context: Context,
        uri: Uri,
        schoolId: String,
        teacherId: String,
        declaredType: String   // "image" | "video"
    ): Flow<UploadProgress> = callbackFlow {
        try {
            // Resolve extension from MIME type so we don't have to parse
            // the file name (content URIs often don't include one).
            val mime = context.contentResolver.getType(uri).orEmpty()
            val ext = when {
                mime.endsWith("/jpeg") || mime.endsWith("/jpg") -> "jpg"
                mime.endsWith("/png")                          -> "png"
                mime.endsWith("/webp")                         -> "webp"
                mime.endsWith("/mp4")                          -> "mp4"
                mime.endsWith("/quicktime")                    -> "mov"
                mime.endsWith("/3gpp")                         -> "3gp"
                else -> if (declaredType == "image") "jpg" else "mp4"
            }

            val path = "stories/${schoolId}/${teacherId}/${System.currentTimeMillis()}.${ext}"
            val ref = FirebaseStorage.getInstance().reference.child(path)

            val task = ref.putFile(uri)
            task.addOnProgressListener { snap ->
                val pct = if (snap.totalByteCount > 0) {
                    ((snap.bytesTransferred * 100) / snap.totalByteCount).toInt().coerceIn(0, 99)
                } else 0
                trySend(UploadProgress.InProgress(pct))
            }
            task.addOnFailureListener { e ->
                trySend(UploadProgress.Failed(e.message ?: "Upload failed"))
                close()
            }
            task.addOnSuccessListener {
                // Resolve the download URL inside another coroutine since
                // getDownloadUrl returns a Task as well.
                ref.downloadUrl
                    .addOnSuccessListener { url ->
                        trySend(UploadProgress.Done(url.toString(), path))
                        close()
                    }
                    .addOnFailureListener { e ->
                        trySend(UploadProgress.Failed(e.message ?: "Failed to read download URL"))
                        close()
                    }
            }
            awaitClose { /* upload runs to completion regardless */ }
        } catch (e: Exception) {
            trySend(UploadProgress.Failed(e.message ?: "Upload setup failed"))
            close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Best-effort rollback — delete a previously-uploaded Storage
     * object. Returns true on success; swallows failures (the orphan
     * cleanup Cloud Function will sweep it eventually).
     *
     * Use this from the VM when the Storage upload succeeded but
     * the Firestore write later failed, so the bucket doesn't bloat
     * with orphaned media.
     */
    suspend fun deleteByPath(storagePath: String): Boolean = try {
        FirebaseStorage.getInstance().reference.child(storagePath).delete().await()
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Convenience: suspend wrapper that uploads and returns the URL,
     * throwing on failure. Use when you don't need progress events.
     */
    suspend fun uploadSuspending(
        context: Context,
        uri: Uri,
        schoolId: String,
        teacherId: String,
        declaredType: String
    ): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.endsWith("/jpeg") || mime.endsWith("/jpg") -> "jpg"
            mime.endsWith("/png")                          -> "png"
            mime.endsWith("/webp")                         -> "webp"
            mime.endsWith("/mp4")                          -> "mp4"
            mime.endsWith("/quicktime")                    -> "mov"
            mime.endsWith("/3gpp")                         -> "3gp"
            else -> if (declaredType == "image") "jpg" else "mp4"
        }
        val path = "stories/${schoolId}/${teacherId}/${System.currentTimeMillis()}.${ext}"
        val ref = FirebaseStorage.getInstance().reference.child(path)
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }
}
