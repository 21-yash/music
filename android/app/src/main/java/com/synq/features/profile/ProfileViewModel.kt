package com.synq.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.data.local.SportsPreferences
import com.synq.data.local.ThemePreferences
import com.synq.data.remote.dto.SportsNotificationPrefsDto
import com.synq.data.remote.dto.UpdateNotificationPreferencesRequest
import com.synq.data.remote.dto.UserDto
import com.synq.data.repository.AuthRepository
import com.synq.data.repository.NotificationRepository
import com.synq.features.music.playback.AudioCacheProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val audioCacheProvider: AudioCacheProvider,
    private val themePreferences: ThemePreferences,
    private val sportsPreferences: SportsPreferences,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme
    val isSportsEnabled: StateFlow<Boolean> = sportsPreferences.isSportsEnabled
    val notificationPrefs: StateFlow<SportsNotificationPrefsDto> = notificationRepository.preferences

    fun setDarkTheme(isDark: Boolean) {
        themePreferences.setDarkTheme(isDark)
    }

    fun toggleTheme() {
        themePreferences.toggleTheme()
    }

    fun setSportsEnabled(enabled: Boolean) {
        sportsPreferences.setSportsEnabled(enabled)
        viewModelScope.launch {
            notificationRepository.onSportsToggled(enabled)
        }
    }

    fun toggleSports() {
        setSportsEnabled(!sportsPreferences.isSportsEnabled.value)
    }

    fun updateNotificationPrefs(request: UpdateNotificationPreferencesRequest) {
        viewModelScope.launch {
            notificationRepository.updatePreferences(request)
        }
    }

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
        viewModelScope.launch {
            notificationRepository.syncPreferences()
        }
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
            notificationRepository.unregisterCurrentToken()
            authRepository.logout()
            _user.value = null
        }
    }
}

