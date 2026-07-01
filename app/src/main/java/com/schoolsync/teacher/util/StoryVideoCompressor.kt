package com.schoolsync.teacher.util

import android.content.Context
import android.net.Uri
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Transcodes a picked story video to a smaller, upload-friendly MP4 before
 * it leaves the device — the same thing WhatsApp/Instagram do client-side.
 *
 * Target ≈ 720p, H.264, ~2 Mbps. A ~90 MB clip typically lands at ~8–12 MB,
 * which uploads fast and stays well under any cap.
 *
 * Resilient by design: on ANY failure (codec unsupported, cancel, error) it
 * resumes with the ORIGINAL [uri] so the upload still proceeds — compression
 * is an optimisation, never a hard gate.
 */
object StoryVideoCompressor {

    /**
     * @param onProgress invoked with 0..99 transcode percent (main-safe? no —
     *        LightCompressor calls back on a background thread; the caller
     *        updates StateFlow which is thread-safe).
     * @return file:// Uri of the compressed MP4, or [uri] unchanged on failure.
     */
    suspend fun compress(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit
    ): Uri = suspendCancellableCoroutine { cont ->
        try {
            VideoCompressor.start(
                context = context.applicationContext,
                uris = listOf(uri),
                isStreamable = true,
                storageConfiguration = AppSpecificStorageConfiguration(subFolderName = "stories"),
                configureWith = Configuration(
                    videoNames = listOf("story_${System.currentTimeMillis()}.mp4"),
                    quality = VideoQuality.MEDIUM,
                    isMinBitrateCheckEnabled = true,   // skip already-small clips
                    videoBitrateInMbps = 2,
                    disableAudio = false,
                    resizer = null                     // auto (keeps aspect ratio)
                ),
                listener = object : CompressionListener {
                    override fun onStart(index: Int) { onProgress(0) }
                    override fun onProgress(index: Int, percent: Float) {
                        onProgress(percent.toInt().coerceIn(0, 99))
                    }
                    override fun onSuccess(index: Int, size: Long, path: String?) {
                        if (cont.isActive) {
                            cont.resume(if (path != null) Uri.fromFile(File(path)) else uri)
                        }
                    }
                    override fun onFailure(index: Int, failureMessage: String) {
                        if (cont.isActive) cont.resume(uri)   // fall back to original
                    }
                    override fun onCancelled(index: Int) {
                        if (cont.isActive) cont.resume(uri)
                    }
                }
            )
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(uri)
        }

        cont.invokeOnCancellation {
            try { VideoCompressor.cancel() } catch (_: Exception) { }
        }
    }
}
