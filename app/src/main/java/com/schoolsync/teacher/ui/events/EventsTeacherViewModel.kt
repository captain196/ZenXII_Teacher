package com.schoolsync.teacher.ui.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.EventDoc
import com.schoolsync.teacher.data.repository.firestore.EventsFirestoreRepository
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
    private val repo: EventsFirestoreRepository
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
                    _uiState.update { it.copy(events = list, isLoading = false) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load events", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun refresh() = loadEvents()
}
