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
    private val likedSongsRepository: com.musicsportsapp.data.repository.LikedSongsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        if (playlistId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 0. Handle Liked Songs special playlist
            if (playlistId.equals("liked", ignoreCase = true) || playlistId.equals("liked_songs", ignoreCase = true)) {
                likedSongsRepository.likedSongs.collect { likedList ->
                    val songs = likedList.map { entity ->
                        Song(
                            id = entity.songId,
                            title = entity.title,
                            artists = listOf(ArtistCredit(id = entity.artist, name = entity.artist, imageUrl = null)),
                            album = null,
                            durationSeconds = entity.durationSeconds,
                            imageUrl = entity.imageUrl,
                            year = "",
                            language = "",
                            hasLyrics = false,
                            playCount = 0,
                            label = "",
                            streamRef = entity.songId,
                            providerId = entity.providerId,
                            addedAt = entity.likedAt
                        )
                    }
                    val domainPlaylist = Playlist(
                        id = "liked",
                        title = "Liked Songs",
                        description = "Your favorite saved tracks",
                        imageUrl = songs.firstOrNull()?.imageUrl,
                        songCount = songs.size,
                        followerCount = 0,
                        songs = songs,
                        providerId = "liked"
                    )
                    _uiState.update { it.copy(isLoading = false, playlist = domainPlaylist) }
                }
                return@launch
            }

            // 1. First attempt to load as a user custom playlist
            when (val customResult = playlistRepository.loadPlaylistDetails(playlistId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, playlist = mapDtoToPlaylist(customResult.data)) }
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
                                    providerId = s.providerId,
                                    addedAt = s.addedAt
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

    fun updatePlaylistDetails(
        name: String,
        description: String,
        imageUrl: String?,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val currentPlaylist = _uiState.value.playlist ?: return
        viewModelScope.launch {
            when (val result = playlistRepository.updatePlaylist(
                playlistId = currentPlaylist.id,
                name = name.ifBlank { currentPlaylist.title },
                description = description,
                imageUrl = imageUrl?.ifBlank { null }
            )) {
                is AppResult.Success -> {
                    val dto = result.data
                    val updated = currentPlaylist.copy(
                        title = dto.name,
                        description = dto.description,
                        imageUrl = dto.imageUrl
                    )
                    _uiState.update { it.copy(playlist = updated) }
                    onComplete(true)
                }
                is AppResult.Error -> onComplete(false)
            }
        }
    }

    fun removeSong(songId: String, onComplete: (Boolean) -> Unit = {}) {
        val currentPlaylist = _uiState.value.playlist ?: return
        if (currentPlaylist.id.equals("liked", ignoreCase = true)) {
            val song = currentPlaylist.songs.find { it.id == songId }
            viewModelScope.launch {
                likedSongsRepository.unlikeSong(songId, song?.providerId ?: "saavn")
                onComplete(true)
            }
            return
        }
        // Optimistic UI update
        val updatedSongs = currentPlaylist.songs.filterNot { it.id == songId }
        _uiState.update { 
            it.copy(playlist = currentPlaylist.copy(songs = updatedSongs, songCount = updatedSongs.size)) 
        }
        viewModelScope.launch {
            when (playlistRepository.removeSongFromPlaylist(currentPlaylist.id, songId)) {
                is AppResult.Success -> onComplete(true)
                is AppResult.Error -> {
                    loadPlaylist(currentPlaylist.id)
                    onComplete(false)
                }
            }
        }
    }

    fun savePlaylistChanges(
        playlistId: String,
        removedSongIds: List<String>,
        remainingSongIds: List<String>,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            for (songId in removedSongIds) {
                playlistRepository.removeSongFromPlaylist(playlistId, songId)
            }
            when (val result = playlistRepository.reorderPlaylistSongs(playlistId, remainingSongIds)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(playlist = mapDtoToPlaylist(result.data)) }
                    onComplete(true)
                }
                is AppResult.Error -> {
                    loadPlaylist(playlistId)
                    onComplete(false)
                }
            }
        }
    }

    private fun mapDtoToPlaylist(dto: com.musicsportsapp.data.remote.dto.PlaylistDto): Playlist {
        return Playlist(
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
                    providerId = s.providerId,
                    addedAt = parseIsoDate(s.addedAt)
                )
            },
            providerId = "custom"
        )
    }

    private fun parseIsoDate(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
