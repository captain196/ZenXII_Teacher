package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a gallery album in Firestore.
 * Collection: `galleryAlbums`
 */
data class GalleryAlbumDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val mediaCount: Int = 0,
    val eventId: String = "",
    val createdBy: String = "",
    val status: String = "active",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)

/**
 * Represents a media item within a gallery album.
 * Collection: `galleryMedia`
 */
data class GalleryMediaDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val albumId: String = "",
    val url: String = "",
    val type: String = "image",      // "image", "video"
    val caption: String = "",
    val uploadedBy: String = "",
    @ServerTimestamp
    val uploadedAt: Timestamp? = null
)
