package com.schoolsync.teacher.data.model.firestore

/**
 * Rich attachment metadata for homework uploads.
 *
 * Introduced 2026-05-15 for the Homework Attachment Workstream Phase 1
 * Step 2 dual-shape backward compatibility pass. Older Firestore homework
 * docs stored attachments as a simple `attachments: List<String>` of
 * download URLs; new uploaders (Step 4 onward) write a richer
 * `attachmentObjects: List<Map>` carrying name / contentType / sizeBytes /
 * uploadedBy / uploadedAtMs alongside the URL.
 *
 * Backward compatibility is preserved by:
 *   • Keeping the legacy `attachments: List<String>` field on HomeworkDoc.
 *   • Adding `attachmentObjects: List<Map<String, Any?>>` as a parallel
 *     field, defaulting to empty list when missing (most current docs).
 *   • Step 4 writers will dual-emit BOTH fields so old readers continue
 *     to see download URLs while new readers get the richer metadata.
 *
 * All fields default to safe empty values so Firestore-deserialized
 * Maps with partial data still produce a usable Attachment.
 */
data class Attachment(
    val name: String = "",            // sanitized display filename
    val storagePath: String = "",     // for cleanup / audit / orphan detection
    val downloadUrl: String = "",     // for display + open
    val contentType: String = "",     // MIME type (image/jpeg, application/pdf, …)
    val sizeBytes: Long = 0L,
    val uploadedBy: String = "",      // Firebase Auth uid of uploader
    val uploadedAtMs: Long = 0L       // epoch milliseconds
) {
    companion object {
        /**
         * Construct an Attachment from a raw Firestore value of either
         * shape:
         *   • String — legacy shape, value is treated as the download URL;
         *              other fields default to blank
         *   • Map<*, *> — new shape, with full metadata fields. Accepts
         *                 both camelCase (downloadUrl, storagePath, …) and
         *                 snake_case (download_url, storage_path, …) keys
         *                 so this parser is forward-compatible with any
         *                 future server-side writer that uses snake_case.
         *
         * Returns null when the input is unparseable (e.g. a Map with
         * neither downloadUrl nor storagePath, a blank String, or an
         * unsupported type). Caller is responsible for filtering nulls.
         */
        fun fromAny(raw: Any?): Attachment? = when (raw) {
            is String -> {
                if (raw.isBlank()) null
                else Attachment(downloadUrl = raw)
            }
            is Map<*, *> -> {
                val url = (raw["downloadUrl"] ?: raw["download_url"] ?: raw["url"] ?: "").toString()
                val path = (raw["storagePath"] ?: raw["storage_path"] ?: "").toString()
                if (url.isBlank() && path.isBlank()) {
                    null  // unusable — no way to display or clean up
                } else {
                    Attachment(
                        name        = (raw["name"] ?: raw["file_name"] ?: "").toString(),
                        storagePath = path,
                        downloadUrl = url,
                        contentType = (raw["contentType"] ?: raw["content_type"] ?: raw["mime"] ?: "").toString(),
                        sizeBytes   = when (val s = raw["sizeBytes"] ?: raw["size_bytes"] ?: raw["size"]) {
                            is Number -> s.toLong()
                            is String -> s.toLongOrNull() ?: 0L
                            else -> 0L
                        },
                        uploadedBy  = (raw["uploadedBy"] ?: raw["uploaded_by"] ?: "").toString(),
                        uploadedAtMs = parseEpochMs(
                            raw["uploadedAtMs"] ?: raw["uploadedAt"] ?: raw["uploaded_at"]
                        )
                    )
                }
            }
            else -> null
        }

        /**
         * Best-effort epoch-millis extraction supporting:
         *   • Long / Int (already epoch ms)
         *   • Numeric String
         *   • Firestore Timestamp serialized as {seconds, nanoseconds} or
         *     {_seconds, _nanoseconds} (REST shape)
         *   • com.google.firebase.Timestamp object (toDate().time)
         */
        private fun parseEpochMs(raw: Any?): Long = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            is com.google.firebase.Timestamp -> raw.toDate().time
            is Map<*, *> -> {
                val sec = when (val s = raw["_seconds"] ?: raw["seconds"]) {
                    is Number -> s.toLong()
                    else -> 0L
                }
                sec * 1000L
            }
            else -> 0L
        }
    }
}

/**
 * Convert an [Attachment] to a Firestore-friendly Map suitable for direct
 * inclusion in a document's `attachmentObjects` array field.
 *
 * Used by [HomeworkFirestoreRepository.createHomework] in Step 4's dual-
 * emit write path. Firestore can serialize a List<Map<String, Any?>>
 * natively; serializing a List<Attachment> data class works too, but
 * an explicit Map conversion makes the on-disk shape obvious from the
 * call site and avoids reflection edge cases when callers later want
 * to splice in additional metadata fields.
 */
fun Attachment.toFirestoreMap(): Map<String, Any?> = mapOf(
    "name"         to name,
    "storagePath"  to storagePath,
    "downloadUrl"  to downloadUrl,
    "contentType"  to contentType,
    "sizeBytes"    to sizeBytes,
    "uploadedBy"   to uploadedBy,
    "uploadedAtMs" to uploadedAtMs
)

/**
 * Extension: merge HomeworkDoc.attachments (legacy List<String>) and
 * HomeworkDoc.attachmentObjects (new List<Map>) into a single canonical
 * List<Attachment>.
 *
 * Merge rule: when both fields are populated for the same logical file,
 * the rich `attachmentObjects` entry wins (it carries full metadata).
 * Legacy URL-only entries that don't appear in attachmentObjects are
 * promoted to Attachment(downloadUrl=url, others blank) so callers see
 * a uniform list regardless of which write path produced the doc.
 *
 * Empty input → empty output. Safe to call on any HomeworkDoc.
 */
fun HomeworkDoc.parsedAttachments(): List<Attachment> {
    val rich = attachmentObjects.mapNotNull { Attachment.fromAny(it) }
    val richUrls = rich.map { it.downloadUrl }.filter { it.isNotBlank() }.toSet()
    val legacyOnly = attachments
        .mapNotNull { Attachment.fromAny(it) }
        .filter { it.downloadUrl !in richUrls }
    return rich + legacyOnly
}
