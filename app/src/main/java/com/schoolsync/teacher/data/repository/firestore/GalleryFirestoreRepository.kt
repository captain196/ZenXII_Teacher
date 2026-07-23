package com.schoolsync.teacher.data.repository.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.GalleryMedia
import com.schoolsync.teacher.data.model.firestore.GalleryAlbumDoc
import com.schoolsync.teacher.data.model.firestore.GalleryMediaDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    // ── Real-time reads ────────────────────────────────────────────────

    /**
     * Live variant of [getAlbums]: emits the album list on every snapshot so
     * new albums (from this or other teachers / admin) appear without a manual
     * refresh. Same schoolId + isArchived filter, orderBy and limit as the
     * one-shot read. A terminal listener error is surfaced as Result.failure
     * (the collector should re-subscribe to recover).
     */
    fun observeAlbums(): Flow<Result<List<GalleryAlbum>>> = flow {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
        if (schoolId == null) {
            emit(Result.failure(Exception("School code not available")))
            return@flow
        }
        emitAll(
            firestoreService.observeDocumentsAs<GalleryAlbumDoc>("galleryAlbums") { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("isArchived", false)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(200)
            }.map { docs -> Result.success(docs.map { it.toAlbum() }) }
        )
    }.catch { e -> emit(Result.failure(e)) }

    /**
     * Live variant of [getAlbumMedia] for a single album. Same required
     * schoolId + albumId + isArchived filter, orderBy and limit as the
     * one-shot read.
     */
    fun observeAlbumMedia(albumId: String): Flow<Result<List<GalleryMedia>>> = flow {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
        if (schoolId == null) {
            emit(Result.failure(Exception("School code not available")))
            return@flow
        }
        emitAll(
            firestoreService.observeDocumentsAs<GalleryMediaDoc>("galleryMedia") { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("albumId", albumId)
                    .whereEqualTo("isArchived", false)
                    .orderBy("uploadedAt", Query.Direction.DESCENDING)
                    .limit(300)
            }.map { docs -> Result.success(docs.map { it.toMedia() }) }
        )
    }.catch { e -> emit(Result.failure(e)) }

    // ── Quota (parity with website GALLERY_LIMITS) ─────────────────────

    /**
     * Pre-upload capacity gate mirroring the website's GALLERY_LIMITS:
     * school-wide max [MAX_SCHOOL_IMAGES] images / [MAX_SCHOOL_VIDEOS] videos,
     * and per-album max [MAX_ALBUM_FILES] files. Uses server-side count()
     * aggregation, falling back to a bounded `.get()` size when aggregation is
     * unavailable. Returns Result.failure with a user-facing message when a
     * limit would be exceeded (>= limit before adding); Result.success to
     * proceed. Fails OPEN on an unresolvable count error (advisory quota;
     * Storage rules still gate) so a transient hiccup can't block uploads.
     */
    suspend fun checkQuota(albumId: String, type: String): Result<Unit> {
        val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            if (type == "video") {
                val videos = countMedia { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("type", "video")
                        .whereEqualTo("isArchived", false)
                }
                if (videos >= MAX_SCHOOL_VIDEOS) {
                    return Result.failure(Exception("School video limit reached ($MAX_SCHOOL_VIDEOS)"))
                }
            } else {
                val images = countMedia { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("type", "image")
                        .whereEqualTo("isArchived", false)
                }
                if (images >= MAX_SCHOOL_IMAGES) {
                    return Result.failure(Exception("School photo limit reached ($MAX_SCHOOL_IMAGES)"))
                }
            }

            val albumFiles = countMedia { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("albumId", albumId)
                    .whereEqualTo("isArchived", false)
            }
            if (albumFiles >= MAX_ALBUM_FILES) {
                return Result.failure(Exception("Album is full (max $MAX_ALBUM_FILES files)"))
            }

            Result.success(Unit)
        } catch (_: Exception) {
            // Can't determine the count (offline / SDK) — allow the upload.
            Result.success(Unit)
        }
    }

    /**
     * Count matching `galleryMedia` docs via server-side aggregation, falling
     * back to a bounded `.get().size()` if count() isn't available. Bounded at
     * limit+1 so the fallback stays cheap yet still detects an over-limit set.
     */
    private suspend fun countMedia(queryBuilder: (com.google.firebase.firestore.CollectionReference) -> Query): Long {
        return try {
            firestoreService.countDocuments("galleryMedia", queryBuilder)
        } catch (_: Exception) {
            firestoreService.queryDocumentsAs<GalleryMediaDoc>("galleryMedia") { ref ->
                queryBuilder(ref).limit(QUOTA_FALLBACK_SCAN)
            }.size.toLong()
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
                if (albumDoc != null && albumDocId != null) {
                    val updates = hashMapOf<String, Any>(
                        "mediaCount" to FieldValue.increment(1L),
                        "updatedAt"  to nowIso
                    )
                    // Cover tracks the LATEST upload (this one) so the album
                    // thumbnail is always the most recent media — UNLESS an admin
                    // pinned a specific cover via setEventCover (coverPinned).
                    // Images use their url; videos use the poster thumbnail (a
                    // video with no extractable poster leaves the cover unchanged).
                    val coverCandidate = if (type == "image") url else thumbnail
                    if (!albumDoc.coverPinned && coverCandidate.isNotBlank()) {
                        updates["coverImage"] = coverCandidate
                    }
                    firestoreService.updateDocument("galleryAlbums", albumDocId, updates)

                    // First media in this album → notify parents once. `mediaCount`
                    // read above is the PRE-upload count (the increment lands only
                    // via the updateDocument just issued), so ==0 means this upload
                    // is the album's first. Best-effort: a push hiccup must never
                    // fail the upload (mirrors RedFlagRepository). Doc-id is
                    // byte-identical to the website's `{schoolId}_gallery_{eventId}`
                    // for event albums so the create-only CF trigger can't double-send.
                    if (albumDoc.mediaCount == 0) {
                        try {
                            val dedupEntity = albumDoc.eventId.ifBlank { albumId }
                            val reqId = "${schoolId}_gallery_${dedupEntity}"
                            firestoreService.setDocument("pushRequests", reqId, mapOf(
                                "mark"         to "GALLERY_ADDED",
                                "schoolId"     to schoolId,
                                "target_group" to "All Parents",
                                "albumId"      to dedupEntity,
                                "title"        to "New Photos",
                                "body"         to ("New photos have been added" +
                                    (if (albumDoc.title.isNotBlank()) " to ${albumDoc.title}" else "") + "."),
                                "category"     to albumDoc.category,
                                "status"       to "pending",
                                "createdAt"    to Timestamp.now()
                            ))
                        } catch (_: Exception) { /* best-effort push */ }
                    }
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

    companion object {
        // Mirrors the website's GALLERY_LIMITS (Events.php).
        private const val MAX_SCHOOL_IMAGES = 200L
        private const val MAX_SCHOOL_VIDEOS = 30L
        private const val MAX_ALBUM_FILES   = 50L
        // Bound for the .get() fallback when count() aggregation is unavailable.
        private const val QUOTA_FALLBACK_SCAN = 250L
    }
}
