package com.schoolsync.teacher.ui.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.GalleryMedia
import com.schoolsync.teacher.data.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val galleryRepository: GalleryRepository
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

    fun uploadMedia(url: String, caption: String, type: String = "image") {
        val album = _uiState.value.selectedAlbum ?: return
        if (url.isBlank()) {
            viewModelScope.launch {
                _events.emit(GalleryEvent.Error("URL cannot be empty"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                galleryRepository.uploadMedia(album.albumId, url, type, caption).fold(
                    onSuccess = { mediaId ->
                        Log.d(TAG, "Uploaded media: $mediaId")
                        _uiState.update { it.copy(isUploading = false, showUploadMediaDialog = false) }
                        _events.emit(GalleryEvent.Success("Media uploaded successfully"))
                        loadMedia(album.albumId)
                        loadAlbums() // Refresh count
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to upload media: ${e.message}", e)
                        _uiState.update { it.copy(isUploading = false) }
                        _events.emit(GalleryEvent.Error(e.message ?: "Failed to upload media"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload media", e)
                _uiState.update { it.copy(isUploading = false) }
                _events.emit(GalleryEvent.Error(e.message ?: "Failed to upload media"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
