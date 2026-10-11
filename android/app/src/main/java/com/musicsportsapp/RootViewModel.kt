package com.musicsportsapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.remote.dto.UserDto
import com.musicsportsapp.data.repository.AuthRepository
import com.musicsportsapp.data.repository.LikedSongsRepository
import com.musicsportsapp.features.music.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    val playerManager: MusicPlayerManager,
    private val likedSongsRepository: LikedSongsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val playbackState = playerManager.playbackState
    val isLoggedIn = authRepository.isLoggedIn

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    loadUserProfile()
                } else {
                    _user.value = null
                }
            }
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            when (val result = authRepository.getCurrentUser()) {
                is AppResult.Success -> {
                    _user.value = result.data
                }
                is AppResult.Error -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _user.value = null
        }
    }

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
    }

    fun seekToRatio(ratio: Float) {
        playerManager.seekToRatio(ratio)
    }

    fun isSongLiked(songId: String?): Flow<Boolean> {
        if (songId == null) return flowOf(false)
        return likedSongsRepository.isSongLiked(songId)
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            likedSongsRepository.toggleLike(song)
        }
    }
}
