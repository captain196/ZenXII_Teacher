package com.schoolsync.teacher.ui.notices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.teacher.util.toDateOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class NoticeItem(
    val noticeId: String,
    val title: String,
    val body: String,
    val bodyHtml: String = "",      // Rich HTML description when present; rendered in WebView on detail
    val author: String = "",
    val authorRole: String = "",    // e.g. "Admin", "HR Manager" — shown next to author name
    val date: String = "",
    val category: String = "General",
    val isRead: Boolean = false,
    val attachmentUrl: String = ""
)

data class NoticesUiState(
    val notices: List<NoticeItem> = emptyList(),
    val filteredNotices: List<NoticeItem> = emptyList(),
    val selectedCategory: String = "All",
    val categories: List<String> = listOf("All", "General", "Academic", "Event", "Holiday", "Exam"),
    val selectedNotice: NoticeItem? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NoticesViewModel @Inject constructor(
    private val communicationFirestoreRepo: CommunicationFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "NoticesVM"
    }

    private val _uiState = MutableStateFlow(NoticesUiState())
    val uiState: StateFlow<NoticesUiState> = _uiState.asStateFlow()

    init {
        loadNotices()
    }

    fun loadNotices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                communicationFirestoreRepo.getCirculars().fold(
                    onSuccess = { circulars ->
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        val noticeItems = circulars.map { c ->
                            NoticeItem(
                                noticeId = c.id,
                                title = c.title,
                                body = c.body,
                                // Only carry HTML if the description actually has markup AND
                                // differs from the plain-text body (avoids WebView for plain notices)
                                bodyHtml = c.description
                                    .takeIf { it.contains('<') && it != c.body }
                                    .orEmpty(),
                                author = c.author,
                                authorRole = c.authorRole,
                                date = c.sentAt.toDateOrNull()?.let { dateFormat.format(it) } ?: "",
                                category = c.category.ifBlank { "General" },
                                attachmentUrl = c.attachmentUrl
                            )
                        }

                        _uiState.update {
                            it.copy(
                                notices = noticeItems,
                                filteredNotices = filterByCategory(noticeItems, it.selectedCategory),
                                isLoading = false
                            )
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load notices", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun filterByCategory(notices: List<NoticeItem>, category: String): List<NoticeItem> {
        return if (category == "All") notices
        else notices.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "All") {
                state.notices
            } else {
                state.notices.filter { it.category.equals(category, ignoreCase = true) }
            }
            state.copy(selectedCategory = category, filteredNotices = filtered)
        }
    }

    fun selectNotice(notice: NoticeItem?) {
        _uiState.update { it.copy(selectedNotice = notice) }
    }

    fun refresh() {
        loadNotices()
    }
}
