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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

/**
 * One selectable audience target in the upload dialog — a concrete
 * (class, section) the teacher is assigned to.
 */
data class AudienceOption(
    /** Canonical token (StorySharedConfig.audienceKey), e.g. "8-a". */
    val token: String,
    /** Human label, e.g. "Class 8th Section A"  /* i18n-ignore: sample constant */. */
    val label: String,
    /** True if the teacher is the class-teacher of this section. */
    val isClassTeacher: Boolean
)

data class StoriesUiState(
    /** ACTIVE stories only (expiresAt > now). */
    val myStories: List<Story> = emptyList(),
    /** EXPIRED stories (expiresAt <= now) — shown in the Archived
     *  section so the teacher can still see past posts until Firestore
     *  TTL cleans them up. Newest-first, same as active. */
    val archivedStories: List<Story> = emptyList(),
    val viewCounts: Map<String, Int> = emptyMap(),
    // ── Identity for the tray's "Your story" tile ──
    /** This staff member's own id — used to split own stories out of the tray. */
    val myUserId: String = "",
    val myName: String = "",
    val myPic: String = "",
    /** Class-sections this teacher can target (from assignments). */
    val audienceOptions: List<AudienceOption> = emptyList(),
    /** Selected canonical tokens. EMPTY = whole school. */
    val selectedAudience: Set<String> = emptySet(),
    // ── Archived gallery + viewer ──
    /** Whether the full-screen archived gallery overlay is showing. */
    val showArchivedGallery: Boolean = false,
    /** Index into archivedStories of the story open in the full-screen
     *  gallery viewer; null = closed. Enables swipe to prev/next. */
    val archivedViewerIndex: Int? = null,
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
    /** Generated poster URL for a picked VIDEO (empty for images). */
    val uploadThumbnailUrl: String = "",
    /**
     * True while a picked video's poster frame is still being extracted and
     * uploaded. Posting is BLOCKED until this clears.
     *
     * Without it there was a race that silently shipped posterless videos: the
     * media upload sets uploadUrl (enabling Share) and only THEN starts the
     * poster job, so tapping Share promptly wrote a story with an empty
     * thumbnailUrl — a permanently blank tile, since nothing ever backfilled
     * it. This is exactly what happened to the video story now in production.
     */
    val posterPending: Boolean = false,
    /** Poster extraction genuinely failed — the author is told, not left guessing. */
    val posterFailed: Boolean = false,
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
    private val tokenManager: TokenManager,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
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
        loadIdentity()
    }

    /**
     * Own id/name/avatar for the tray's "Your story" tile. Needed even when
     * this staff member has posted nothing — the tile is still shown (as the
     * post entry) whenever they hold edit rights, so it can't be derived from
     * their own story group the way the other tiles are.
     */
    private fun loadIdentity() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    myUserId = tokenManager.userId.firstOrNull().orEmpty(),
                    myName = tokenManager.userName.firstOrNull().orEmpty(),
                    myPic = tokenManager.profilePic.firstOrNull().orEmpty()
                )
            }
        }
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
                // Split into live vs expired so the UI can show an
                // "Archived" section for past stories. Docs arrive
                // already sorted newest-first; partition preserves order.
                val now = System.currentTimeMillis()
                val (activeDocs, expiredDocs) = docs.partition { it.expiresAtMillis > now }
                val active = activeDocs.map { it.toStory() }
                val archived = expiredDocs.map { it.toStory() }
                val counts = docs.associate { it.id to it.viewCount }
                Log.d(TAG, "snapshot: ${active.size} active, ${archived.size} archived")
                // Repair any of THIS author's videos that have no poster.
                // Previously this only fired when they opened the insights
                // sheet, so a blank tile persisted for every other viewer until
                // the author happened to tap their own card. Driving it off the
                // snapshot fixes it as soon as the story is known about.
                active.forEach { backfillPosterIfMissing(it) }
                _uiState.update {
                    it.copy(
                        myStories = active,
                        archivedStories = archived,
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
            com.schoolsync.teacher.util.debugLog("Stories.publish BLOCKED — uploadUrl blank"  /* i18n-ignore: log */)
            viewModelScope.launch { _events.emit(StoriesEvent.Error(appContext.localizedString(R.string.vm_story_media_required))) }
            return
        }
        // Backstop for the poster race. The Share button is already disabled
        // while a poster is in flight, but state can be re-entered (rotation,
        // a queued tap) and a posterless video is unrecoverable from the doc
        // alone — so refuse here too rather than trusting the button.
        if (state.posterPending) {
            com.schoolsync.teacher.util.debugLog("Stories.publish BLOCKED — poster still generating"  /* i18n-ignore: log */)
            viewModelScope.launch {
                _events.emit(StoriesEvent.Error(appContext.localizedString(R.string.vm_story_preview_wait)))
            }
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
                    thumbnailUrl = state.uploadThumbnailUrl.trim(),
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
                                uploadThumbnailUrl = "",
                                pickedLocalUri = "",
                                uploadCaption = "",
                                uploadType = "image",
                                mediaUploadPercent = -1,
                                posterPending = false,
                                posterFailed = false
                            )
                        }
                        _events.emit(StoriesEvent.Success(appContext.localizedString(R.string.vm_story_uploaded)))
                        // No reload call — listener auto-pushes the new doc.
                    },
                    onFailure = { e ->
                        com.schoolsync.teacher.util.debugLog(
                            "Stories.publish FAILED: ${e.javaClass.simpleName}: ${e.message}"
                        )
                        Log.e(TAG, appContext.localizedString(R.string.vm_story_upload_failed), e)
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
                        _events.emit(StoriesEvent.Error(e.message ?: appContext.localizedString(R.string.vm_story_upload_failed)))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, appContext.localizedString(R.string.vm_story_upload_failed), e)
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
                _events.emit(StoriesEvent.Error(e.message ?: appContext.localizedString(R.string.vm_story_upload_failed)))
            }
        }
    }

    // ── Poster self-heal ───────────────────────────────────────────

    /** One self-heal attempt per story per session — never a retry storm. */
    private val posterBackfillAttempted = mutableSetOf<String>()

    /**
     * Self-healing poster: a video story with no `thumbnailUrl` shows a blank
     * tile in every surface forever. Decode a frame from the already-uploaded
     * video, upload it, patch the doc — the listener then pushes the repaired
     * story back into the list on its own.
     *
     * Covers legacy posts, admin-panel posts (PHP extracts no frames) and any
     * post where on-device generation failed. Silent by design: a missing
     * poster is cosmetic and not worth interrupting the author over.
     */
    private fun backfillPosterIfMissing(story: Story) {
        if (!story.type.equals("video", ignoreCase = true)) return
        if (story.thumbnailUrl.isNotBlank()) return
        if (!posterBackfillAttempted.add(story.storyId)) return
        viewModelScope.launch {
            storyRepo.backfillThumbnail(story.storyId).fold(
                onSuccess = { Log.d(TAG, "poster backfilled for ${story.storyId}") },
                onFailure = { e -> Log.d(TAG, "poster backfill skipped for ${story.storyId}: ${e.message}") }
            )
        }
    }

    // ── Archived gallery + viewer ──────────────────────────────────
    fun openArchivedGallery() { _uiState.update { it.copy(showArchivedGallery = true) } }
    fun closeArchivedGallery() { _uiState.update { it.copy(showArchivedGallery = false) } }
    fun openArchivedViewer(story: Story) {
        val idx = _uiState.value.archivedStories.indexOfFirst { it.storyId == story.storyId }
        _uiState.update { it.copy(archivedViewerIndex = if (idx >= 0) idx else 0) }
    }
    fun closeArchivedViewer() { _uiState.update { it.copy(archivedViewerIndex = null) } }

    fun deleteStory(story: Story) {
        viewModelScope.launch {
            try {
                storyRepo.deleteStory(story.storyId).fold(
                    onSuccess = {
                        _events.emit(StoriesEvent.Success(appContext.localizedString(R.string.vm_story_deleted)))
                        // Listener removes the row automatically.
                    },
                    onFailure = { e ->
                        _events.emit(StoriesEvent.Error(e.message ?: appContext.localizedString(R.string.vm_delete_failed)))
                    }
                )
            } catch (e: Exception) {
                _events.emit(StoriesEvent.Error(e.message ?: appContext.localizedString(R.string.vm_delete_failed)))
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
                uploadType = if (closing) "image" else it.uploadType,
                uploadThumbnailUrl = if (closing) "" else it.uploadThumbnailUrl,
                posterPending = if (closing) false else it.posterPending,
                posterFailed = if (closing) false else it.posterFailed
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
                _events.emit(StoriesEvent.Error(appContext.localizedString(R.string.vm_missing_school_user)))
                return@launch
            }
            com.schoolsync.teacher.util.debugLog(
                "Stories.pick → storage upload starting, school=$schoolCode teacher=$teacherId"
            )

            // Show the picked media immediately as a live preview while
            // it processes (download URL only arrives at the end). Drop any
            // poster from a PREVIOUS pick in the same dialog — otherwise
            // re-picking would carry the old video's frame forward.
            _uiState.update {
                it.copy(
                    mediaUploadPercent = 0,
                    pickedLocalUri = uri.toString(),
                    uploadThumbnailUrl = "",
                    posterPending = false,
                    posterFailed = false
                )
            }

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
                        val isVideo = declaredType == "video"
                        _uiState.update {
                            it.copy(
                                uploadUrl = progress.downloadUrl,
                                uploadStoragePath = progress.storagePath,
                                mediaUploadPercent = 100,
                                // Latch BEFORE the poster job starts, in the same
                                // update that enables the Share button — otherwise
                                // there is a window where Share is live and the
                                // poster isn't, which is the race that shipped the
                                // posterless video currently in production.
                                posterPending = isVideo,
                                posterFailed = false
                            )
                        }
                        if (isVideo) {
                            // Try the COMPRESSED file first — it is a real local
                            // file the frame extractor can always open — then the
                            // originally-picked Uri as the fallback.
                            val thumb = com.schoolsync.teacher.util.StoryMediaUploader
                                .uploadThumbnail(
                                    context = context,
                                    candidates = listOf(sourceUri, uri),
                                    schoolId = schoolCode,
                                    teacherId = teacherId
                                )
                            _uiState.update {
                                it.copy(
                                    uploadThumbnailUrl = thumb.orEmpty(),
                                    posterPending = false,
                                    posterFailed = thumb.isNullOrBlank()
                                )
                            }
                            if (thumb.isNullOrBlank()) {
                                com.schoolsync.teacher.util.debugLog("Stories.poster FAILED for $sourceUri")
                                // Not fatal — posting is still allowed and the
                                // author's own client repairs the poster from the
                                // uploaded video the first time they open the
                                // story's insights (backfillPosterIfMissing).
                                _events.emit(StoriesEvent.Error(
                                    appContext.localizedString(R.string.vm_story_no_preview)
                                ))
                            }
                        }
                        _events.emit(StoriesEvent.Success(appContext.localizedString(R.string.vm_story_media_ready)))
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
        _uiState.update {
            it.copy(
                uploadUrl = "", uploadStoragePath = "", uploadThumbnailUrl = "",
                pickedLocalUri = "", mediaUploadPercent = -1,
                posterPending = false, posterFailed = false
            )
        }
        if (path.isNotBlank()) {
            viewModelScope.launch {
                com.schoolsync.teacher.util.StoryMediaUploader.deleteByPath(path)
            }
        }
    }

    // ─── Mapper: Firestore doc → existing UI Story model ───────────
    /**
     * Parse an ISO-8601 timestamp to epoch millis, or 0 when unparseable
     * (which the UI renders as "no time" rather than a wrong one).
     * Needed because the admin panel writes createdAt as a string.
     */
    private fun parseIsoMillis(s: String): Long = runCatching {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    }.recoverCatching {
        java.time.Instant.parse(s).toEpochMilli()
    }.getOrDefault(0L)

    private fun StoryDoc.toStory(): Story {
        // createdAt is Any? — Firestore may deliver Timestamp; convert
        // to epoch millis for the UI's existing Long-typed field.
        val createdMillis = when (val ts = createdAt) {
            is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
            is Number -> ts.toLong()
            // Admin-panel stories write createdAt as an ISO-8601 STRING
            // (Stories.php uses date('c')), not a Timestamp. Returning 0 here
            // made every admin-posted story render with a blank timestamp.
            is String -> parseIsoMillis(ts)
            else -> 0L
        }
        return Story(
            storyId    = id,
            mediaUrl   = mediaUrl,
            type       = type,
            thumbnailUrl = thumbnailUrl,
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
