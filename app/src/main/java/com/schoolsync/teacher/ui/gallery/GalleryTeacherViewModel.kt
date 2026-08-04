package com.schoolsync.teacher.ui.gallery

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.GalleryMedia
import com.schoolsync.teacher.data.repository.firestore.GalleryFirestoreRepository
import com.schoolsync.teacher.util.GalleryMediaUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class GalleryUiState(
    val albums: List<GalleryAlbum> = emptyList(),
    val selectedAlbum: GalleryAlbum? = null,
    val media: List<GalleryMedia> = emptyList(),
    val isLoadingAlbums: Boolean = false,
    val isLoadingMedia: Boolean = false,
    val isUploading: Boolean = false,
    /** True while a picked video is being transcoded before upload. */
    val isCompressing: Boolean = false,
    /** Compression progress 0..100 (only meaningful while [isCompressing]). */
    val compressPercent: Int = 0,
    val isCreatingAlbum: Boolean = false,
    val showCreateAlbumDialog: Boolean = false,
    val showUploadMediaDialog: Boolean = false,
    /** Set when the ALBUMS load fails (network / permission / index) — drives a
     *  distinct error+retry state so a failure isn't masked as "no albums yet". */
    val albumsError: String? = null,
    /** Set when the MEDIA load for the open album fails — same rationale. */
    val mediaError: String? = null
)

sealed class GalleryEvent {
    data class Success(val message: String) : GalleryEvent()
    data class Error(val message: String) : GalleryEvent()
}

@HiltViewModel
class GalleryTeacherViewModel @Inject constructor(
    private val galleryRepository: GalleryFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "GalleryVM"
        // Backstop so a stalled upload can't strand the user forever behind a
        // non-dismissible spinner. Generous enough for a full-size video on a
        // weak school connection; the user can also cancel manually.
        private const val IMAGE_UPLOAD_TIMEOUT_MS = 120_000L   // 2 min
        private const val VIDEO_UPLOAD_TIMEOUT_MS = 420_000L   // 7 min
    }

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GalleryEvent>()
    val events = _events.asSharedFlow()

    /** Handle to the in-flight upload so the user can cancel it. */
    private var uploadJob: Job? = null

    /** Live-listener subscriptions, restarted on manual refresh / retry. */
    private var albumsJob: Job? = null
    private var mediaJob: Job? = null

    init {
        loadAlbums()
    }

    /**
     * (Re)subscribe to the real-time albums listener. New/removed albums appear
     * without a manual refresh; the Refresh button re-subscribes (which also
     * recovers from a terminal listener error, since a callbackFlow closes on
     * error and won't re-emit on its own).
     */
    fun loadAlbums() {
        albumsJob?.cancel()
        _uiState.update { it.copy(isLoadingAlbums = true, albumsError = null) }
        albumsJob = viewModelScope.launch {
            try {
                galleryRepository.observeAlbums().collect { result ->
                    result.fold(
                        onSuccess = { albums ->
                            Log.d(TAG, "Loaded ${albums.size} albums")
                            _uiState.update {
                                it.copy(albums = albums, isLoadingAlbums = false, albumsError = null)
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to load albums: ${e.message}", e)
                            _uiState.update {
                                it.copy(isLoadingAlbums = false, albumsError = e.message ?: "Couldn't load albums")
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load albums", e)
                _uiState.update { it.copy(isLoadingAlbums = false, albumsError = e.message ?: "Couldn't load albums") }
            }
        }
    }

    /**
     * Open (select) an album by its albumId — used when arriving from the
     * Events "View Photos" jump. Reuses the loaded album list when possible;
     * otherwise refreshes albums first, then selects the match.
     */
    fun openAlbumById(albumId: String) {
        if (albumId.isBlank()) return
        viewModelScope.launch {
            _uiState.value.albums.firstOrNull { it.albumId == albumId }?.let { album ->
                selectAlbum(album)
                return@launch
            }
            _uiState.update { it.copy(isLoadingAlbums = true, albumsError = null) }
            galleryRepository.getAlbums().fold(
                onSuccess = { albums ->
                    _uiState.update { it.copy(albums = albums, isLoadingAlbums = false, albumsError = null) }
                    albums.firstOrNull { it.albumId == albumId }?.let { selectAlbum(it) }
                },
                onFailure = { e ->
                    Log.e(TAG, "openAlbumById failed: ${e.message}", e)
                    _uiState.update { it.copy(isLoadingAlbums = false, albumsError = e.message ?: "Couldn't load albums") }
                }
            )
        }
    }

    fun selectAlbum(album: GalleryAlbum?) {
        _uiState.update { it.copy(selectedAlbum = album, media = emptyList()) }
        if (album != null) {
            loadMedia(album.albumId)
        } else {
            // Back to the album grid — stop listening to the previous album's media.
            mediaJob?.cancel()
        }
    }

    /**
     * (Re)subscribe to the real-time media listener for the open album. New
     * uploads (this teacher's or others') appear live. Cancels any previous
     * album's subscription so we only ever listen to the selected album.
     */
    private fun loadMedia(albumId: String) {
        mediaJob?.cancel()
        _uiState.update { it.copy(isLoadingMedia = true, mediaError = null) }
        mediaJob = viewModelScope.launch {
            try {
                galleryRepository.observeAlbumMedia(albumId).collect { result ->
                    result.fold(
                        onSuccess = { media ->
                            Log.d(TAG, "Loaded ${media.size} media for album $albumId")
                            _uiState.update { it.copy(media = media, isLoadingMedia = false, mediaError = null) }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to load media: ${e.message}", e)
                            _uiState.update { it.copy(isLoadingMedia = false, mediaError = e.message ?: "Couldn't load photos") }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load media", e)
                _uiState.update { it.copy(isLoadingMedia = false, mediaError = e.message ?: "Couldn't load photos") }
            }
        }
    }

    fun showCreateAlbumDialog() {
        _uiState.update { it.copy(showCreateAlbumDialog = true) }
    }

    fun hideCreateAlbumDialog() {
        _uiState.update { it.copy(showCreateAlbumDialog = false) }
    }

    fun createAlbum(title: String, description: String, category: String) {
        if (title.isBlank()) {
            viewModelScope.launch {
                _events.emit(GalleryEvent.Error("Album title cannot be empty"))
            }
            return
        }

        // Flip the flag SYNCHRONOUSLY (before launching) so the confirm button
        // disables on this frame — a fast double-tap can otherwise fire twice
        // before the coroutine runs and create duplicate albums.
        if (_uiState.value.isCreatingAlbum) return
        _uiState.update { it.copy(isCreatingAlbum = true) }
        viewModelScope.launch {
            try {
                galleryRepository.createAlbum(title, description, category).fold(
                    onSuccess = { albumId ->
                        Log.d(TAG, "Created album: $albumId")
                        _uiState.update { it.copy(isCreatingAlbum = false, showCreateAlbumDialog = false) }
                        _events.emit(GalleryEvent.Success("Album created successfully"))
                        // The real-time observeAlbums listener surfaces the new album.
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to create album: ${e.message}", e)
                        _uiState.update { it.copy(isCreatingAlbum = false) }
                        _events.emit(GalleryEvent.Error(e.message ?: "Failed to create album"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create album", e)
                _uiState.update { it.copy(isCreatingAlbum = false) }
                _events.emit(GalleryEvent.Error(e.message ?: "Failed to create album"))
            }
        }
    }

    fun showUploadMediaDialog() {
        _uiState.update { it.copy(showUploadMediaDialog = true) }
    }

    fun hideUploadMediaDialog() {
        _uiState.update { it.copy(showUploadMediaDialog = false) }
    }

    /**
     * Upload a picked file (Uri) to Firebase Storage, then write the
     * resulting download URL into the gallery via [uploadMedia]. Validates
     * size + MIME before kicking off the network upload.
     */
    fun uploadMediaFile(context: Context, uri: Uri, declaredType: String, caption: String) {
        val album = _uiState.value.selectedAlbum ?: run {
            viewModelScope.launch { _events.emit(GalleryEvent.Error("No album selected")) }
            return
        }

        val validationError = GalleryMediaUploader.validate(context, uri, declaredType)
        if (validationError != null) {
            viewModelScope.launch { _events.emit(GalleryEvent.Error(validationError)) }
            return
        }

        // Flip SYNCHRONOUSLY so the confirm button disables this frame (kills
        // the double-submit race). Ignore a second call while one is in flight.
        if (_uiState.value.isUploading) return
        _uiState.update { it.copy(isUploading = true) }

        // Track uploaded object paths OUTSIDE withTimeout so timeout / cancel
        // can roll back whatever already landed in Storage.
        var uploadedPath: String? = null
        var posterPath: String? = null

        uploadJob = viewModelScope.launch {
            try {
                // Quota pre-check (parity with website GALLERY_LIMITS). Runs
                // BEFORE any Storage write; on failure abort without touching
                // Storage. The busy-flag guard above already blocked double-submit.
                val quota = galleryRepository.checkQuota(album.albumId, declaredType)
                if (quota.isFailure) {
                    Log.d(TAG, "uploadMediaFile: blocked by quota: ${quota.exceptionOrNull()?.message}")
                    _uiState.update { it.copy(isUploading = false) }
                    _events.emit(GalleryEvent.Error(quota.exceptionOrNull()?.message ?: "Upload limit reached"))
                    return@launch
                }

                // Canonical school scope: `schoolId` is always populated at
                // login; `schoolCode` is a fallback for older sessions.
                val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: throw Exception("School ID not available")

                // Videos: transcode to ~720p/2Mbps BEFORE the size check, exactly
                // as Stories does. Without this the raw clip goes straight at the
                // 25 MB cap — and a modern phone shoots 1080p60 at ~40-60 Mbps, so
                // 25 MB is 4-8 SECONDS of video. Practically every real clip was
                // rejected with "Video too large", which reads as "gallery video
                // upload is broken". The compressor returns the ORIGINAL uri on
                // any failure, so this can only help.
                val sourceUri = if (declaredType == "video") {
                    _uiState.update { it.copy(isCompressing = true, compressPercent = 0) }
                    try {
                        com.schoolsync.teacher.util.StoryVideoCompressor.compress(context, uri) { pct ->
                            _uiState.update { it.copy(compressPercent = pct) }
                        }
                    } finally {
                        _uiState.update { it.copy(isCompressing = false, compressPercent = 0) }
                    }
                } else {
                    uri
                }

                // Size is checked HERE, against the compressed output, not the
                // raw pick (see GalleryMediaUploader.validateSize).
                if (declaredType == "video") {
                    val sizeError = GalleryMediaUploader.validateSize(context, sourceUri, declaredType)
                    if (sizeError != null) {
                        _uiState.update { it.copy(isUploading = false) }
                        _events.emit(GalleryEvent.Error(sizeError))
                        return@launch
                    }
                }

                val timeout = if (declaredType == "video") VIDEO_UPLOAD_TIMEOUT_MS else IMAGE_UPLOAD_TIMEOUT_MS
                withTimeout(timeout) {
                    Log.d(TAG, "uploadMediaFile: starting upload type=$declaredType")
                    val upload = GalleryMediaUploader.uploadSuspending(
                        context     = context,
                        uri         = uri,
                        schoolId    = schoolId,
                        albumId     = album.albumId,
                        declaredType= declaredType,
                        sourceUri   = sourceUri
                    )
                    uploadedPath = upload.storagePath
                    Log.d(TAG, "uploadMediaFile: storage upload OK")

                    // For videos, generate + upload a poster frame and read the
                    // duration so the Parent app / admin gallery don't show a blank
                    // video tile (cross-system thumbnail contract). Best-effort.
                    val poster = if (declaredType == "video") {
                        GalleryMediaUploader.uploadVideoPoster(context, uri, schoolId, album.albumId)
                    } else null
                    posterPath = poster?.storagePath

                    galleryRepository.uploadMedia(
                        albumId   = album.albumId,
                        url       = upload.downloadUrl,
                        type      = declaredType,
                        caption   = caption,
                        thumbnail = poster?.thumbnailUrl.orEmpty(),
                        duration  = poster?.duration.orEmpty()
                    ).fold(
                        onSuccess = { mediaId ->
                            Log.d(TAG, "uploadMediaFile: firestore write OK mediaId=$mediaId")
                            _uiState.update { it.copy(isUploading = false, showUploadMediaDialog = false) }
                            _events.emit(GalleryEvent.Success("Media uploaded successfully"))
                            // Real-time listeners (observeAlbumMedia / observeAlbums)
                            // pick up the new media row + mediaCount bump on their own,
                            // so no manual reload is needed here.
                        },
                        onFailure = { e ->
                            Log.e(TAG, "uploadMediaFile: firestore write failed: ${e.message}", e)
                            // Roll back the orphaned Storage object(s) so a failed
                            // Firestore write doesn't leave billed, unreferenced files.
                            val deleted = GalleryMediaUploader.deleteByPath(upload.storagePath)
                            if (!deleted) Log.w(TAG, "uploadMediaFile: orphan rollback failed for uploaded object")
                            poster?.storagePath?.let { GalleryMediaUploader.deleteByPath(it) }
                            _uiState.update { it.copy(isUploading = false) }
                            _events.emit(GalleryEvent.Error(e.message ?: "Failed to save media"))
                        }
                    )
                }
            } catch (timeout: TimeoutCancellationException) {
                // Only the withTimeout block was cancelled — the outer coroutine
                // is still active, so normal suspend cleanup runs fine.
                Log.w(TAG, "uploadMediaFile: timed out after ${if (declaredType == "video") "7m" else "2m"}")
                uploadedPath?.let { GalleryMediaUploader.deleteByPath(it) }
                posterPath?.let { GalleryMediaUploader.deleteByPath(it) }
                _uiState.update { it.copy(isUploading = false) }
                _events.emit(GalleryEvent.Error("Upload timed out. Check your connection and try again."))
            } catch (cancel: CancellationException) {
                // User cancelled: the whole coroutine is cancelled, so run the
                // rollback + snackbar under NonCancellable or they'd be skipped.
                Log.d(TAG, "uploadMediaFile: cancelled by user")
                _uiState.update { it.copy(isUploading = false) }
                withContext(NonCancellable) {
                    uploadedPath?.let { GalleryMediaUploader.deleteByPath(it) }
                    posterPath?.let { GalleryMediaUploader.deleteByPath(it) }
                    _events.emit(GalleryEvent.Error("Upload cancelled"))
                }
                throw cancel
            } catch (e: Exception) {
                Log.e(TAG, "uploadMediaFile failed", e)
                uploadedPath?.let { GalleryMediaUploader.deleteByPath(it) }
                posterPath?.let { GalleryMediaUploader.deleteByPath(it) }
                _uiState.update { it.copy(isUploading = false) }
                _events.emit(GalleryEvent.Error(e.message ?: "Upload failed"))
            }
        }
    }

    /** Cancel an in-flight upload (user tapped Cancel on the upload dialog). */
    fun cancelUpload() {
        uploadJob?.cancel(CancellationException("User cancelled upload"))
    }

    fun clearError() {
        _uiState.update { it.copy(albumsError = null, mediaError = null) }
    }

    /** Retry the albums load after a failure (from the inline error state). */
    fun retryAlbums() = loadAlbums()

    /** Retry the current album's media load after a failure. */
    fun retryMedia() {
        _uiState.value.selectedAlbum?.let { loadMedia(it.albumId) }
    }
}
