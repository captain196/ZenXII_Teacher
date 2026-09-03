package com.schoolsync.teacher.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

/**
 * Drives the forced password-change screen shown to a teacher whose
 * password was reset by an admin. The screen blocks navigation to the
 * rest of the app until a new password is set.
 *
 * Calls the server endpoint /auth/clear_must_change which uses the
 * Admin SDK to update Firebase Auth and clear the must_change_password
 * custom claim atomically — no Firebase recent-login re-auth required.
 */
data class ForceChangePasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val newVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed class ForceChangePasswordEvent {
    data object Success : ForceChangePasswordEvent()
    data object LoggedOut : ForceChangePasswordEvent()
}

@HiltViewModel
class ForceChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForceChangePasswordUiState())
    val uiState: StateFlow<ForceChangePasswordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ForceChangePasswordEvent>()
    val events = _events.asSharedFlow()

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun toggleNewVisibility() {
        _uiState.update { it.copy(newVisible = !it.newVisible) }
    }

    fun toggleConfirmVisibility() {
        _uiState.update { it.copy(confirmVisible = !it.confirmVisible) }
    }

    fun submit() {
        val state = _uiState.value
        val err = validate(state.newPassword, state.confirmPassword)
        if (err != null) {
            _uiState.update { it.copy(errorMessage = err) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val res = authRepository.clearMustChange(state.newPassword)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.emit(ForceChangePasswordEvent.Success)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = e.message ?: appContext.localizedString(R.string.vm_reset_failed))
                    }
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(ForceChangePasswordEvent.LoggedOut)
        }
    }

    private fun validate(new: String, confirm: String): String? {
        // Mirrors the server-side policy in Auth_api::clear_must_change.
        if (new.length < 8 || new.length > 72) return appContext.localizedString(R.string.vm_pw_length)
        if (!new.any { it.isUpperCase() })       return appContext.localizedString(R.string.vm_pw_upper)
        if (!new.any { it.isLowerCase() })       return appContext.localizedString(R.string.vm_pw_lower)
        if (!new.any { it.isDigit() })           return appContext.localizedString(R.string.vm_pw_digit)
        if (new != confirm)                      return appContext.localizedString(R.string.vm_pw_mismatch)
        return null
    }
}
