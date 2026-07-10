package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Wire-format model for a `galleryAlbums` Firestore document.
 * Harmonized schema (Phase C-2) — fields shared between Admin (Events.php),
 * Teacher (this app), and Parent.
 */
data class GalleryAlbumDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val albumId: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",           // canonical: NOT coverUrl
    val coverPinned: Boolean = false,      // true = admin pinned cover; uploads must NOT auto-advance it
    val source: String = "general",        // "event" | "general"
    val eventId: String = "",
    val session: String = "",
    val category: String = "",
    val mediaCount: Int = 0,
    val isArchived: Boolean = false,       // replaces legacy `status`
    val createdBy: String = "",
    val createdAt: String = "",            // ISO 8601 (NOT serverTimestamp)
    val updatedAt: String = "",
    val archivedAt: String? = null,
    val archivedBy: String? = null
)

/**
 * Wire-format model for a `galleryMedia` Firestore document.
 */
data class GalleryMediaDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val albumId: String = "",
    val url: String = "",
    val type: String = "image",            // "image" | "video"
    val thumbnail: String = "",            // poster frame for video (cross-system contract)
    val duration: String = "",             // video length label (cross-system contract; written as string, may be "")
    val caption: String = "",
    val isArchived: Boolean = false,
    val uploadedBy: String = "",
    val uploadedAt: String = "",
    val updatedAt: String = ""
)
