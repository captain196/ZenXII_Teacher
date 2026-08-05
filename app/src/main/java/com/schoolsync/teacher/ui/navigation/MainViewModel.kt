package com.schoolsync.teacher.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.repository.AuthRepository
import com.schoolsync.teacher.data.repository.firestore.CapabilityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    capabilityRepository: CapabilityRepository
) : ViewModel() {

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent = _logoutEvent.asSharedFlow()

    /**
     * Live staff capabilities driving nav/tile visibility. UNKNOWN until the
     * server staffCapabilities doc loads → the UI fails OPEN (shows everything),
     * so nothing is hidden before the RBAC rollout populates it.
     */
    val capabilities = capabilityRepository.capabilities

    /** Teacher display name for sidebar profile section. */
    val teacherName = tokenManager.userName
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** School display name for sidebar branding. */
    val schoolName = tokenManager.schoolDisplayName
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Profile picture URL (may be empty). */
    val profilePic = tokenManager.profilePic
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Display label: designation if set, else position. */
    val position = tokenManager.designation.combine(tokenManager.position) { desig, pos ->
        desig?.takeIf { it.isNotBlank() } ?: pos ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _logoutEvent.emit(Unit)
        }
    }
}
