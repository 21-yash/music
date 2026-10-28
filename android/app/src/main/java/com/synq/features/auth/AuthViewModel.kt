package com.synq.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.data.remote.dto.LoginRequest
import com.synq.data.remote.dto.RegisterRequest
import com.synq.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isLoginMode = MutableStateFlow(true)
    val isLoginMode: StateFlow<Boolean> = _isLoginMode.asStateFlow()

    private val _identifier = MutableStateFlow("")
    val identifier: StateFlow<String> = _identifier.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _authSuccess = MutableStateFlow(false)
    val authSuccess: StateFlow<Boolean> = _authSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (!loggedIn) {
                    resetState()
                }
            }
        }
    }

    fun resetState() {
        _identifier.value = ""
        _email.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _inviteCode.value = ""
        _errorMessage.value = null
        _authSuccess.value = false
        _isLoading.value = false
        _isLoginMode.value = true
    }

    fun toggleMode() {
        _isLoginMode.value = !_isLoginMode.value
        _errorMessage.value = null
    }

    fun setIdentifier(value: String) {
        _identifier.value = value
        _errorMessage.value = null
    }

    fun setEmail(value: String) {
        _email.value = value
        _errorMessage.value = null
    }

    fun setInviteCode(value: String) {
        _inviteCode.value = value
        _errorMessage.value = null
    }

    fun setPassword(value: String) {
        _password.value = value
        _errorMessage.value = null
    }

    fun setConfirmPassword(value: String) {
        _confirmPassword.value = value
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun submit() {
        val isLogin = _isLoginMode.value
        val id = _identifier.value.trim()
        val pwd = _password.value

        if (isLogin) {
            if (id.isBlank()) {
                _errorMessage.value = "Username or email is required"
                return
            }
            if (pwd.isBlank()) {
                _errorMessage.value = "Password is required"
                return
            }

            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                when (val result = authRepository.login(LoginRequest(login = id, password = pwd))) {
                    is AppResult.Success -> {
                        _authSuccess.value = true
                    }
                    is AppResult.Error -> {
                        _errorMessage.value = result.error.message
                    }
                }
                _isLoading.value = false
            }
        } else {
            val mail = _email.value.trim()
            val confirm = _confirmPassword.value
            val code = _inviteCode.value.trim()

            if (id.length < 3) {
                _errorMessage.value = "Username must be at least 3 characters"
                return
            }
            if (!id.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                _errorMessage.value = "Username can only contain letters, numbers, and underscores"
                return
            }
            if (!mail.contains("@") || !mail.contains(".")) {
                _errorMessage.value = "Valid email address is required"
                return
            }
            if (code.isBlank()) {
                _errorMessage.value = "Invite code is required"
                return
            }
            if (pwd.length < 8) {
                _errorMessage.value = "Password must be at least 8 characters"
                return
            }
            if (pwd != confirm) {
                _errorMessage.value = "Passwords do not match"
                return
            }

            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                val request = RegisterRequest(
                    username = id,
                    email = mail,
                    password = pwd,
                    inviteCode = code,
                )
                when (val result = authRepository.register(request)) {
                    is AppResult.Success -> {
                        _authSuccess.value = true
                    }
                    is AppResult.Error -> {
                        val err = result.error
                        if (err.code == "FORBIDDEN" || err.code == "HTTP_403" || err.message.contains("invite code", ignoreCase = true)) {
                            _errorMessage.value = "Invalid or already used invite code"
                        } else {
                            _errorMessage.value = err.message
                        }
                    }
                }
                _isLoading.value = false
            }
        }
    }
}
