package com.schoolsync.teacher.ui.lessonplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.CurriculumTopicDoc
import com.schoolsync.teacher.data.repository.firestore.LessonPlanConflictException
import com.schoolsync.teacher.data.repository.firestore.LessonPlanFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.LessonPlanFirestoreRepository.SaveLessonPlanInput
import com.schoolsync.teacher.data.repository.firestore.LessonPlanFirestoreRepository.SlotWithPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** UI state for the daily lesson planner screen. */
data class LessonPlanUiState(
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val dayLabel: String = SimpleDateFormat("EEEE, d MMM", Locale.US).format(Date()),
    val rows: List<SlotWithPlan> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,

    // Edit sheet state
    val editing: SlotWithPlan? = null,
    val editingTopics: List<CurriculumTopicDoc> = emptyList(),
    val isEditingTopicsLoading: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface LessonPlanEvent {
    data class Saved(val message: String = "Saved") : LessonPlanEvent
    data class Error(val message: String) : LessonPlanEvent
    data object Conflict : LessonPlanEvent       // 409-equivalent — view auto-reloads
}

@HiltViewModel
class LessonPlanViewModel @Inject constructor(
    private val repo: LessonPlanFirestoreRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LessonPlanUiState())
    val ui: StateFlow<LessonPlanUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<LessonPlanEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LessonPlanEvent> = _events.asSharedFlow()

    init {
        load()
    }

    /** Refresh the day's slot+plan list. */
    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            val date = _ui.value.date
            repo.getMyDailyPlan(date).fold(
                onSuccess = { rows ->
                    _ui.update {
                        it.copy(
                            rows = rows,
                            isLoading = false,
                            dayLabel = SimpleDateFormat("EEEE, d MMM", Locale.US)
                                .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: Date())
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
                }
            )
        }
    }

    /** Move to a different date (used by an external date picker). */
    fun setDate(iso: String) {
        if (iso == _ui.value.date) return
        _ui.update { it.copy(date = iso) }
        load()
    }

    // ── Quick actions ───────────────────────────────────────────────────

    fun markCompleted(slot: SlotWithPlan) = quickStatus(slot, "completed")
    fun markSkipped  (slot: SlotWithPlan) = quickStatus(slot, "skipped")

    private fun quickStatus(slot: SlotWithPlan, status: String) {
        viewModelScope.launch {
            val plan = slot.plan
            val input = SaveLessonPlanInput(
                className = slot.entry.className,
                section = slot.entry.section,
                subject = slot.entry.subject,
                date = _ui.value.date,
                periodIndex = slot.entry.periodNumber - 1,
                status = status,
                topicId = plan?.topicId.orEmpty(),
                topicTitle = plan?.topicTitle.orEmpty(),
                notes = plan?.notes.orEmpty(),
                rescheduledTo = if (status == "rescheduled") plan?.rescheduledTo.orEmpty() else "",
                expectedVersion = plan?.version
            )
            performSave(input)
        }
    }

    // ── Edit sheet ──────────────────────────────────────────────────────

    fun openEdit(slot: SlotWithPlan) {
        _ui.update { it.copy(editing = slot, editingTopics = emptyList(), isEditingTopicsLoading = true) }
        viewModelScope.launch {
            val topics = repo.getTopicsForSubject(
                className = slot.entry.className,
                section = slot.entry.section,
                subject = slot.entry.subject
            ).getOrDefault(emptyList())
            _ui.update { it.copy(editingTopics = topics, isEditingTopicsLoading = false) }
        }
    }

    fun closeEdit() {
        _ui.update { it.copy(editing = null, editingTopics = emptyList()) }
    }

    fun saveEdit(
        topicId: String,
        topicTitle: String,
        notes: String,
        status: String,
        rescheduledTo: String,
    ) {
        val slot = _ui.value.editing ?: return
        val input = SaveLessonPlanInput(
            className = slot.entry.className,
            section = slot.entry.section,
            subject = slot.entry.subject,
            date = _ui.value.date,
            periodIndex = slot.entry.periodNumber - 1,
            status = status,
            topicId = topicId,
            topicTitle = topicTitle,
            notes = notes,
            rescheduledTo = rescheduledTo,
            expectedVersion = slot.plan?.version
        )
        viewModelScope.launch { performSave(input, closeAfter = true) }
    }

    // ── Save core ───────────────────────────────────────────────────────

    private suspend fun performSave(input: SaveLessonPlanInput, closeAfter: Boolean = false) {
        _ui.update { it.copy(isSaving = true) }
        val result = repo.saveLessonPlan(input)
        _ui.update { it.copy(isSaving = false) }

        result.fold(
            onSuccess = {
                _events.emit(LessonPlanEvent.Saved())
                if (closeAfter) closeEdit()
                load()  // auto-refresh — same UX as admin Phase 6B
            },
            onFailure = { e ->
                if (e is LessonPlanConflictException) {
                    _events.emit(LessonPlanEvent.Conflict)
                    if (closeAfter) closeEdit()
                    load()
                } else {
                    _events.emit(LessonPlanEvent.Error(e.message ?: "Save failed"))
                }
            }
        )
    }
}
