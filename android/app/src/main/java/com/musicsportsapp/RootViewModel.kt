package com.musicsportsapp

import androidx.lifecycle.ViewModel
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class RootViewModel @Inject constructor(
    val playerManager: MusicPlayerManager
) : ViewModel() {
    val playbackState = playerManager.playbackState

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }
}
