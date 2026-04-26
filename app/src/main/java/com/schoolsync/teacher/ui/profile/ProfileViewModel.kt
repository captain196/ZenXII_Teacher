package com.schoolsync.teacher.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.firestore.StaffDoc
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
    val assignments: List<ClassAssignment> = emptyList(),
    val classTeacherSections: List<String> = emptyList(),
    val error: String? = null,
    val cachedName: String = "",
    val cachedEmail: String = "",
    val cachedPhone: String = "",
    val cachedPosition: String = "",
    val cachedDepartment: String = "",
    val cachedSchoolName: String = "",
    val cachedProfilePic: String = ""
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val staffRepo: StaffFirestoreRepository,
    private val teacherRepo: TeacherRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init { loadProfile() }

    fun refresh() = loadProfile()

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

            val doc = staffResult.getOrNull()
            val assignments = assignResult.getOrDefault(emptyList())

            val ctSections = assignments
                .filter { it.classTeacher }
                .map { "${it.className} — ${it.section}" }
                .distinct()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                staff = doc,
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
