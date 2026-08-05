package com.schoolsync.teacher.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.firestore.StaffDoc
import com.schoolsync.teacher.data.model.firestore.StaffPrivateDoc
import com.schoolsync.teacher.data.repository.AuthRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.StaffFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val staff: StaffDoc? = null,
    // Owner-only sensitive PII (audit fix C1). Null pre-migration / on denial;
    // the screen then falls back to the inline staff-doc values.
    val staffPrivate: StaffPrivateDoc? = null,
    val assignments: List<ClassAssignment> = emptyList(),
    val classTeacherSections: List<String> = emptyList(),
    val error: String? = null,
    val cachedName: String = "",
    val cachedEmail: String = "",
    val cachedPhone: String = "",
    val cachedPosition: String = "",
    val cachedDepartment: String = "",
    val cachedSchoolName: String = "",
    val cachedProfilePic: String = "",
    // Logout flow (mirrors Parent app): confirm dialog → loading → success.
    val showLogoutDialog: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutSuccess: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val staffRepo: StaffFirestoreRepository,
    private val teacherRepo: TeacherRepository,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init { loadProfile() }

    fun refresh() = loadProfile()

    // ── Logout ────────────────────────────────────────────────────────────
    // Same UX as the Parent app: tapping "Log out" opens a confirm dialog;
    // confirming shows an in-button spinner while the session is cleared, then
    // logoutSuccess flips so the screen can navigate back to Login.

    fun setShowLogoutDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLogoutDialog = show)
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            val result = authRepository.logout()
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    isLoggingOut = false,
                    logoutSuccess = true,
                    showLogoutDialog = false
                )
            } else {
                _uiState.value.copy(
                    isLoggingOut = false,
                    error = result.exceptionOrNull()?.message ?: "Logout failed"
                )
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Cached fields (instant)
            val name = tokenManager.userName.firstOrNull() ?: ""
            val position = tokenManager.position.firstOrNull() ?: ""
            val department = tokenManager.department.firstOrNull() ?: ""
            val schoolName = tokenManager.schoolDisplayName.firstOrNull() ?: ""
            val profilePic = tokenManager.profilePic.firstOrNull() ?: ""

            _uiState.value = _uiState.value.copy(
                cachedName = name, cachedPosition = position,
                cachedDepartment = department, cachedSchoolName = schoolName,
                cachedProfilePic = profilePic
            )

            val schoolCode = tokenManager.schoolCode.firstOrNull() ?: ""
            val userId = tokenManager.userId.firstOrNull() ?: ""
            if (schoolCode.isEmpty() || userId.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Not logged in")
                return@launch
            }

            // Fetch staff doc + assignments in parallel
            val docId = "${schoolCode}_${userId}"
            val staffResult = staffRepo.getStaff(docId)
            val assignResult = teacherRepo.getAssignedClasses()
            // Owner-only sensitive PII, same doc id (audit fix C1). Fail-soft:
            // returns null pre-migration / on permission denial → the screen
            // falls back to the inline staff-doc values.
            val staffPrivate = staffRepo.getStaffPrivate(docId)

            val doc = staffResult.getOrNull()
            val assignments = assignResult.getOrDefault(emptyList())

            val ctSections = assignments
                .filter { it.classTeacher }
                .map { "${it.className} — ${it.section}" }
                .distinct()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                staff = doc,
                staffPrivate = staffPrivate,
                assignments = assignments,
                classTeacherSections = ctSections,
                cachedName = doc?.name?.ifEmpty { name } ?: name,
                cachedEmail = doc?.email ?: "",
                cachedPhone = doc?.phone ?: "",
                cachedPosition = doc?.designation?.ifEmpty { doc.position }?.ifEmpty { position } ?: position,
                cachedDepartment = doc?.department?.ifEmpty { department } ?: department,
                cachedProfilePic = doc?.profilePic?.ifEmpty { profilePic } ?: profilePic,
                error = if (staffResult.isFailure) "Profile: ${staffResult.exceptionOrNull()?.message}" else null
            )
        }
    }
}
