package com.schoolsync.teacher.data.model

/**
 * Gallery album — uploaded by teacher (`source="general"`) or auto-generated
 * from an event by the admin (`source="event"`). Single unified Firestore
 * collection `galleryAlbums` (Phase C-2 harmonization).
 *
 * Wire-format invariants:
 *   - `isArchived` is the visibility flag (replaces legacy `status`)
 *   - `coverImage` (NOT `coverUrl`) is the cover URL field
 *   - `createdAt`/`updatedAt` are ISO 8601 strings
 *   - `source` ∈ {"event", "general"}
 */
data class GalleryAlbum(
    val albumId: String = "",
    val schoolId: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",
    val source: String = "general",        // "event" | "general"
    val eventId: String = "",
    val session: String = "",
    val category: String = "",             // optional user-input classifier (sports / academic / cultural / …)
    val mediaCount: Int = 0,
    val isArchived: Boolean = false,
    val createdBy: String = "",
    val createdAt: String = "",            // ISO 8601
    val updatedAt: String = "",            // ISO 8601
    val archivedAt: String? = null,
    val archivedBy: String? = null
) {
    val isEventAlbum: Boolean get() = source == "event"

    companion object {
        fun fromMap(albumId: String, data: Map<String, Any?>): GalleryAlbum = GalleryAlbum(
            albumId     = albumId,
            schoolId    = data["schoolId"]?.toString() ?: "",
            title       = data["title"]?.toString() ?: "",
            description = data["description"]?.toString() ?: "",
            coverImage  = data["coverImage"]?.toString() ?: "",
            source      = data["source"]?.toString() ?: "general",
            eventId     = data["eventId"]?.toString() ?: "",
            session     = data["session"]?.toString() ?: "",
            category    = data["category"]?.toString() ?: "",
            mediaCount  = (data["mediaCount"] as? Number)?.toInt() ?: 0,
            isArchived  = (data["isArchived"] as? Boolean) ?: false,
            createdBy   = data["createdBy"]?.toString() ?: "",
            createdAt   = data["createdAt"]?.toString() ?: "",
            updatedAt   = data["updatedAt"]?.toString() ?: "",
            archivedAt  = data["archivedAt"]?.toString(),
            archivedBy  = data["archivedBy"]?.toString()
        )
    }
}

/**
 * Media item within a gallery album. Unified Firestore collection `galleryMedia`.
 */
data class GalleryMedia(
    val mediaId: String = "",
    val albumId: String = "",
    val url: String = "",
    val type: String = "image",            // "image" | "video"
    val thumbnail: String = "",            // poster frame for video (cross-system contract)
    val duration: String = "",             // video length label; "" for images (written as string)
    val caption: String = "",
    val isArchived: Boolean = false,
    val uploadedBy: String = "",
    val uploadedAt: String = "",           // ISO 8601
    val updatedAt: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "mediaId"    to mediaId,
        "albumId"    to albumId,
        "url"        to url,
        "type"       to type,
        "thumbnail"  to thumbnail,
        "duration"   to duration,
        "caption"    to caption,
        "isArchived" to isArchived,
        "uploadedBy" to uploadedBy,
        "uploadedAt" to uploadedAt,
        "updatedAt"  to updatedAt
    )

    companion object {
        fun fromMap(mediaId: String, data: Map<String, Any?>): GalleryMedia = GalleryMedia(
            mediaId    = mediaId,
            albumId    = data["albumId"]?.toString() ?: "",
            url        = data["url"]?.toString() ?: "",
            type       = data["type"]?.toString() ?: "image",
            thumbnail  = data["thumbnail"]?.toString() ?: "",
            duration   = data["duration"]?.toString() ?: "",
            caption    = data["caption"]?.toString() ?: "",
            isArchived = (data["isArchived"] as? Boolean) ?: false,
            uploadedBy = data["uploadedBy"]?.toString() ?: "",
            uploadedAt = data["uploadedAt"]?.toString() ?: "",
            updatedAt  = data["updatedAt"]?.toString() ?: ""
        )
    }
}
