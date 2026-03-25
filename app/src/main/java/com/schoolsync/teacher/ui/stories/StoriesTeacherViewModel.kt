package com.schoolsync.teacher.ui.stories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.Story
import com.schoolsync.teacher.data.repository.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoriesUiState(
    val myStories: List<Story> = emptyList(),
    val viewCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val showUploadDialog: Boolean = false,
    val uploadUrl: String = "",
    val uploadCaption: String = "",
    val uploadType: String = "image",
    val error: String? = null
)

sealed class StoriesEvent {
    data class Success(val message: String) : StoriesEvent()
    data class Error(val message: String) : StoriesEvent()
}

@HiltViewModel
class StoriesTeacherViewModel @Inject constructor(
    private val storyRepository: StoryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "StoriesVM"
    }

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StoriesEvent>()
    val events = _events.asSharedFlow()

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                storyRepository.getMyStories().fold(
                    onSuccess = { stories ->
                        Log.d(TAG, "Loaded ${stories.size} stories")
                        _uiState.update { it.copy(myStories = stories, isLoading = false) }
                        // Load view counts for each story
                        loadViewCounts(stories)
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load stories", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stories", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadViewCounts(stories: List<Story>) {
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            for (story in stories) {
                try {
                    counts[story.storyId] = storyRepository.getViewCount(story.storyId)
                } catch (_: Exception) {
                    counts[story.storyId] = 0
                }
            }
            _uiState.update { it.copy(viewCounts = counts) }
        }
    }

    fun uploadStory() {
        val state = _uiState.value
        if (state.uploadUrl.isBlank()) {
            viewModelScope.launch { _events.emit(StoriesEvent.Error("Media URL is required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                storyRepository.uploadStory(
                    mediaUrl = state.uploadUrl.trim(),
                    type = state.uploadType,
                    caption = state.uploadCaption.trim()
                ).fold(
                    onSuccess = { storyId ->
                        Log.d(TAG, "Story uploaded: $storyId")
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                showUploadDialog = false,
                                uploadUrl = "",
                                uploadCaption = "",
                                uploadType = "image"
                            )
                        }
                        _events.emit(StoriesEvent.Success("Story uploaded successfully"))
                        loadStories()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to upload story", e)
                        _uiState.update { it.copy(isUploading = false) }
                        _events.emit(StoriesEvent.Error(e.message ?: "Failed to upload story"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload story", e)
                _uiState.update { it.copy(isUploading = false) }
                _events.emit(StoriesEvent.Error(e.message ?: "Failed to upload story"))
            }
        }
    }

    fun deleteStory(story: Story) {
        viewModelScope.launch {
            try {
                storyRepository.deleteStory(story.storyId).fold(
                    onSuccess = {
                        _events.emit(StoriesEvent.Success("Story deleted"))
                        loadStories()
                    },
                    onFailure = { e ->
                        _events.emit(StoriesEvent.Error(e.message ?: "Failed to delete"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(StoriesEvent.Error(e.message ?: "Failed to delete"))
            }
        }
    }

    fun toggleUploadDialog() {
        _uiState.update {
            it.copy(
                showUploadDialog = !it.showUploadDialog,
                uploadUrl = if (it.showUploadDialog) "" else it.uploadUrl,
                uploadCaption = if (it.showUploadDialog) "" else it.uploadCaption,
                uploadType = if (it.showUploadDialog) "image" else it.uploadType
            )
        }
    }

    fun setUploadUrl(url: String) {
        _uiState.update { it.copy(uploadUrl = url) }
    }

    fun setUploadCaption(caption: String) {
        _uiState.update { it.copy(uploadCaption = caption) }
    }

    fun setUploadType(type: String) {
        _uiState.update { it.copy(uploadType = type) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadStories()
    }
}
