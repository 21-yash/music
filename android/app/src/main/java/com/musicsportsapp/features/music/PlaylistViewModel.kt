package com.musicsportsapp.features.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.features.music.data.repository.MusicRepository
import com.musicsportsapp.features.music.domain.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        if (playlistId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getPlaylist(playlistId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, playlist = result.data) }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                }
            }
        }
    }
}
