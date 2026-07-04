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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val albums: List<GalleryAlbum> = emptyList(),
    val selectedAlbum: GalleryAlbum? = null,
    val media: List<GalleryMedia> = emptyList(),
    val isLoadingAlbums: Boolean = false,
    val isLoadingMedia: Boolean = false,
    val isUploading: Boolean = false,
    val isCreatingAlbum: Boolean = false,
    val showCreateAlbumDialog: Boolean = false,
    val showUploadMediaDialog: Boolean = false,
    val error: String? = null
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
    }

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GalleryEvent>()
    val events = _events.asSharedFlow()

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlbums = true, error = null) }
            try {
                galleryRepository.getAlbums().fold(
                    onSuccess = { albums ->
                        Log.d(TAG, "Loaded ${albums.size} albums")
                        _uiState.update {
                            it.copy(albums = albums, isLoadingAlbums = false)
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load albums: ${e.message}", e)
                        _uiState.update {
                            it.copy(isLoadingAlbums = false, error = e.message)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load albums", e)
                _uiState.update { it.copy(isLoadingAlbums = false, error = e.message) }
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
            _uiState.update { it.copy(isLoadingAlbums = true, error = null) }
            galleryRepository.getAlbums().fold(
                onSuccess = { albums ->
                    _uiState.update { it.copy(albums = albums, isLoadingAlbums = false) }
                    albums.firstOrNull { it.albumId == albumId }?.let { selectAlbum(it) }
                },
                onFailure = { e ->
                    Log.e(TAG, "openAlbumById failed: ${e.message}", e)
                    _uiState.update { it.copy(isLoadingAlbums = false, error = e.message) }
                }
            )
        }
    }

    fun selectAlbum(album: GalleryAlbum?) {
        _uiState.update { it.copy(selectedAlbum = album, media = emptyList()) }
        if (album != null) {
            loadMedia(album.albumId)
        }
    }

    private fun loadMedia(albumId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMedia = true) }
            try {
                galleryRepository.getAlbumMedia(albumId).fold(
                    onSuccess = { media ->
                        Log.d(TAG, "Loaded ${media.size} media for album $albumId")
                        _uiState.update { it.copy(media = media, isLoadingMedia = false) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load media: ${e.message}", e)
                        _uiState.update { it.copy(isLoadingMedia = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load media", e)
                _uiState.update { it.copy(isLoadingMedia = false, error = e.message) }
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

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingAlbum = true) }
            try {
                galleryRepository.createAlbum(title, description, category).fold(
                    onSuccess = { albumId ->
                        Log.d(TAG, "Created album: $albumId")
                        _uiState.update { it.copy(isCreatingAlbum = false, showCreateAlbumDialog = false) }
                        _events.emit(GalleryEvent.Success("Album created successfully"))
                        loadAlbums()
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

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                // Canonical school scope: `schoolId` is always populated at
                // login; `schoolCode` is a fallback for older sessions.
                val schoolId = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: throw Exception("School ID not available")

                Log.d(TAG, "uploadMediaFile: starting upload type=$declaredType")
                val upload = GalleryMediaUploader.uploadSuspending(
                    context     = context,
                    uri         = uri,
                    schoolId    = schoolId,
                    albumId     = album.albumId,
                    declaredType= declaredType
                )
                Log.d(TAG, "uploadMediaFile: storage upload OK")

                galleryRepository.uploadMedia(album.albumId, upload.downloadUrl, declaredType, caption).fold(
                    onSuccess = { mediaId ->
                        Log.d(TAG, "uploadMediaFile: firestore write OK mediaId=$mediaId")
                        _uiState.update { it.copy(isUploading = false, showUploadMediaDialog = false) }
                        _events.emit(GalleryEvent.Success("Media uploaded successfully"))
                        loadMedia(album.albumId)
                        loadAlbums() // refresh count
                    },
                    onFailure = { e ->
                        Log.e(TAG, "uploadMediaFile: firestore write failed: ${e.message}", e)
                        // Roll back the orphaned Storage object so a failed
                        // Firestore write doesn't leave a billed, unreferenced file.
                        val deleted = GalleryMediaUploader.deleteByPath(upload.storagePath)
                        if (!deleted) Log.w(TAG, "uploadMediaFile: orphan rollback failed for uploaded object")
                        _uiState.update { it.copy(isUploading = false) }
                        _events.emit(GalleryEvent.Error(e.message ?: "Failed to save media"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "uploadMediaFile failed", e)
                _uiState.update { it.copy(isUploading = false) }
                _events.emit(GalleryEvent.Error(e.message ?: "Upload failed"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
