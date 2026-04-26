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

data class LoginUiState(
    val userId: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPasswordVisible: Boolean = false
)

sealed class LoginEvent {
    data object LoginSuccess : LoginEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events = _events.asSharedFlow()

    fun onUserIdChange(value: String) {
        _uiState.update { it.copy(userId = value.trim(), error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun login() {
        val state = _uiState.value
        if (state.userId.isBlank()) {
            _uiState.update { it.copy(error = "Teacher ID is required") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "Password is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
                        _uiState.update { it.copy(isLoading = false) }
                        _events.emit(LoginEvent.LoginSuccess)
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = throwable.message ?: "Login failed"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Login error", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "An error occurred")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
