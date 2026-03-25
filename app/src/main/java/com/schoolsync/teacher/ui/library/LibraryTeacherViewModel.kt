package com.schoolsync.teacher.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.LibraryBookDoc
import com.schoolsync.teacher.data.model.firestore.LibraryFineDoc
import com.schoolsync.teacher.data.model.firestore.LibraryIssueDoc
import com.schoolsync.teacher.data.repository.firestore.LibraryFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTab: Int = 0,  // 0=Catalog, 1=Issued, 2=Overdue, 3=Fines
    val books: List<LibraryBookDoc> = emptyList(),
    val issuedBooks: List<LibraryIssueDoc> = emptyList(),
    val overdueBooks: List<LibraryIssueDoc> = emptyList(),
    val fines: List<LibraryFineDoc> = emptyList(),
    val searchQuery: String = ""
)

@HiltViewModel
class LibraryTeacherViewModel @Inject constructor(
    private val libraryRepo: LibraryFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryVM"
    }

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    /** Load data for every tab in parallel. */
    private fun loadAllData() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Load books (catalog)
                val booksResult = libraryRepo.getBooks(_uiState.value.searchQuery)
                booksResult.fold(
                    onSuccess = { books ->
                        _uiState.update { it.copy(books = books) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load books", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load books", e)
            }

            try {
                val issuedResult = libraryRepo.getIssuedBooks()
                issuedResult.fold(
                    onSuccess = { issued ->
                        _uiState.update { it.copy(issuedBooks = issued) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load issued books", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load issued books", e)
            }

            try {
                val overdueResult = libraryRepo.getOverdueBooks()
                overdueResult.fold(
                    onSuccess = { overdue ->
                        _uiState.update { it.copy(overdueBooks = overdue) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load overdue books", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load overdue books", e)
            }

            try {
                val finesResult = libraryRepo.getFines()
                finesResult.fold(
                    onSuccess = { fines ->
                        _uiState.update { it.copy(fines = fines) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load fines", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fines", e)
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchBooks()
    }

    private fun searchBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                libraryRepo.getBooks(_uiState.value.searchQuery).fold(
                    onSuccess = { books ->
                        _uiState.update { it.copy(books = books, isLoading = false) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Search failed", e)
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        loadAllData()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
