package com.schoolsync.teacher.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.schoolsync.teacher.data.model.firestore.Attachment
import kotlinx.coroutines.tasks.await

/**
 * Uploads teacher-created homework attachments to Firebase Cloud Storage
 * and returns a fully-populated [Attachment] metadata object.
 *
 * Introduced 2026-05-15 for Homework Attachment Workstream Phase 1 Step 3.
 * Patterns mirror [GalleryMediaUploader] and [StoryMediaUploader] (validate
 * → upload → return downloadUrl + storagePath → deleteByPath for rollback)
 * with homework-specific constraints:
 *
 *   • MIME allowlist:  image/jpeg, image/png, image/webp, application/pdf
 *     (Office docs and text files intentionally NOT included; add only
 *     when a concrete user need is demonstrated.)
 *   • Per-file size cap: 10 MiB (10 × 1024 × 1024 = 10485760 bytes,
 *     inclusive — matching the future Storage Rule limit).
 *   • Per-homework count cap: 5 files (enforced by callers via
 *     [validateCount]; this uploader does not track aggregate state).
 *
 * Storage path:
 *     homework/{schoolId}/{homeworkId}/teacher/{epochMillis}_{slug}.{ext}
 *
 * Returned [Attachment] is compatible with the Step 2 Parent-side parser
 * (Parent/Attachment.kt) and the Teacher-side [Attachment] data class. The
 * Step 5 writer is responsible for dual-emitting this into BOTH
 * `attachments: List<String>` (URL only) and `attachmentObjects: List<Map>`
 * (full metadata) on the Firestore homework doc.
 *
 * ─── App-layer defense, NOT a security boundary ───────────────────────
 * The MIME and size enforcement here is a UX-fast pre-check plus an
 * honest-client guard. A modified APK or direct REST call can bypass it.
 * Rule-layer enforcement (Storage Rules) is deferred to a future bucket-
 * wide hardening workstream. Until then, the recovery rule allows any
 * authenticated user to upload — this uploader's caps are the only
 * thing preventing oversized / wrong-type uploads from honest clients.
 *
 * ─── Telemetry ─────────────────────────────────────────────────────────
 * Every upload attempt emits one of these structured lines via [debugLog]:
 *
 *   ACC_HW_ATTACHMENT_VALIDATE_FAIL reason=<…> mime=<…> size=<…>
 *   ACC_HW_ATTACHMENT_UPLOAD_START  schoolId=<…> hwId=<…> path=<…> bytes=<…>
 *   ACC_HW_ATTACHMENT_UPLOAD_OK     schoolId=<…> hwId=<…> path=<…> bytes=<…> elapsedMs=<…>
 *   ACC_HW_ATTACHMENT_UPLOAD_FAIL   schoolId=<…> hwId=<…> path=<…> err=<…>
 *   ACC_HW_ATTACHMENT_DELETE_OK     path=<…>
 *   ACC_HW_ATTACHMENT_DELETE_FAIL   path=<…> err=<…>
 *
 * Captured in `cache/debug.log` (50KB ring file). Forensic recipe:
 *   adb shell run-as com.schoolsync.teacher cat cache/debug.log | grep ACC_HW_ATTACHMENT_
 *
 * Originally routed through Log.i/Log.e with tag "HomeworkAttachUpload"
 * (2026-05-15 Step 3), but iQOO/OPPO/Xiaomi/Vivo strip third-party logcat
 * tags entirely — the cache file is the only reliable on-device sink on
 * that device class. Swapped to [debugLog] 2026-05-15 as a freeze-
 * compatible observability hot-fix when Phase 1 smoke testing exposed
 * the gap. Same shape as ACC_DENORM_* and ACC_HW_ATTACHMENT_OPEN_* on
 * the Parent side.
 */
object HomeworkAttachmentUploader {

    /**
     * MIME allowlist for homework attachments. Must match the eventual
     * Storage Rule allowlist (Phase 1 hardening). Intentionally tight —
     * widen only with explicit user-need justification.
     */
    val ALLOWED_MIME_TYPES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf"
    )

    /** 10 MiB per-file cap. Inclusive (a 10-MiB file is allowed). */
    const val MAX_FILE_BYTES: Long = 10L * 1024L * 1024L

    /** Per-homework attachment count cap. */
    const val MAX_FILES_PER_HOMEWORK: Int = 5

    /** Bytes-per-MiB constant for human-readable error messages. */
    private const val MIB: Long = 1024L * 1024L

    /**
     * Pre-upload validation. Returns null on success, or a user-facing
     * error message string on failure. Caller surfaces the string in a
     * toast / dialog.
     *
     * Checks performed:
     *   1. MIME type is in the allowlist (via ContentResolver.getType)
     *   2. File size is ≤ MAX_FILE_BYTES (best-effort; some content URIs
     *      don't expose length, in which case size check is skipped and
     *      the Storage SDK / future rule will reject oversized uploads)
     *
     * Does NOT enforce the per-homework count cap — use [validateCount]
     * for that, BEFORE displaying the file picker / starting upload.
     */
    fun validate(context: Context, uri: Uri): String? {
        val cr = context.contentResolver
        val mime = cr.getType(uri).orEmpty().lowercase()

        if (mime !in ALLOWED_MIME_TYPES) {
            debugLog("ACC_HW_ATTACHMENT_VALIDATE_FAIL reason=mime_not_allowed mime=$mime")
            return "Only JPEG, PNG, WebP images or PDF documents are allowed " +
                    "(got ${if (mime.isBlank()) "unknown type" else mime})."
        }

        val size: Long? = try {
            cr.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.length.takeIf { it > 0 }
            }
        } catch (_: Exception) { null }

        if (size != null && size > MAX_FILE_BYTES) {
            val capMb = MAX_FILE_BYTES / MIB
            val gotMb = "%.1f".format(size.toDouble() / MIB)
            debugLog("ACC_HW_ATTACHMENT_VALIDATE_FAIL reason=too_large mime=$mime size=$size")
            return "File too large ($gotMb MB). Maximum is $capMb MB per file."
        }

        return null
    }

    /**
     * Count-cap check. Returns null on success, or a user-facing error
     * message string when adding [additionalCount] files would push the
     * homework past [MAX_FILES_PER_HOMEWORK].
     *
     * Example: 4 files already selected + picking 2 more = 6 > 5 → fail.
     */
    fun validateCount(currentCount: Int, additionalCount: Int): String? {
        val total = currentCount + additionalCount
        if (total > MAX_FILES_PER_HOMEWORK) {
            return "Maximum $MAX_FILES_PER_HOMEWORK attachments per homework. " +
                    "You already have $currentCount and tried to add $additionalCount."
        }
        return null
    }

    /**
     * Single-file suspend upload. On success returns a fully-populated
     * [Attachment] suitable for direct inclusion in the homework doc's
     * `attachmentObjects` array AND for extracting the URL into the
     * legacy `attachments` array (dual-emit by the Step 5 writer).
     *
     * On failure throws [Exception]. Caller is responsible for rollback
     * of any earlier successful uploads in a multi-file batch via
     * [deleteByPath] on each prior [Attachment.storagePath].
     *
     * Parameters:
     *   • [schoolId]   — used as the first variable segment in the
     *                    Storage path. Whatever value the caller passes
     *                    becomes the path tenant marker. Today's other
     *                    mobile uploaders pass `tokenManager.user.schoolCode`
     *                    (5-digit code); be consistent with that.
     *   • [homeworkId] — the homework document id this attachment belongs
     *                    to.
     *
     * Validation is re-run inside this method as defense-in-depth — if
     * the caller forgot to invoke [validate], the upload still rejects
     * disallowed MIME / oversized files before contacting Storage. The
     * caller-visible behaviour is identical in both cases (the same
     * error string surfaces via the thrown exception).
     */
    suspend fun uploadSuspending(
        context: Context,
        uri: Uri,
        schoolId: String,
        homeworkId: String
    ): Attachment {
        // Defensive re-validation.
        validate(context, uri)?.let { error ->
            throw IllegalArgumentException(error)
        }

        val cr = context.contentResolver
        val mime = cr.getType(uri).orEmpty().lowercase()
        val sizeBytes: Long = try {
            cr.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { len -> len > 0 } } ?: 0L
        } catch (_: Exception) { 0L }

        val originalName = queryDisplayName(context, uri)
        val slug         = sanitizeSlug(originalName)
        val ext          = extensionForMime(mime)
        val nowMs        = System.currentTimeMillis()
        val storagePath  = "homework/$schoolId/$homeworkId/teacher/${nowMs}_$slug.$ext"

        debugLog(
            "ACC_HW_ATTACHMENT_UPLOAD_START schoolId=$schoolId hwId=$homeworkId " +
                "path=$storagePath mime=$mime bytes=$sizeBytes"
        )

        val uploaderUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val started = System.currentTimeMillis()

        try {
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            val elapsed = System.currentTimeMillis() - started

            debugLog(
                "ACC_HW_ATTACHMENT_UPLOAD_OK schoolId=$schoolId hwId=$homeworkId " +
                    "path=$storagePath bytes=$sizeBytes elapsedMs=$elapsed"
            )

            return Attachment(
                name         = "$slug.$ext",
                storagePath  = storagePath,
                downloadUrl  = downloadUrl,
                contentType  = mime,
                sizeBytes    = sizeBytes,
                uploadedBy   = uploaderUid,
                uploadedAtMs = nowMs
            )
        } catch (e: Exception) {
            debugLog(
                "ACC_HW_ATTACHMENT_UPLOAD_FAIL schoolId=$schoolId hwId=$homeworkId " +
                    "path=$storagePath err=${e.message}"
            )
            // Re-throw so caller can rollback any sibling uploads.
            throw e
        }
    }

    /**
     * Best-effort rollback — delete a previously-uploaded Storage object
     * by its full storage path. Returns true on success, false otherwise.
     * Never throws (failures are swallowed and logged; orphan objects
     * are recoverable later by a sweep workstream if needed).
     *
     * Use from the caller when:
     *   • One of several batched uploads succeeded but a later one failed
     *     → roll back the successful ones to keep Storage clean
     *   • The Firestore homework write failed AFTER uploads completed
     *     → roll back uploads so the bucket doesn't bloat with orphaned
     *       files referenced by nothing
     */
    suspend fun deleteByPath(storagePath: String): Boolean = try {
        FirebaseStorage.getInstance().reference.child(storagePath).delete().await()
        debugLog("ACC_HW_ATTACHMENT_DELETE_OK path=$storagePath")
        true
    } catch (e: Exception) {
        debugLog("ACC_HW_ATTACHMENT_DELETE_FAIL path=$storagePath err=${e.message}")
        false
    }

    // ─── Internal helpers ──────────────────────────────────────────────

    /**
     * Pull the display filename from the ContentResolver. Returns empty
     * string when the URI doesn't expose a name (some content providers
     * don't). The caller's slug builder treats that as "use a generic
     * 'file' base name".
     */
    private fun queryDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx).orEmpty() else ""
                } else ""
            }.orEmpty()
        } catch (_: Exception) { "" }
    }

    /**
     * Sanitize the original filename into a Storage-safe slug:
     *   • Strip the file extension (we re-derive it from the MIME type
     *     so the on-disk Content-Type is consistent with what's stored).
     *   • Lowercase everything.
     *   • Replace any character outside [a-z0-9._-] with '_'.
     *   • Collapse repeated underscores into one.
     *   • Trim leading/trailing punctuation.
     *   • Truncate to 64 characters.
     *   • Fall back to "file" when the result is blank (e.g. when the
     *     content provider didn't expose a filename).
     *
     * Path-traversal protection: the slug cannot contain '/' (sanitizer
     * replaces it) nor '..' (the '.' character is allowed but only as
     * a literal; the timestamp prefix in the Storage path means even a
     * `..` slug can't escape the homework folder).
     */
    private fun sanitizeSlug(displayName: String): String {
        val stripped = displayName
            .substringBeforeLast('.', missingDelimiterValue = displayName)
            .lowercase()
        val cleaned = stripped
            .replace(Regex("[^a-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.', '-')
            .take(64)
        return cleaned.ifBlank { "file" }
    }

    /**
     * Map MIME to canonical file extension. Caller has already validated
     * MIME is in [ALLOWED_MIME_TYPES] so the `else` branch should never
     * fire in practice — defensive fallback to "bin".
     */
    private fun extensionForMime(mime: String): String = when (mime) {
        "image/jpeg"      -> "jpg"
        "image/png"       -> "png"
        "image/webp"      -> "webp"
        "application/pdf" -> "pdf"
        else              -> "bin"
    }
}
