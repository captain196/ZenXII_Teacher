package com.schoolsync.teacher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.schoolsync.teacher.data.model.firestore.StorySharedConfig
import java.io.File
import java.io.FileOutputStream
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

    // Raw-pick caps (PRE-compression). Images are downscaled/re-encoded
    // client-side before upload (see [compressImage]) the way Instagram/
    // WhatsApp do, so this cap is generous — it only rejects absurd inputs
    // at pick time; anything reasonable is shrunk before it leaves the
    // device. The AUTHORITATIVE post-compression cap is
    // StorySharedConfig.MAX_IMAGE_BYTES (10 MB) — enforced in [upload].
    private const val RAW_MAX_IMAGE_BYTES = 40L * 1024 * 1024
    // Generous raw cap: videos are transcoded to ~720p/2Mbps client-side
    // (see StoryVideoCompressor) before upload, so this only rejects absurd
    // inputs at pick time. The AUTHORITATIVE post-compression cap is
    // StorySharedConfig.MAX_VIDEO_BYTES (50 MB) — enforced in [upload].
    private const val RAW_MAX_VIDEO_BYTES = 300L * 1024 * 1024

    /** Longest-edge cap for uploaded images (Instagram-story-ish 9:16 @1080
     *  fits comfortably; 1920 keeps landscape/quality without bloating). */
    private const val MAX_IMAGE_DIMEN = 1920
    private const val JPEG_QUALITY = 82

    /** Best-effort byte size of a content/file Uri; -1 when unknown. */
    private fun fileSizeBytes(context: Context, uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?.use { it.length.takeIf { len -> len > 0 } } ?: -1L
    } catch (_: Exception) { -1L }

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
            val cap = if (declaredType == "image") RAW_MAX_IMAGE_BYTES else RAW_MAX_VIDEO_BYTES
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

            // Shrink images on-device before upload (Instagram/WhatsApp do
            // the same). Falls back to the original Uri on any failure.
            val uploadUri = if (declaredType == "image") compressImage(context, uri) else uri
            // Re-derive extension: a compressed image is always JPEG.
            val finalExt = if (declaredType == "image") "jpg" else ext

            // Post-compression size guard (M8). The AUTHORITATIVE cap is the
            // storage-rule limit mirrored in StorySharedConfig (10 MB image /
            // 50 MB video). Compression usually lands well under it, but a
            // long clip — or a compressor/image fallback to the original —
            // can still exceed it. Reject here with a clear message instead
            // of letting the Storage rule reject the PUT with a generic
            // "you don't have permission" error. Unknown size (-1) fails open;
            // the Storage rule remains the server-side backstop.
            val finalBytes = fileSizeBytes(context, uploadUri)
            val postCap = if (declaredType == "image")
                StorySharedConfig.MAX_IMAGE_BYTES else StorySharedConfig.MAX_VIDEO_BYTES
            if (finalBytes in 1..Long.MAX_VALUE && finalBytes > postCap) {
                val capMb = postCap / (1024 * 1024)
                val noun = if (declaredType == "image") "Image" else "Video"
                trySend(UploadProgress.Failed("$noun too large after compression, max $capMb MB."))
                close()
                return@callbackFlow
            }

            val path = "stories/${schoolId}/${teacherId}/${System.currentTimeMillis()}.${finalExt}"
            val ref = FirebaseStorage.getInstance().reference.child(path)

            val task = ref.putFile(uploadUri)
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
     * Downscale + re-encode an image to a JPEG no larger than
     * [MAX_IMAGE_DIMEN] on its longest edge, honouring EXIF rotation, and
     * write it to the cache dir. Returns a file:// Uri for the compressed
     * copy, or the original [uri] unchanged on any failure (so upload still
     * proceeds). This mirrors what Instagram/WhatsApp do client-side so a
     * 12 MP phone photo (~5–10 MB) uploads as a ~200–500 KB JPEG.
     */
    private fun compressImage(context: Context, uri: Uri): Uri {
        return try {
            val cr = context.contentResolver

            // 1. Read bounds only (no full decode) to compute a sane sample.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return uri

            // 2. Power-of-two downsample to get within ~2× of target cheaply.
            var sample = 1
            val longest = maxOf(srcW, srcH)
            while (longest / sample > MAX_IMAGE_DIMEN * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return uri

            // 3. Precise scale to the longest-edge cap.
            val scale = MAX_IMAGE_DIMEN.toFloat() / maxOf(bmp.width, bmp.height)
            if (scale < 1f) {
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                )
            }

            // 4. Honour EXIF orientation so portrait photos don't upload sideways.
            val orientation = cr.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            val rotation = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            // 5. Encode JPEG to cache and hand back a file:// Uri.
            val out = File(context.cacheDir, "story_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            Uri.fromFile(out)
        } catch (_: Exception) {
            uri
        }
    }

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
}
