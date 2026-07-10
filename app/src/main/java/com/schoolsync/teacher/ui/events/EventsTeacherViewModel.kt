package com.schoolsync.teacher.ui.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.GalleryAlbum
import com.schoolsync.teacher.data.model.firestore.EventDoc
import com.schoolsync.teacher.util.AttachmentUrlValidator
import com.schoolsync.teacher.data.repository.firestore.EventsFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.GalleryFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val events: List<EventDoc> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EventsTeacherViewModel @Inject constructor(
    private val repo: EventsFirestoreRepository,
    private val galleryRepo: GalleryFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EventsTeacherVM"
    }

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getEvents().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(events = injectAlbumCovers(list), isLoading = false) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load events", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun refresh() = loadEvents()

    /**
     * Many events carry their photos in the linked gallery album (source=
     * "event") rather than on the event doc's own `mediaUrls` (e.g. "Annual
     * sport day" has an empty mediaUrls but its album holds the picture). For
     * any such event, borrow the album's `coverImage` so the list card / detail
     * dialog still show a cover. One extra album query, only when needed.
     */
    private suspend fun injectAlbumCovers(events: List<EventDoc>): List<EventDoc> {
        // "Usable cover" = a media url that passes the attachment allowlist.
        // Gating on this (not just isEmpty) means events whose mediaUrls hold
        // only invalid/unshowable entries still borrow the album cover.
        fun hasUsableCover(e: EventDoc) = e.mediaUrls.any {
            AttachmentUrlValidator.validate(it) is AttachmentUrlValidator.Result.Valid
        }
        if (events.all { hasUsableCover(it) }) return events
        val albums = galleryRepo.getAlbums().getOrNull().orEmpty()
            .filter { it.source == "event" && it.coverImage.isNotBlank() }
        if (albums.isEmpty()) return events
        return events.map { evt ->
            if (hasUsableCover(evt)) evt
            else {
                // Album stores the RAW event id ("EVT0001"); evt.id is the full
                // "{schoolId}_{EVT...}" doc id.
                val cover = albums.firstOrNull { a ->
                    evt.id == a.eventId || evt.id.endsWith("_${a.eventId}")
                }?.coverImage
                if (cover != null) evt.copy(mediaUrls = listOf(cover)) else evt
            }
        }
    }

    /**
     * Look up the admin-generated gallery album for an event (source="event",
     * eventId == the event's id), or null if the event has no photo album.
     * Powers the Events detail "View Photos" jump.
     */
    suspend fun getEventAlbum(eventId: String): GalleryAlbum? =
        galleryRepo.getEventAlbum(eventId).getOrNull()
}
