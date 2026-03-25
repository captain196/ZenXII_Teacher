package com.schoolsync.teacher.data.model

data class GalleryAlbum(
    val albumId: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",
    val category: String = "",
    val createdAt: Long = 0L,
    val mediaCount: Int = 0,
    val status: String = "active"
) {
    companion object {
        fun fromMap(albumId: String, data: Map<String, Any?>): GalleryAlbum = GalleryAlbum(
            albumId = albumId,
            title = data["title"]?.toString() ?: "",
            description = data["description"]?.toString() ?: "",
            coverImage = data["coverImage"]?.toString() ?: data["cover_image"]?.toString() ?: "",
            category = data["category"]?.toString() ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: (data["created_at"] as? Number)?.toLong() ?: 0L,
            mediaCount = (data["mediaCount"] as? Number)?.toInt() ?: (data["media_count"] as? Number)?.toInt() ?: 0,
            status = data["status"]?.toString() ?: "active"
        )
    }
}

data class GalleryMedia(
    val mediaId: String = "",
    val url: String = "",
    val type: String = "image",
    val caption: String = "",
    val uploadedAt: Long = 0L,
    val uploadedBy: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "url" to url, "type" to type, "caption" to caption,
        "uploadedAt" to uploadedAt, "uploadedBy" to uploadedBy
    )
    companion object {
        fun fromMap(mediaId: String, data: Map<String, Any?>): GalleryMedia = GalleryMedia(
            mediaId = mediaId,
            url = data["url"]?.toString() ?: "",
            type = data["type"]?.toString() ?: "image",
            caption = data["caption"]?.toString() ?: "",
            uploadedAt = (data["uploadedAt"] as? Number)?.toLong() ?: (data["uploaded_at"] as? Number)?.toLong() ?: 0L,
            uploadedBy = data["uploadedBy"]?.toString() ?: data["uploaded_by"]?.toString() ?: ""
        )
    }
}
