package com.musicsportsapp.features.music.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.local.db.entity.LikedSongEntity
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.local.db.entity.RecentlyPlayedEntity
import com.musicsportsapp.data.repository.AuthRepository
import com.musicsportsapp.data.repository.LikedSongsRepository
import com.musicsportsapp.data.repository.PlaylistRepository
import com.musicsportsapp.data.repository.RecentlyPlayedRepository
import com.musicsportsapp.features.music.domain.model.ArtistCredit
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListeningHistoryViewModel @Inject constructor(
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val likedSongsRepository: LikedSongsRepository,
    private val playlistRepository: PlaylistRepository,
    private val authRepository: AuthRepository,
    private val playerManager: MusicPlayerManager,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recentlyPlayed: StateFlow<List<RecentlyPlayedEntity>> = recentlyPlayedRepository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<LikedSongEntity>> = likedSongsRepository.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playRecentlyPlayed(item: RecentlyPlayedEntity) {
        val song = Song(
            id = item.songId,
            title = item.title,
            artists = listOf(ArtistCredit(id = item.artist, name = item.artist, imageUrl = null)),
            album = null,
            durationSeconds = item.durationSeconds,
            imageUrl = item.imageUrl,
            year = "",
            language = "",
            hasLyrics = false,
            playCount = 0,
            label = "",
            streamRef = item.songId,
            providerId = item.providerId,
        )
        playerManager.playSong(song)
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            likedSongsRepository.toggleLike(song)
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = playlistRepository.addSongToPlaylist(playlistId, song)
            onComplete?.invoke(result is AppResult.Success)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            recentlyPlayedRepository.clearHistory()
        }
    }
}
