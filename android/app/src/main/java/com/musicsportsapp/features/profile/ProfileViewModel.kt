package com.musicsportsapp.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.remote.dto.UserDto
import com.musicsportsapp.data.repository.AuthRepository
import com.musicsportsapp.features.music.playback.AudioCacheProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val audioCacheProvider: AudioCacheProvider,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.cachedUser.collect { cached ->
                if (cached != null) {
                    _user.value = cached
                }
            }
        }
        loadUserProfile()
        loadCacheSize()
    }

    fun loadCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            audioCacheProvider.updateCacheSize()
            _cacheSizeBytes.value = audioCacheProvider.getCacheSizeBytes()
        }
    }

    fun clearCache(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = audioCacheProvider.clearCache()
            loadCacheSize()
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            when (val result = authRepository.getCurrentUser()) {
                is AppResult.Success -> {
                    _user.value = result.data
                }
                is AppResult.Error -> {
                    // Stays guest or unauthenticated
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _user.value = null
        }
    }
}

