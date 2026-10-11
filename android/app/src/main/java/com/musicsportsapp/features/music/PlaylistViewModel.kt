package com.musicsportsapp.features.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.repository.PlaylistRepository
import com.musicsportsapp.features.music.data.repository.MusicRepository
import com.musicsportsapp.features.music.domain.model.ArtistCredit
import com.musicsportsapp.features.music.domain.model.Playlist
import com.musicsportsapp.features.music.domain.model.Song
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
    private val repository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        if (playlistId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. First attempt to load as a user custom playlist
            when (val customResult = playlistRepository.loadPlaylistDetails(playlistId)) {
                is AppResult.Success -> {
                    val dto = customResult.data
                    val domainPlaylist = Playlist(
                        id = dto.id,
                        title = dto.name,
                        description = dto.description,
                        imageUrl = dto.imageUrl,
                        songCount = dto.songs.size,
                        followerCount = 0,
                        songs = dto.songs.map { s ->
                            Song(
                                id = s.songId,
                                title = s.title,
                                artists = listOf(ArtistCredit(id = s.artist, name = s.artist, imageUrl = null)),
                                album = null,
                                durationSeconds = s.duration,
                                imageUrl = s.imageUrl,
                                year = "",
                                language = "",
                                hasLyrics = false,
                                playCount = 0,
                                label = "",
                                streamRef = s.songId,
                                providerId = s.providerId
                            )
                        },
                        providerId = "custom"
                    )
                    _uiState.update { it.copy(isLoading = false, playlist = domainPlaylist) }
                    return@launch
                }
                is AppResult.Error -> {
                    // Check local Room database in case user is offline
                    val localEntity = playlistRepository.getLocalPlaylist(playlistId)
                    if (localEntity != null) {
                        val localSongs = playlistRepository.getLocalPlaylistSongs(playlistId)
                        val domainPlaylist = Playlist(
                            id = localEntity.serverId,
                            title = localEntity.name,
                            description = localEntity.description ?: "",
                            imageUrl = localEntity.imageUrl,
                            songCount = localEntity.songCount,
                            followerCount = 0,
                            songs = localSongs.map { s ->
                                Song(
                                    id = s.songId,
                                    title = s.title,
                                    artists = listOf(ArtistCredit(id = s.artist, name = s.artist, imageUrl = null)),
                                    album = null,
                                    durationSeconds = s.durationSeconds,
                                    imageUrl = s.imageUrl,
                                    year = "",
                                    language = "",
                                    hasLyrics = false,
                                    playCount = 0,
                                    label = "",
                                    streamRef = s.songId,
                                    providerId = s.providerId
                                )
                            },
                            providerId = "custom"
                        )
                        _uiState.update { it.copy(isLoading = false, playlist = domainPlaylist) }
                        return@launch
                    }
                }
            }

            // 2. Fallback to external/JioSaavn playlist
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
