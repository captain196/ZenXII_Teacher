package com.schoolsync.teacher.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.repository.AuthRepository
import com.schoolsync.teacher.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.friendlyErrorMessage
import androidx.annotation.StringRes
import com.schoolsync.teacher.util.friendlyErrorRes

data class LoginUiState(
    val userId: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    // @StringRes, not String: a resolved message held here survives
    // recreate() and so survives a language change, leaving the error stranded
    // in the previous language while the rest of the screen switches.
    @StringRes val errorRes: Int? = null,
    val isPasswordVisible: Boolean = false
)

sealed class LoginEvent {
    data object LoginSuccess : LoginEvent()
    data object LoginRequiresPasswordChange : LoginEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events = _events.asSharedFlow()

    fun onUserIdChange(value: String) {
        _uiState.update { it.copy(userId = value.trim(), errorRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorRes = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun login() {
        val state = _uiState.value
        if (state.userId.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.vm_teacher_id_required) }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.vm_password_required) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorRes = null) }
            try {
                // Resolve or generate a deviceId
                val deviceId = tokenManager.deviceId.firstOrNull()
                    ?: UUID.randomUUID().toString()

                val result = authRepository.login(
                    userId = state.userId,
                    password = state.password,
                    deviceId = deviceId
                )
                result.fold(
                    onSuccess = {
                        // Reinstall / new-device restore: adopt staff.prefLang
                        // when this device has no explicit choice of its own.
                        // Awaited (not fire-and-forget) so the language is
                        // settled before the first post-login screen renders.
                        authRepository.restoreLanguageFromServer()
                        _uiState.update { it.copy(isLoading = false) }
                        // Force-change-password gate: if the admin reset this
                        // user's password, AuthRepository.login cached the flag
                        // in TokenManager. Route accordingly.
                        val mustChange = tokenManager.mustChangePassword.firstOrNull() ?: false
                        _events.emit(
                            if (mustChange) LoginEvent.LoginRequiresPasswordChange
                            else LoginEvent.LoginSuccess
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorRes = friendlyErrorRes(throwable) ?: R.string.vm_login_failed
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Login error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorRes = friendlyErrorRes(e) ?: R.string.vm_error_occurred
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorRes = null) }
    }
}
