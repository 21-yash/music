package com.synq.features.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.data.repository.LikedSongsRepository
import com.synq.features.music.data.repository.MusicRepository
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongDetailUiState(
    val song: Song? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val likedSongsRepository: LikedSongsRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    fun isSongLiked(songId: String): StateFlow<Boolean> =
        likedSongsRepository.isSongLiked(songId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadSong(songId: String) {
        if (songId.isBlank()) return
        // If already loaded for this song, do not wipe state or re-show spinner
        if (_uiState.value.song?.id == songId && _uiState.value.error == null) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // First check if current song playing matches
            val current = playerManager.playbackState.value.currentSong
            if (current != null && current.id == songId) {
                _uiState.update { it.copy(isLoading = false, song = current) }
                enrichArtistImages(current)
                if (current.album != null && current.year.isNotBlank()) {
                    return@launch
                }
            }

            when (val result = repository.getSong(songId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, song = result.data) }
                    enrichArtistImages(result.data)
                }
                is AppResult.Error -> {
                    // Check again if current song playing matches
                    val fallback = playerManager.playbackState.value.currentSong
                    if (fallback != null && fallback.id == songId) {
                        _uiState.update { it.copy(isLoading = false, song = fallback) }
                        enrichArtistImages(fallback)
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = result.error.message) }
                    }
                }
            }
        }
    }

    private fun enrichArtistImages(song: Song) {
        viewModelScope.launch {
            val updated = kotlinx.coroutines.coroutineScope {
                song.artists.map { artist ->
                    async {
                        if (!artist.imageUrl.isNullOrBlank() && artist.id.isNotBlank()) {
                            artist
                        } else {
                            val key = if (artist.id.isNotBlank()) artist.id else artist.name
                            val res = repository.getArtist(key)
                            if (res is AppResult.Success) {
                                artist.copy(
                                    id = if (res.data.id.isNotBlank()) res.data.id else artist.id,
                                    imageUrl = if (!res.data.imageUrl.isNullOrBlank()) res.data.imageUrl else artist.imageUrl
                                )
                            } else {
                                artist
                            }
                        }
                    }
                }.awaitAll()
            }
            val current = _uiState.value.song
            if (current != null && current.id == song.id) {
                _uiState.update { it.copy(song = current.copy(artists = updated)) }
            }
        }
    }

    fun playSong(song: Song) {
        playerManager.playSong(song)
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            likedSongsRepository.toggleLike(song)
        }
    }

    fun playNext(song: Song) {
        playerManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }
}
