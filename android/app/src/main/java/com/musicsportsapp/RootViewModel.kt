package com.musicsportsapp

import androidx.lifecycle.ViewModel
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val playerManager: MusicPlayerManager
) : ViewModel() {
    val playbackState = playerManager.playbackState

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }
}
