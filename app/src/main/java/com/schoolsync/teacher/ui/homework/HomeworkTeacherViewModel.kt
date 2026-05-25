package com.schoolsync.teacher.ui.homework

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.HomeworkStatusEntry
import com.schoolsync.teacher.data.model.HomeworkTeacher
import com.schoolsync.teacher.data.model.StudentInfo
import com.schoolsync.teacher.data.model.firestore.Attachment
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.teacher.util.HomeworkAttachmentUploader
import com.schoolsync.teacher.util.debugLog
import com.schoolsync.teacher.util.toEpochMillisOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeworkClassSection(
    val className: String,
    val section: String
) {
    val displayName: String get() = "$className - $section"
}

/**
 * One in-progress attachment selection (file picked by the teacher in the
 * Create Homework dialog but not yet uploaded to Firebase Storage). Holds
 * just enough metadata to render a removable chip in the UI and to feed
 * the uploader on submit. Step 4 addition (2026-05-15).
 */
data class SelectedAttachment(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
)

data class HomeworkFormState(
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val dueDate: String = "",
    val isSubmitting: Boolean = false,
    // Step 4 (2026-05-15) — attachment selection state.
    val selectedAttachments: List<SelectedAttachment> = emptyList(),
    /**
     * Index (within selectedAttachments) of the currently-uploading file
     * during submit. -1 = idle / not uploading. UI uses this to put a
     * spinner on the active chip and disable the Add Files button.
     */
    val uploadingIndex: Int = -1,
    /**
     * Last attachment-selection or upload error to surface inline below
     * the chip list. Cleared by `clearAttachmentError()`. Distinct from
     * the global `HomeworkUiState.error` AlertDialog so a per-file
     * MIME / size rejection doesn't push a modal over the dialog itself.
     */
    val attachmentError: String? = null
)

data class HomeworkUiState(
    val availableClasses: List<HomeworkClassSection> = emptyList(),
    val availableSubjects: List<String> = emptyList(),
    val selectedClass: HomeworkClassSection? = null,
    val selectedSubjectFilter: String? = null, // null = all subjects
    val homeworkList: List<HomeworkTeacher> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val formState: HomeworkFormState = HomeworkFormState(),
    val selectedHomework: HomeworkTeacher? = null,
    val submissions: List<HomeworkStatusEntry> = emptyList(),
    val students: List<StudentInfo> = emptyList(),
    /** Teacher marks for non-submitters: studentId → (score, remark). */
    val teacherMarks: Map<String, com.schoolsync.teacher.data.repository.firestore.TeacherMarkEntry> = emptyMap(),
    val showDetailSheet: Boolean = false,
    val isLoadingSubmissions: Boolean = false,
    // Subjects available for the selected class (from assignments)
    val subjectsForClass: List<String> = emptyList(),
    // Phase HW: delete confirmation
    val homeworkToDelete: HomeworkTeacher? = null
)

sealed class HomeworkEvent {
    data class Success(val message: String) : HomeworkEvent()
    data class Error(val message: String) : HomeworkEvent()
}

@HiltViewModel
class HomeworkTeacherViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val tokenManager: TokenManager,
    private val homeworkFirestoreRepo: HomeworkFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeworkVM"
    }

    private val _uiState = MutableStateFlow(HomeworkUiState())
    val uiState: StateFlow<HomeworkUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeworkEvent>()
    val events = _events.asSharedFlow()

    // Cached assignments for subject lookups
    private var allAssignments: List<ClassAssignment> = emptyList()
    private var homeworkListenerJob: Job? = null

    init {
        loadAssignedClasses()
    }

    private fun loadAssignedClasses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        allAssignments = assignments
                        val classSections = assignments
                            .map { HomeworkClassSection(it.className, it.section) }
                            .distinct()
                        val allSubjects = assignments.map { it.subject }.distinct().sorted()

                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                availableSubjects = allSubjects,
                                selectedClass = classSections.firstOrNull()
                            )
                        }

                        if (classSections.isNotEmpty()) {
                            updateSubjectsForClass(classSections.first())
                            loadStudentsForClass(classSections.first())
                            loadHomework()
                        } else {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load assignments", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load classes", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun updateSubjectsForClass(classSection: HomeworkClassSection) {
        val subjects = allAssignments
            .filter { it.className == classSection.className && it.section == classSection.section }
            .map { it.subject }
            .distinct()
            .sorted()
        _uiState.update { it.copy(subjectsForClass = subjects) }
    }

    fun selectClass(classSection: HomeworkClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        _uiState.update {
            it.copy(
                selectedClass = classSection,
                selectedSubjectFilter = null,
                selectedHomework = null,
                showDetailSheet = false
            )
        }
        updateSubjectsForClass(classSection)
        loadStudentsForClass(classSection)
        loadHomework()
    }

    private fun loadStudentsForClass(classSection: HomeworkClassSection) {
        viewModelScope.launch {
            try {
                val result = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                )
                result.getOrNull()?.let { students ->
                    _uiState.update { it.copy(students = students) }
                }
            } catch (e: Exception) {
                // BUG-024 — structured debugLog replaces silent catch. The
                // UI keeps its prior students list (no _uiState mutation on
                // failure) so the screen doesn't render an empty state; the
                // log surfaces the failure for operator triage.
                debugLog("ACC_HW_VM_LOAD_STUDENTS_FAILED class=${classSection.className} section=${classSection.section} err=${e.javaClass.simpleName}:${e.message}")
            }
        }
    }

    fun selectSubjectFilter(subject: String?) {
        _uiState.update { it.copy(selectedSubjectFilter = subject) }
        // Fix #3: re-filter immediately since loadHomework is a one-shot call
        loadHomework()
    }

    fun loadHomework() {
        val classSection = _uiState.value.selectedClass ?: return
        val sectionKey = "${com.schoolsync.teacher.util.Constants.classKey(classSection.className)}/${com.schoolsync.teacher.util.Constants.sectionKey(classSection.section)}"

        homeworkListenerJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }

        homeworkListenerJob = viewModelScope.launch {
            try {
                // Firestore-only: read homework for this section
                homeworkFirestoreRepo.getHomework(sectionKey).fold(
                    onSuccess = { homeworkDocs ->
                        val allHomework = homeworkDocs.map { doc ->
                            HomeworkTeacher(
                                hwId = doc.id,
                                title = doc.title,
                                description = doc.description,
                                subject = doc.subject,
                                teacherId = doc.teacherId,
                                teacherName = doc.teacherName,
                                dueDate = doc.dueDate,
                                createdAt = doc.createdAt.toEpochMillisOrNull() ?: 0L,
                                status = doc.status,
                                className = doc.className,
                                section = doc.section,
                                attachments = doc.attachments
                            )
                        }
                        val subjectFilter = _uiState.value.selectedSubjectFilter
                        val filtered = if (subjectFilter != null) {
                            allHomework.filter { it.subject.equals(subjectFilter, ignoreCase = true) }
                        } else {
                            allHomework
                        }
                        Log.d(TAG, "Firestore loaded: ${filtered.size} homework items")
                        _uiState.update { it.copy(homeworkList = filtered, isLoading = false) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Firestore load failed", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Homework load failed", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // --- Create homework ---

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                formState = HomeworkFormState(
                    subject = it.subjectsForClass.firstOrNull() ?: ""
                )
            )
        }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, formState = HomeworkFormState()) }
    }

    fun updateFormTitle(title: String) {
        _uiState.update { it.copy(formState = it.formState.copy(title = title)) }
    }

    fun updateFormDescription(desc: String) {
        _uiState.update { it.copy(formState = it.formState.copy(description = desc)) }
    }

    fun updateFormSubject(subject: String) {
        _uiState.update { it.copy(formState = it.formState.copy(subject = subject)) }
    }

    fun updateFormDueDate(date: String) {
        _uiState.update { it.copy(formState = it.formState.copy(dueDate = date)) }
    }

    // ────────────────────────────────────────────────────────────────────
    // Step 4 (2026-05-15) — Attachment selection orchestration
    // ────────────────────────────────────────────────────────────────────

    /**
     * Handle a batch of URIs returned by the file-picker activity result.
     *
     * Performs (in order):
     *   1. Count-cap check (current selection + new uris ≤ MAX_FILES).
     *      Excess silently truncated with an error message.
     *   2. Per-file MIME + size validation via
     *      [HomeworkAttachmentUploader.validate]. First rejection short-
     *      circuits — the entire batch's accepted entries land in state
     *      and the rejected file's reason surfaces in `attachmentError`.
     *   3. Display name + size pulled from ContentResolver so the chip
     *      preview can show realistic file metadata before upload.
     *
     * Files only enter `selectedAttachments` after passing validation.
     * Upload to Storage does NOT happen here — that's deferred to the
     * submit handler so a teacher can change their mind / remove chips
     * without leaving orphaned Storage objects.
     */
    fun onAttachmentsPicked(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val state = _uiState.value
        if (state.formState.isSubmitting) return  // ignore picks during upload

        val current = state.formState.selectedAttachments
        val capError = HomeworkAttachmentUploader.validateCount(current.size, uris.size)
        if (capError != null) {
            _uiState.update {
                it.copy(formState = it.formState.copy(attachmentError = capError))
            }
            return
        }

        val accepted = mutableListOf<SelectedAttachment>()
        var firstError: String? = null
        for (uri in uris) {
            val validationError = HomeworkAttachmentUploader.validate(context, uri)
            if (validationError != null) {
                if (firstError == null) firstError = validationError
                continue
            }
            // Pull display name + size from ContentResolver. Either can be
            // null/0 if the provider doesn't expose it — we tolerate that
            // and let the uploader (and Storage SDK) re-check at upload time.
            val cr = context.contentResolver
            val mime = cr.getType(uri).orEmpty()
            var name = "file"
            var size = 0L
            try {
                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val si = c.getColumnIndex(OpenableColumns.SIZE)
                            if (ni >= 0) name = c.getString(ni) ?: name
                            if (si >= 0) size = c.getLong(si)
                        }
                    }
            } catch (_: Exception) { /* keep defaults */ }
            accepted.add(SelectedAttachment(uri = uri, displayName = name, sizeBytes = size, mimeType = mime))
        }

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    selectedAttachments = it.formState.selectedAttachments + accepted,
                    attachmentError = firstError
                )
            )
        }
    }

    /** Remove a chip before submit. No Storage cleanup needed (nothing uploaded yet). */
    fun removeSelectedAttachment(uri: Uri) {
        val state = _uiState.value
        if (state.formState.isSubmitting) return
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    selectedAttachments = it.formState.selectedAttachments.filterNot { sa -> sa.uri == uri }
                )
            )
        }
    }

    /** Dismiss the inline attachment error banner. */
    fun clearAttachmentError() {
        _uiState.update {
            it.copy(formState = it.formState.copy(attachmentError = null))
        }
    }

    /**
     * Submit handler — orchestrates attachment uploads then Firestore write.
     *
     * Flow:
     *   1. Validate form fields (title / subject / due date). Bail early
     *      with a snackbar event on first failure.
     *   2. Mark `isSubmitting = true`.
     *   3. For each selected attachment IN ORDER:
     *      • set `uploadingIndex` so UI can spinner the active chip
     *      • call HomeworkAttachmentUploader.uploadSuspending()
     *      • collect resulting Attachment objects
     *   4. If any upload fails → ROLLBACK every previously-uploaded
     *      Attachment by calling deleteByPath, then surface error and
     *      release the submit lock. NO Firestore write is attempted.
     *   5. After all uploads succeed, re-query the section roster (same
     *      reasoning as before — state could be stale by submit time)
     *      and call homeworkFirestoreRepo.createHomework with the rich
     *      Attachment list. Repository handles dual-emit internally.
     *   6. If the Firestore write fails → ROLLBACK every uploaded
     *      Attachment to keep Storage clean of orphans, then surface
     *      error. The created hwId (if it got to the write call) is
     *      assumed not committed since the call returned failure.
     *   7. On full success → clear the form (including attachments),
     *      emit a success event, refresh the homework list.
     *
     * Caller (Create dialog "Create" button) is responsible for
     * disabling itself while `isSubmitting` is true.
     */
    fun createHomework(context: Context) {
        val state = _uiState.value
        val classSection = state.selectedClass ?: return
        val form = state.formState

        if (form.title.isBlank()) {
            viewModelScope.launch { _events.emit(HomeworkEvent.Error("Title is required")) }
            return
        }
        if (form.subject.isBlank()) {
            viewModelScope.launch { _events.emit(HomeworkEvent.Error("Subject is required")) }
            return
        }
        if (form.dueDate.isBlank()) {
            // Was silently falling back to today via .ifBlank — making it
            // impossible to spot a missed picker selection. Force the
            // teacher to pick explicitly.
            viewModelScope.launch { _events.emit(HomeworkEvent.Error("Due date is required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    formState = it.formState.copy(
                        isSubmitting = true,
                        attachmentError = null
                    )
                )
            }

            val schoolCode = tokenManager.schoolCode.firstOrNull().orEmpty()
            val teacherId = tokenManager.userId.firstOrNull().orEmpty()
            val teacherName = tokenManager.userName.firstOrNull() ?: ""
            val dueDate = form.dueDate

            // Pre-mint the hwId so attachment Storage paths share it with
            // the Firestore doc that will follow. Both this site and the
            // repository's fallback route through the SAME helper
            // [HomeworkFirestoreRepository.mintHwId], which appends an
            // 8-hex-char SecureRandom suffix (matching the Admin PHP
            // Finding #8 format). This eliminates the within-millisecond
            // collision risk that, post-attachment-workstream, would have
            // orphaned uploaded files when two teachers in the same
            // school clicked Create within the same ms — see
            // teacher_hwid_entropy_backlog.md.
            val hwId = HomeworkFirestoreRepository.mintHwId(schoolCode)

            // ── Upload phase ──────────────────────────────────────────
            val uploaded = mutableListOf<Attachment>()
            var uploadError: String? = null
            try {
                for ((i, selected) in form.selectedAttachments.withIndex()) {
                    _uiState.update {
                        it.copy(formState = it.formState.copy(uploadingIndex = i))
                    }
                    val attachment = HomeworkAttachmentUploader.uploadSuspending(
                        context = context,
                        uri = selected.uri,
                        schoolId = schoolCode,
                        homeworkId = hwId
                    )
                    uploaded.add(attachment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Attachment upload failed; rolling back ${uploaded.size} prior uploads", e)
                uploadError = e.message ?: "Attachment upload failed"
            }

            if (uploadError != null) {
                // Rollback partial uploads (best-effort).
                for (att in uploaded) {
                    HomeworkAttachmentUploader.deleteByPath(att.storagePath)
                }
                _uiState.update {
                    it.copy(
                        formState = it.formState.copy(
                            isSubmitting = false,
                            uploadingIndex = -1,
                            attachmentError = uploadError
                        )
                    )
                }
                _events.emit(HomeworkEvent.Error("Could not upload attachments: $uploadError"))
                return@launch
            }

            _uiState.update {
                it.copy(formState = it.formState.copy(uploadingIndex = -1))
            }

            // ── Firestore write phase ─────────────────────────────────
            try {
                val freshRoster = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                ).getOrNull() ?: _uiState.value.students
                val totalStudents = freshRoster.size

                homeworkFirestoreRepo.createHomework(
                    title = form.title.trim(),
                    description = form.description.trim(),
                    subject = form.subject.trim(),
                    className = classSection.className,
                    section = classSection.section,
                    dueDate = dueDate,
                    teacherId = teacherId,
                    teacherName = teacherName,
                    totalStudents = totalStudents,
                    attachments = uploaded,
                    existingHwId = hwId
                ).fold(
                    onSuccess = { firestoreHwId ->
                        Log.d(TAG, "Homework created: $firestoreHwId with ${uploaded.size} attachments")
                        _uiState.update {
                            it.copy(
                                showCreateDialog = false,
                                formState = HomeworkFormState()
                            )
                        }
                        _events.emit(HomeworkEvent.Success("Homework created successfully"))
                        loadHomework() // Refresh list
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to create homework; rolling back ${uploaded.size} uploads", e)
                        for (att in uploaded) {
                            HomeworkAttachmentUploader.deleteByPath(att.storagePath)
                        }
                        _uiState.update {
                            it.copy(formState = it.formState.copy(isSubmitting = false))
                        }
                        _events.emit(HomeworkEvent.Error(e.message ?: "Failed to create homework"))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create homework (outer); rolling back ${uploaded.size} uploads", e)
                for (att in uploaded) {
                    HomeworkAttachmentUploader.deleteByPath(att.storagePath)
                }
                _uiState.update { it.copy(formState = it.formState.copy(isSubmitting = false)) }
                _events.emit(HomeworkEvent.Error(e.message ?: "Failed to create homework"))
            }
        }
    }

    // --- Delete homework ---

    /** Set to non-null to show delete confirmation dialog. */
    fun confirmDelete(hw: HomeworkTeacher?) {
        _uiState.update { it.copy(
            homeworkToDelete = hw
        )}
    }

    fun executeDelete() {
        val hw = _uiState.value.homeworkToDelete ?: return
        _uiState.update { it.copy(homeworkToDelete = null) }

        viewModelScope.launch {
            try {
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                // Cascade — delete every doc in `submissions` AND `teacherMarks`
                // tied to this homework. Skipping teacherMarks (the original
                // bug) left orphans for students who were graded without
                // submitting; they piled up in Firestore and confused later
                // reports.
                // BUG-017 — per-doc cascade-delete failures previously silent
                // (catches were `catch (_: Exception) {}`). Now surfaced via
                // debugLog + per-doc-failure counters so the success message
                // reports partial failures honestly. Atomicity (full
                // transaction wrap) and pagination remain deferred to a
                // future Mobile transaction primitives phase.
                val submissions = homeworkFirestoreRepo.getSubmissions(hw.hwId).getOrNull() ?: emptyList()
                var failedSubDeletes = 0
                for (sub in submissions) {
                    try {
                        fs.collection("submissions").document(sub.id).delete().await()
                    } catch (e: Exception) {
                        failedSubDeletes++
                        debugLog("ACC_HW_DELETE_CASCADE_SUB_FAILED hwId=${hw.hwId} subId=${sub.id} err=${e.javaClass.simpleName}:${e.message}")
                    }
                }
                val schoolCode = tokenManager.schoolId.firstOrNull() ?: ""
                val markDocs = try {
                    fs.collection("teacherMarks")
                        .whereEqualTo("schoolId", schoolCode)
                        .whereEqualTo("homeworkId", hw.hwId)
                        .get()
                        .await()
                        .documents
                } catch (e: Exception) {
                    Log.w(TAG, "teacherMarks query failed during delete cascade: ${e.message}")
                    emptyList()
                }
                var failedMarkDeletes = 0
                for (m in markDocs) {
                    try {
                        m.reference.delete().await()
                    } catch (e: Exception) {
                        failedMarkDeletes++
                        debugLog("ACC_HW_DELETE_CASCADE_MARK_FAILED hwId=${hw.hwId} markId=${m.id} err=${e.javaClass.simpleName}:${e.message}")
                    }
                }

                homeworkFirestoreRepo.deleteHomework(hw.hwId).fold(
                    onSuccess = {
                        Log.d(TAG, "Homework + ${submissions.size} submissions (failed=$failedSubDeletes) + ${markDocs.size} teacherMarks (failed=$failedMarkDeletes) deleted: ${hw.hwId}")
                        val msg = buildString {
                            append("Homework deleted")
                            if (submissions.isNotEmpty()) {
                                append(" (${submissions.size} submission(s)")
                                if (failedSubDeletes > 0) append(", $failedSubDeletes failed")
                            }
                            if (markDocs.isNotEmpty()) {
                                if (submissions.isNotEmpty()) {
                                    append(", ${markDocs.size} mark(s)")
                                    if (failedMarkDeletes > 0) append(", $failedMarkDeletes failed")
                                    append(")")
                                } else {
                                    append(" (${markDocs.size} mark(s)")
                                    if (failedMarkDeletes > 0) append(", $failedMarkDeletes failed")
                                    append(")")
                                }
                            } else if (submissions.isNotEmpty()) append(")")
                        }
                        _events.emit(HomeworkEvent.Success(msg))
                        _uiState.update { it.copy(selectedHomework = null, showDetailSheet = false) }
                        loadHomework()
                    },
                    onFailure = { e ->
                        _events.emit(HomeworkEvent.Error(e.message ?: "Failed to delete"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(HomeworkEvent.Error(e.message ?: "Failed to delete"))
            }
        }
    }

    // --- Submission detail ---

    fun selectHomework(hw: HomeworkTeacher) {
        _uiState.update {
            it.copy(
                selectedHomework = hw,
                showDetailSheet = true,
                isLoadingSubmissions = true
            )
        }
        loadSubmissions(hw)
    }

    fun hideDetailSheet() {
        submissionListenerJob?.cancel()
        _uiState.update { it.copy(showDetailSheet = false, selectedHomework = null) }
    }

    private var submissionListenerJob: Job? = null

    // Finding #39 / Phase L1 Pattern A 2026-05-15 — versioned dispatch
    // token. Kotlin's Job.cancel() is non-blocking; the cancelled
    // coroutine continues executing until its next suspension point.
    // During rapid loadSubmissions() re-attach (e.g. teacher marks N
    // students in quick succession), an in-flight collect{} from the
    // PRIOR listener can land a final emission BETWEEN the cancel
    // signal and the NEW listener attaching — stale data overwrites
    // the freshly-attached state. Incrementing this counter on every
    // loadSubmissions() entry, then checking it on each collect{}
    // emission, lets stale emissions self-discard. Constant-time guard.
    private val submissionListenerVersion = java.util.concurrent.atomic.AtomicLong(0)

    private fun loadSubmissions(hw: HomeworkTeacher) {
        val classSection = _uiState.value.selectedClass ?: return

        submissionListenerJob?.cancel()
        val myVersion = submissionListenerVersion.incrementAndGet()

        submissionListenerJob = viewModelScope.launch {
            try {
                // Roster + teacherMarks loaded once per detail-sheet open.
                // These change rarely compared to submissions and don't need
                // a live listener.
                val students = studentRepository.getStudentsForClass(
                    classSection.className, classSection.section
                ).getOrNull() ?: emptyList()
                val teacherMarks = homeworkFirestoreRepo.getTeacherMarksForHomework(hw.hwId)
                    .getOrNull() ?: emptyMap()

                // Live submissions — every parent submit / teacher review
                // mutates a doc here, and this listener pushes the change
                // straight into the UI without a manual refresh. submission-
                // Count is NOT mirrored here: the parent app's submit
                // transaction owns the increment to keep it monotonic.
                homeworkFirestoreRepo.observeSubmissions(hw.hwId).collect { docs ->
                    // Finding #39 stale-collector guard: drop emissions
                    // from listener iterations that have been superseded
                    // by a newer loadSubmissions() call. Without this, the
                    // prior collector's final emit (between cancel signal
                    // and coroutine teardown) can overwrite the newer
                    // listener's initial state with stale data.
                    if (submissionListenerVersion.get() != myVersion) return@collect

                    val submissions = docs.map { doc ->
                        HomeworkStatusEntry(
                            studentId = doc.studentId,
                            studentName = doc.studentName,
                            status = doc.status,
                            remark = doc.remark,
                            text = doc.text,
                            files = doc.files,
                            score = doc.score,
                            maxMarks = doc.maxMarks
                        )
                    }
                    Log.d(TAG, "Submissions live-update: ${submissions.size} entries")
                    _uiState.update {
                        it.copy(
                            students = students,
                            submissions = submissions,
                            teacherMarks = teacherMarks,
                            isLoadingSubmissions = false
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal — sheet dismissed
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load submissions", e)
                _uiState.update { it.copy(isLoadingSubmissions = false) }
            }
        }
    }

    fun markStudentStatus(
        studentId: String,
        studentName: String,
        status: String,
        remark: String = "",
        score: Int = -1
    ) {
        val hw = _uiState.value.selectedHomework ?: return

        viewModelScope.launch {
            try {
                val teacherId = tokenManager.userId.firstOrNull() ?: ""
                val teacherName = tokenManager.userName.firstOrNull() ?: teacherId
                val studentName = _uiState.value.students
                    .firstOrNull { it.studentId == studentId }?.name ?: ""

                // Atomic decide-and-write — see HomeworkFirestoreRepository
                // .reviewOrMark for the race-condition rationale. The
                // submission-vs-teacherMark branch lives inside a Firestore
                // transaction so a parent-app submit landing mid-flight
                // cannot create a duplicate-grade state.
                homeworkFirestoreRepo.reviewOrMark(
                    homeworkId = hw.hwId,
                    studentId  = studentId,
                    studentName = studentName,
                    score      = score,
                    remark     = remark,
                    reviewedBy = teacherName,
                    teacherId  = teacherId,
                    status     = status   // Pass through the teacher's pick
                ).fold(
                    onSuccess = {
                        // Finding #41 / Phase L1 Pattern B 2026-05-15 —
                        // removed redundant loadHomework() refresh after
                        // mark. Rationale: reviewOrMark mutates only
                        // /submissions and /teacherMarks docs; the parent
                        // homework doc's denormalized fields
                        // (submissionCount, totalStudents) are NOT touched
                        // by teacher marking. The submission listener
                        // restart below picks up the actual state change.
                        // Removing loadHomework() also eliminates the
                        // list-vs-detail interleaving glitch where two
                        // independent async fetches raced to update
                        // separate state slices.
                        loadSubmissions(hw)
                    },
                    onFailure = { e ->
                        _events.emit(HomeworkEvent.Error(e.message ?: "Failed to save mark"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(HomeworkEvent.Error(e.message ?: "Failed to update status"))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadHomework()
    }
}
