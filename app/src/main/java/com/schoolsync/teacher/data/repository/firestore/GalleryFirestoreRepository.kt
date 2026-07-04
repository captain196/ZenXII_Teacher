package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.GalleryMedia
import com.schoolsync.teacher.data.model.firestore.GalleryAlbumDoc
import com.schoolsync.teacher.data.model.firestore.GalleryMediaDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase C-2 canonical gallery repository (Teacher).
 *
 * Reads + writes the unified `galleryAlbums` / `galleryMedia` collections
 * shared with Admin (Events.php) and Parent. No RTDB.
 *
 * Visibility filter: `isArchived == false` (replaces legacy `status==active`).
 * Wire format: see GalleryAlbumDoc / GalleryMediaDoc.
 */
@Singleton
class GalleryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Doc → UI model mapping ─────────────────────────────────────────
    private fun GalleryAlbumDoc.toAlbum(): GalleryAlbum = GalleryAlbum(
        albumId     = albumId.ifBlank { id },
        schoolId    = schoolId,
        title       = title,
        description = description,
        coverImage  = coverImage,
        source      = source.ifBlank { "general" },
        eventId     = eventId,
        session     = session,
        category    = category,
        mediaCount  = mediaCount,
        isArchived  = isArchived,
        createdBy   = createdBy,
        createdAt   = createdAt,
        updatedAt   = updatedAt,
        archivedAt  = archivedAt,
        archivedBy  = archivedBy
    )

    private fun GalleryMediaDoc.toMedia(): GalleryMedia = GalleryMedia(
        mediaId    = id,
        albumId    = albumId,
        url        = url,
        type       = type,
        thumbnail  = thumbnail,
        duration   = duration,
        caption    = caption,
        isArchived = isArchived,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
        updatedAt  = updatedAt
    )

    // ── Reads ──────────────────────────────────────────────────────────

    /**
     * All non-archived albums for the current school, newest first.
     */
    suspend fun getAlbums(): Result<List<GalleryAlbum>> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("isArchived", false)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(200)
            }
            Result.success(docs.map { it.toAlbum() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The event-generated album for a given event, or null if none exists.
     *
     * Admin (Events.php) writes one `galleryAlbums` doc per event that has
     * photos, with `source="event"` and `eventId` == the event's id. This
     * looks that doc up so the Events UI can offer a "View Photos" jump.
     *
     * Same per-doc schoolId guard as the other reads: Firestore rules check
     * resource.data.schoolId, so the query must be school-scoped.
     */
    suspend fun getEventAlbum(eventId: String): Result<GalleryAlbum?> {
        if (eventId.isBlank()) return Result.success(null)
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        // Event albums store the RAW event id (e.g. "EVT0001"), but callers pass
        // EventDoc.id which is the @DocumentId full doc id "{schoolId}_{EVT...}".
        // Strip the prefix so the query matches the album's eventId.
        val rawEventId = eventId.removePrefix("${schoolId}_")

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("eventId", rawEventId)
                    .whereEqualTo("isArchived", false)
                    .limit(1)
            }
            Result.success(docs.firstOrNull()?.toAlbum())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * All non-archived media for an album, newest first.
     *
     * The schoolId filter is REQUIRED — Firestore rules check
     * resource.data.schoolId per-doc, so the query must guarantee all
     * returned docs share the auth'd user's school, otherwise the entire
     * query is rejected with PERMISSION_DENIED.
     */
    suspend fun getAlbumMedia(albumId: String): Result<List<GalleryMedia>> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryMediaDoc>(
                "galleryMedia"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("albumId", albumId)
                    .whereEqualTo("isArchived", false)
                    .orderBy("uploadedAt", Query.Direction.DESCENDING)
                    .limit(300)
            }
            Result.success(docs.map { it.toMedia() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Writes ─────────────────────────────────────────────────────────

    /**
     * Create a teacher-authored ("source=general") album.
     * Signature matches the legacy GalleryRepository.createAlbum so the
     * GalleryTeacherViewModel call site stays unchanged.
     */
    suspend fun createAlbum(
        title: String,
        description: String = "",
        category: String = ""
    ): Result<String> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = tokenManager.userId.firstOrNull().orEmpty()
        val session   = tokenManager.session.firstOrNull().orEmpty()

        val albumId = "${schoolId}_${System.currentTimeMillis()}"
        val nowIso  = nowIso()

        val data = hashMapOf(
            "schoolId"    to schoolId,
            "albumId"     to albumId,
            "title"       to title,
            "description" to description,
            "coverImage"  to "",
            "source"      to "general",          // teacher-created
            "eventId"     to "",
            "session"     to session,
            "category"    to category,
            "mediaCount"  to 0,
            "isArchived"  to false,              // replaces legacy `status: "active"`
            "createdBy"   to teacherId,
            "createdAt"   to nowIso,             // ISO 8601 string (matches admin)
            "updatedAt"   to nowIso
        )

        return try {
            firestoreService.setDocument("galleryAlbums", albumId, data)
            Result.success(albumId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload a media item into an album. Signature matches the legacy
     * GalleryRepository.uploadMedia so the ViewModel call site stays unchanged.
     */
    suspend fun uploadMedia(
        albumId: String,
        url: String,
        type: String = "image",
        caption: String = "",
        thumbnail: String = "",
        duration: String = ""
    ): Result<String> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val teacherId = tokenManager.userId.firstOrNull().orEmpty()

        val mediaId = "${albumId}_${System.currentTimeMillis()}"
        val nowIso  = nowIso()

        // `thumbnail`/`duration` are the cross-system video poster contract — the
        // Parent app + admin gallery read these to render video tiles. Always
        // written (blank for images / when extraction failed) for a stable shape.
        val data = hashMapOf(
            "schoolId"   to schoolId,
            "albumId"    to albumId,
            "url"        to url,
            "type"       to type,
            "caption"    to caption,
            "thumbnail"  to thumbnail,
            "duration"   to duration,
            "isArchived" to false,
            "uploadedBy" to teacherId,
            "uploadedAt" to nowIso,
            "updatedAt"  to nowIso
        )

        return try {
            firestoreService.setDocument("galleryMedia", mediaId, data)

            // Atomically bump the album's mediaCount and updatedAt.
            // We have to find the album doc by `albumId` field because
            // the doc-ID format differs between admin-created event
            // albums ("{loginCode}_{albumId}") and teacher-created
            // albums ("{schoolId}_{millis}"). Failure here is logged
            // but doesn't fail the upload — the media row exists either
            // way, just the count display might be stale until the next
            // album refresh.
            try {
                val albumDocs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                    "galleryAlbums"
                ) { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("albumId", albumId)
                        .limit(1)
                }
                val albumDoc = albumDocs.firstOrNull()
                val albumDocId = albumDoc?.id
                if (albumDocId != null) {
                    val updates = hashMapOf<String, Any>(
                        "mediaCount" to FieldValue.increment(1L),
                        "updatedAt"  to nowIso
                    )
                    // Back-fill the album cover from the first image upload
                    // when it hasn't been set yet (createAlbum writes "").
                    if (albumDoc.coverImage.isBlank() && type == "image" && url.isNotBlank()) {
                        updates["coverImage"] = url
                    }
                    firestoreService.updateDocument("galleryAlbums", albumDocId, updates)
                }
            } catch (_: Exception) { /* best-effort */ }

            Result.success(mediaId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────
    private fun nowIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            .format(java.util.Date())
}
