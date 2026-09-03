package com.schoolsync.teacher.ui.notices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.CircularDoc
import com.schoolsync.teacher.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.teacher.util.toDateOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

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
    val categories: List<String> = NoticesViewModel.NOTICE_CATEGORIES,
    val selectedNotice: NoticeItem? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class NoticesViewModel @Inject constructor(
    private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val badgeBus: com.schoolsync.teacher.util.BadgeBus,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "NoticesVM"

        /**
         * Single source of truth for the filter chip set. Must include EVERY
         * category the notice cards can render (see NoticeCard) so no notice
         * category is unreachable by filtering.
         */
        val NOTICE_CATEGORIES = listOf(
            "All", "General", "Academic", "Event", "Holiday", "Exam",
            "Fee", "Policy", "Administrative", "Emergency", "Recruitment"
        )
    }

    private val _uiState = MutableStateFlow(NoticesUiState())
    val uiState: StateFlow<NoticesUiState> = _uiState.asStateFlow()

    /** IDs this teacher has opened (from circularReads), updated optimistically. */
    private var readIds: Set<String> = emptySet()

    /** A deep-link notice id waiting to be auto-selected once the list arrives. */
    private var pendingSelectId: String? = null

    private var observeJob: Job? = null

    init {
        observeNotices()
    }

    /**
     * Collect the repository's real-time circulars+notices stream so the list
     * updates live and re-subscribes on account/school switch. A stream error
     * surfaces via [NoticesUiState.error] while any already-loaded list stays
     * visible (a comms channel must never show a false "all clear").
     */
    private fun observeNotices() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            readIds = runCatching { communicationFirestoreRepo.getReadCircularIds() }
                .getOrDefault(emptySet())
            communicationFirestoreRepo.observeCirculars()
                .catch { e ->
                    Log.e(TAG, "Notices stream failed", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: appContext.localizedString(R.string.vm_notices_refresh_failed)
                        )
                    }
                }
                .collect { circulars ->
                    val items = circulars.map { it.toNoticeItem() }
                    _uiState.update { st ->
                        st.copy(
                            notices = items,
                            filteredNotices = filterByCategory(items, st.selectedCategory),
                            isLoading = false,
                            error = null, // a fresh successful emission clears any stale error
                            // Keep the open notice in sync with the latest data.
                            selectedNotice = st.selectedNotice?.let { sel ->
                                items.find { it.noticeId == sel.noticeId } ?: sel
                            }
                        )
                    }
                    publishBadge(items)
                    applyPendingSelection(items)
                }
        }
    }

    /** Nav-rail unread badge = genuinely unread notices (from read receipts). */
    private fun publishBadge(items: List<NoticeItem>) {
        badgeBus.setCount("notices", items.count { !it.isRead })
    }

    private fun CircularDoc.toNoticeItem(): NoticeItem {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return NoticeItem(
            noticeId = id,
            title = title,
            body = body,
            // Only carry HTML if the description actually has markup AND
            // differs from the plain-text body (avoids WebView for plain notices)
            bodyHtml = description.takeIf { it.contains('<') && it != body }.orEmpty(),
            author = author,
            authorRole = authorRole,
            date = sentAt.toDateOrNull()?.let { dateFormat.format(it) } ?: "",
            category = category.ifBlank { "General" },
            isRead = id in readIds,
            attachmentUrl = attachmentUrl
        )
    }

    private fun filterByCategory(notices: List<NoticeItem>, category: String): List<NoticeItem> {
        return if (category == "All") notices
        else notices.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                filteredNotices = filterByCategory(state.notices, category)
            )
        }
    }

    /**
     * Auto-open a notice arriving from an FCM deep link. If the notice isn't
     * loaded yet, remember the id and apply it on the next stream emission.
     */
    fun openNoticeById(id: String) {
        if (id.isBlank()) return
        val existing = _uiState.value.notices.find { it.matchesDeepLinkId(id) }
        if (existing != null) selectNotice(existing) else pendingSelectId = id
    }

    private fun applyPendingSelection(items: List<NoticeItem>) {
        val id = pendingSelectId ?: return
        val match = items.find { it.matchesDeepLinkId(id) } ?: return
        pendingSelectId = null
        selectNotice(match)
    }

    /**
     * Deep-link ids from push payloads are the RAW notice id (e.g. "NOT0001"),
     * but a NoticeItem.noticeId is the full Firestore doc key
     * ("{schoolId}_NOT0001" for admin-sent notices). Match tolerantly on either
     * side so a tapped push reliably resolves to its notice instead of silently
     * leaving the user on the list.
     */
    private fun NoticeItem.matchesDeepLinkId(pushId: String): Boolean =
        noticeId == pushId || noticeId.endsWith("_$pushId") || pushId.endsWith("_$noticeId")

    fun selectNotice(notice: NoticeItem?) {
        _uiState.update { it.copy(selectedNotice = notice) }
        // Opening a notice marks it read: optimistic local update (clears the
        // unread dot everywhere it appears) + best-effort idempotent receipt.
        val id = notice?.noticeId
        if (id != null && id !in readIds) {
            readIds = readIds + id
            _uiState.update { st ->
                fun mark(list: List<NoticeItem>) = list.map { if (it.noticeId == id) it.copy(isRead = true) else it }
                st.copy(
                    notices = mark(st.notices),
                    filteredNotices = mark(st.filteredNotices),
                    selectedNotice = st.selectedNotice?.let { if (it.noticeId == id) it.copy(isRead = true) else it }
                ).also { publishBadge(it.notices) }
            }
            viewModelScope.launch { communicationFirestoreRepo.markCircularRead(id) }
        }
    }

    fun refresh() {
        // Keep any already-loaded list visible; only show the full-screen spinner
        // on a cold refresh. Restart the live listener (also re-fetches read state).
        _uiState.update { it.copy(isLoading = it.notices.isEmpty(), error = null) }
        observeNotices()
    }
}
