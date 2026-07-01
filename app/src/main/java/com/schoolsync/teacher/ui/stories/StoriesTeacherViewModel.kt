package com.schoolsync.teacher.ui.stories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.Story
import com.schoolsync.teacher.data.model.firestore.StoryDoc
import com.schoolsync.teacher.data.model.firestore.StorySharedConfig
import com.schoolsync.teacher.data.repository.TeacherRepository
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

/**
 * One selectable audience target in the upload dialog — a concrete
 * (class, section) the teacher is assigned to.
 */
data class AudienceOption(
    /** Canonical token (StorySharedConfig.audienceKey), e.g. "8-a". */
    val token: String,
    /** Human label, e.g. "Class 8th Section A". */
    val label: String,
    /** True if the teacher is the class-teacher of this section. */
    val isClassTeacher: Boolean
)

data class StoriesUiState(
    val myStories: List<Story> = emptyList(),
    val viewCounts: Map<String, Int> = emptyMap(),
    /** Class-sections this teacher can target (from assignments). */
    val audienceOptions: List<AudienceOption> = emptyList(),
    /** Selected canonical tokens. EMPTY = whole school. */
    val selectedAudience: Set<String> = emptySet(),
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
    /** Local content:// Uri of the picked media — shown as a live
     *  preview WHILE the upload is in flight (before the download URL
     *  returns). Cleared on publish / clear / dialog close. */
    val pickedLocalUri: String = "",
    val uploadCaption: String = "",
    val uploadType: String = "image",
    /** Phase B media-pick + upload progress.
     *  -1 = idle. 0..99 = transcode/upload in flight. 100 = done (URL populated). */
    val mediaUploadPercent: Int = -1,
    /** True while a video is being transcoded on-device (before upload), so
     *  the picker overlay can label the phase "Compressing…" vs "Uploading…". */
    val isCompressing: Boolean = false,
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
    private val teacherRepo: TeacherRepository,
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
        loadAudienceOptions()
    }

    /**
     * Load the class-sections this teacher is assigned to, for the
     * upload dialog's audience picker. Default selection = the
     * section(s) they're class-teacher of; if none, all assigned
     * sections (so a pure subject teacher's post is still scoped to
     * what they teach rather than blasted school-wide).
     */
    private fun loadAudienceOptions() {
        viewModelScope.launch {
            val assignments = teacherRepo.getAssignedClasses().getOrNull().orEmpty()
            val options = assignments
                .filter { it.section.isNotBlank() }
                .groupBy { StorySharedConfig.audienceKey(it.className, it.section) }
                .map { (token, group) ->
                    AudienceOption(
                        token = token,
                        label = "${group.first().className} ${group.first().section}",
                        isClassTeacher = group.any { it.classTeacher }
                    )
                }
                .sortedBy { it.label }
            val default = options.filter { it.isClassTeacher }.map { it.token }.toSet()
                .ifEmpty { options.map { it.token }.toSet() }
            _uiState.update { it.copy(audienceOptions = options, selectedAudience = default) }
        }
    }

    /** Toggle one class-section in/out of the target audience. */
    fun toggleAudience(token: String) {
        _uiState.update {
            val next = it.selectedAudience.toMutableSet()
            if (!next.add(token)) next.remove(token)
            it.copy(selectedAudience = next)
        }
    }

    /** Clear all targets → school-wide. */
    fun selectWholeSchool() {
        _uiState.update { it.copy(selectedAudience = emptySet()) }
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
                    teacherPic  = "",  // resolved from cached profile pic in repo
                    audienceClassKeys = state.selectedAudience.toList()  // empty = school-wide
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
                                pickedLocalUri = "",
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
            val closing = it.showUploadDialog
            it.copy(
                showUploadDialog = !it.showUploadDialog,
                uploadUrl = if (closing) "" else it.uploadUrl,
                uploadStoragePath = if (closing) "" else it.uploadStoragePath,
                pickedLocalUri = if (closing) "" else it.pickedLocalUri,
                mediaUploadPercent = if (closing) -1 else it.mediaUploadPercent,
                uploadCaption = if (closing) "" else it.uploadCaption,
                uploadType = if (closing) "image" else it.uploadType
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
            // Auto-detect image vs video from the picked file's MIME so the
            // teacher never has to pre-select a media type (the picker now
            // accepts both). Keep state.uploadType in sync so the preview
            // decoder and the published story doc use the right type.
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val declaredType = if (mime.startsWith("video/")) "video" else "image"
            _uiState.update { it.copy(uploadType = declaredType) }
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

            // Storage path is stories/{schoolId}/{teacherId}/… and the rule
            // allows the write only when the JWT `school_id` claim == that
            // path segment. The claim equals KEY_SCHOOL_ID (the same value
            // Firestore reads use), whereas KEY_SCHOOL_CODE is set only
            // conditionally in saveProfile and can be blank/stale — which
            // produced "no permission to access this" on upload. Prefer
            // schoolId; fall back to schoolCode only if it's blank.
            val schoolCode = tokenManager.schoolId.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: tokenManager.schoolCode.firstOrNull().orEmpty()
            val teacherId  = tokenManager.userId.firstOrNull().orEmpty()
            if (schoolCode.isBlank() || teacherId.isBlank()) {
                com.schoolsync.teacher.util.debugLog(
                    "Stories.pick IDENTITY BLANK schoolId='$schoolCode' teacherId='$teacherId'"
                )
                _events.emit(StoriesEvent.Error("Missing school or user — please re-login."))
                return@launch
            }
            com.schoolsync.teacher.util.debugLog(
                "Stories.pick → storage upload starting, school=$schoolCode teacher=$teacherId"
            )

            // Show the picked media immediately as a live preview while
            // it processes (download URL only arrives at the end).
            _uiState.update { it.copy(mediaUploadPercent = 0, pickedLocalUri = uri.toString()) }

            // Videos: transcode to ~720p/2Mbps on-device first (WhatsApp/
            // Instagram do the same). Images are downscaled inside the
            // uploader. On any failure the compressor returns the original.
            val sourceUri = if (declaredType == "video") {
                _uiState.update { it.copy(isCompressing = true, mediaUploadPercent = 0) }
                val compressed = com.schoolsync.teacher.util.StoryVideoCompressor.compress(
                    context, uri
                ) { pct -> _uiState.update { it.copy(mediaUploadPercent = pct) } }
                _uiState.update { it.copy(isCompressing = false, mediaUploadPercent = 0) }
                compressed
            } else {
                uri
            }

            com.schoolsync.teacher.util.StoryMediaUploader.upload(
                context, sourceUri, schoolCode, teacherId, declaredType
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
                        _events.emit(StoriesEvent.Success("Media ready — tap Share Story to post"))
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
        _uiState.update { it.copy(uploadUrl = "", uploadStoragePath = "", pickedLocalUri = "", mediaUploadPercent = -1) }
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
            viewCount   = viewCount,
            audienceClassKeys = audienceClassKeys,
            reactionCounts = reactionCounts
        )
    }
}
