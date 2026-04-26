package com.schoolsync.teacher.ui.stories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.Story
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import com.schoolsync.teacher.data.repository.firestore.StoryFirestoreRepository
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

data class StoriesUiState(
    val myStories: List<Story> = emptyList(),
    val viewCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val showUploadDialog: Boolean = false,
    val uploadUrl: String = "",
    /**
     * Storage path (e.g. "stories/SCH_X/T0001/1713620938421.jpg") of
     * the currently-picked media. Tracked so a failed Firestore write
     * can roll back the Storage upload (Hardening #5).
     */
    val uploadStoragePath: String = "",
    val uploadCaption: String = "",
    val uploadType: String = "image",
    /** Phase B media-pick + upload progress.
     *  -1 = idle. 0..99 = upload in flight. 100 = done (URL populated). */
    val mediaUploadPercent: Int = -1,
    val error: String? = null
)

sealed class StoriesEvent {
    data class Success(val message: String) : StoriesEvent()
    data class Error(val message: String) : StoriesEvent()
}

/**
 * Teacher Stories VM — Firestore-only.
 *
 * Subscribes to [StoryFirestoreRepository.observeMyStories] which
 * is a real-time snapshot listener over the SAME `stories`
 * collection that the parent app reads and the admin panel
 * moderates. A new upload from this VM is reflected on every
 * connected client (parents, admin moderation queue) within ~100ms
 * — no manual refresh on either side.
 *
 * The legacy RTDB-backed StoryRepository (Social/Stories +
 * Social/StoryViews) has been removed from the call path; the
 * collection is no longer read or written.
 */
@HiltViewModel
class StoriesTeacherViewModel @Inject constructor(
    private val storyRepo: StoryFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "StoriesVM"
    }

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StoriesEvent>()
    val events = _events.asSharedFlow()

    init {
        observeMyStories()
    }

    /**
     * Real-time observer — replaces the old `loadStories()` one-shot.
     * Any upload (from this device or another) that lands in
     * Firestore re-emits here automatically; viewCount changes from
     * parent reads do too. No manual refresh needed.
     */
    private fun observeMyStories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            storyRepo.observeMyStories().collect { docs ->
                val stories = docs.map { it.toStory() }
                val counts = docs.associate { it.id to it.viewCount }
                Log.d(TAG, "snapshot: ${stories.size} stories")
                _uiState.update {
                    it.copy(
                        myStories = stories,
                        viewCounts = counts,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    fun uploadStory() {
        val state = _uiState.value
        if (state.uploadUrl.isBlank()) {
            com.schoolsync.teacher.util.debugLog("Stories.publish BLOCKED — uploadUrl blank")
            viewModelScope.launch { _events.emit(StoriesEvent.Error("Media URL is required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            // Snapshot the Storage path BEFORE the Firestore write so
            // we can roll back if the doc fails to land.
            val storagePathToCleanup = state.uploadStoragePath
            com.schoolsync.teacher.util.debugLog(
                "Stories.publish START path=$storagePathToCleanup type=${state.uploadType} captionLen=${state.uploadCaption.length}"
            )
            try {
                val teacherName = tokenManager.userName.firstOrNull().orEmpty()
                storyRepo.uploadStory(
                    mediaUrl    = state.uploadUrl.trim(),
                    type        = state.uploadType,
                    caption     = state.uploadCaption.trim(),
                    teacherName = teacherName,
                    teacherPic  = ""   // resolved from cached profile pic in repo
                ).fold(
                    onSuccess = { storyId ->
                        com.schoolsync.teacher.util.debugLog("Stories.publish DONE id=$storyId")
                        Log.d(TAG, "Story uploaded: $storyId")
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                showUploadDialog = false,
                                uploadUrl = "",
                                uploadStoragePath = "",
                                uploadCaption = "",
                                uploadType = "image",
                                mediaUploadPercent = -1
                            )
                        }
                        _events.emit(StoriesEvent.Success("Story uploaded successfully"))
                        // No reload call — listener auto-pushes the new doc.
                    },
                    onFailure = { e ->
                        com.schoolsync.teacher.util.debugLog(
                            "Stories.publish FAILED: ${e.javaClass.simpleName}: ${e.message}"
                        )
                        Log.e(TAG, "Failed to upload story", e)
                        // Hardening #5 — Storage upload already happened
                        // (it landed during the picker step) but the
                        // Firestore write failed. Clean up the orphan.
                        if (storagePathToCleanup.isNotBlank()) {
                            val deleted = com.schoolsync.teacher.util.StoryMediaUploader
                                .deleteByPath(storagePathToCleanup)
                            Log.d(TAG, "Rollback: storage file delete=$deleted path=$storagePathToCleanup")
                        }
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadUrl = "",
                                uploadStoragePath = "",
                                mediaUploadPercent = -1
                            )
                        }
                        _events.emit(StoriesEvent.Error(e.message ?: "Failed to upload story"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload story", e)
                if (storagePathToCleanup.isNotBlank()) {
                    com.schoolsync.teacher.util.StoryMediaUploader
                        .deleteByPath(storagePathToCleanup)
                }
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadUrl = "",
                        uploadStoragePath = "",
                        mediaUploadPercent = -1
                    )
                }
                _events.emit(StoriesEvent.Error(e.message ?: "Failed to upload story"))
            }
        }
    }

    fun deleteStory(story: Story) {
        viewModelScope.launch {
            try {
                storyRepo.deleteStory(story.storyId).fold(
                    onSuccess = {
                        _events.emit(StoriesEvent.Success("Story deleted"))
                        // Listener removes the row automatically.
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

    fun setUploadUrl(url: String) { _uiState.update { it.copy(uploadUrl = url) } }
    fun setUploadCaption(caption: String) { _uiState.update { it.copy(uploadCaption = caption) } }
    fun setUploadType(type: String) { _uiState.update { it.copy(uploadType = type) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    /**
     * Phase B — receive a content Uri from the system photo picker,
     * validate, upload to Firebase Storage, and populate `uploadUrl`
     * with the resulting download URL when finished. Updates the
     * `mediaUploadPercent` state continuously so the dialog can render
     * a progress bar.
     *
     * On any failure, emits a StoriesEvent.Error and clears the
     * progress so the user can retry.
     */
    fun pickAndUploadMedia(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val state = _uiState.value
            val declaredType = state.uploadType
            com.schoolsync.teacher.util.debugLog(
                "Stories.pick START type=$declaredType uri=$uri"
            )
            val validationError = com.schoolsync.teacher.util.StoryMediaUploader.validate(
                context, uri, declaredType
            )
            if (validationError != null) {
                com.schoolsync.teacher.util.debugLog("Stories.pick VALIDATION FAIL: $validationError")
                _events.emit(StoriesEvent.Error(validationError))
                return@launch
            }

            val schoolCode = tokenManager.schoolCode.firstOrNull().orEmpty()
            val teacherId  = tokenManager.userId.firstOrNull().orEmpty()
            if (schoolCode.isBlank() || teacherId.isBlank()) {
                com.schoolsync.teacher.util.debugLog(
                    "Stories.pick IDENTITY BLANK schoolCode='$schoolCode' teacherId='$teacherId'"
                )
                _events.emit(StoriesEvent.Error("Missing school or user — please re-login."))
                return@launch
            }
            com.schoolsync.teacher.util.debugLog(
                "Stories.pick → storage upload starting, school=$schoolCode teacher=$teacherId"
            )

            _uiState.update { it.copy(mediaUploadPercent = 0) }
            com.schoolsync.teacher.util.StoryMediaUploader.upload(
                context, uri, schoolCode, teacherId, declaredType
            ).collect { progress ->
                when (progress) {
                    is com.schoolsync.teacher.util.StoryMediaUploader.UploadProgress.InProgress -> {
                        _uiState.update { it.copy(mediaUploadPercent = progress.percent) }
                    }
                    is com.schoolsync.teacher.util.StoryMediaUploader.UploadProgress.Done -> {
                        com.schoolsync.teacher.util.debugLog(
                            "Stories.upload DONE url=${progress.downloadUrl.take(60)}… path=${progress.storagePath}"
                        )
                        _uiState.update {
                            it.copy(
                                uploadUrl = progress.downloadUrl,
                                uploadStoragePath = progress.storagePath,
                                mediaUploadPercent = 100
                            )
                        }
                        _events.emit(StoriesEvent.Success("Media uploaded — tap Upload to publish"))
                    }
                    is com.schoolsync.teacher.util.StoryMediaUploader.UploadProgress.Failed -> {
                        com.schoolsync.teacher.util.debugLog("Stories.upload FAILED: ${progress.reason}")
                        _uiState.update { it.copy(mediaUploadPercent = -1) }
                        _events.emit(StoriesEvent.Error(progress.reason))
                    }
                }
            }
        }
    }

    /**
     * Discard a previously-uploaded URL (lets user re-pick). Also
     * deletes the Storage file so it doesn't become an orphan.
     */
    fun clearPickedMedia() {
        val path = _uiState.value.uploadStoragePath
        _uiState.update { it.copy(uploadUrl = "", uploadStoragePath = "", mediaUploadPercent = -1) }
        if (path.isNotBlank()) {
            viewModelScope.launch {
                com.schoolsync.teacher.util.StoryMediaUploader.deleteByPath(path)
            }
        }
    }

    /** Pull-to-refresh kept as a no-op convenience — listener already
     *  delivers fresh data; this just clears any error banner. */
    fun refresh() { _uiState.update { it.copy(error = null) } }

    // ─── Mapper: Firestore doc → existing UI Story model ───────────
    private fun StoryDoc.toStory(): Story {
        // createdAt is Any? — Firestore may deliver Timestamp; convert
        // to epoch millis for the UI's existing Long-typed field.
        val createdMillis = when (val ts = createdAt) {
            is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
            is Number -> ts.toLong()
            is String -> 0L  // server timestamp pending — show as 0
            else -> 0L
        }
        return Story(
            storyId    = id,
            mediaUrl   = mediaUrl,
            type       = type,
            caption    = caption,
            createdAt  = createdMillis,
            expiresAt  = expiresAtMillis,      // canonical Timestamp → Long
            // Use effective* helpers so legacy docs (no authorX fields)
            // still render with the teacher* fallback.
            teacherName = effectiveAuthorName,
            teacherPic  = effectiveAuthorPic,
            viewCount   = viewCount
        )
    }
}
