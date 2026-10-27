package com.synq.features.music.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.data.local.db.entity.LikedSongEntity
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.data.local.db.entity.RecentlyPlayedEntity
import com.synq.data.repository.AuthRepository
import com.synq.data.repository.LikedSongsRepository
import com.synq.data.repository.PlaylistRepository
import com.synq.data.repository.RecentlyPlayedRepository
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.MusicPlayerManager
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

    fun playNext(song: Song) {
        playerManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
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

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            recentlyPlayedRepository.deleteEntry(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            recentlyPlayedRepository.clearHistory()
        }
    }
}
