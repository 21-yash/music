package com.musicsportsapp.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.data.local.db.entity.FollowedTeamEntity
import com.musicsportsapp.data.local.db.entity.LikedSongEntity
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.local.db.entity.RecentlyPlayedEntity
import com.musicsportsapp.data.repository.AuthRepository
import com.musicsportsapp.data.repository.FollowedTeamRepository
import com.musicsportsapp.data.repository.LikedSongsRepository
import com.musicsportsapp.data.repository.PlaylistRepository
import com.musicsportsapp.data.repository.RecentlyPlayedRepository
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.domain.model.ArtistCredit
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import com.musicsportsapp.core.domain.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    ALL,
    PLAYLISTS,
    LIKED,
    HISTORY,
    TEAMS,
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val likedSongsRepository: LikedSongsRepository,
    private val playlistRepository: PlaylistRepository,
    private val followedTeamRepository: FollowedTeamRepository,
    private val playerManager: MusicPlayerManager,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recentlyPlayed: StateFlow<List<RecentlyPlayedEntity>> = recentlyPlayedRepository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<LikedSongEntity>> = likedSongsRepository.likedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedCount: StateFlow<Int> = likedSongsRepository.likedSongsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedTeams: StateFlow<List<FollowedTeamEntity>> = followedTeamRepository.followedTeams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(LibraryTab.ALL)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    syncAll()
                }
            }
        }
    }

    fun setTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun setCreatePlaylistDialogVisible(visible: Boolean) {
        _showCreatePlaylistDialog.value = visible
    }

    fun syncAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                likedSongsRepository.syncWithServer()
                playlistRepository.syncPlaylists()
                followedTeamRepository.syncFollowedTeams()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
            _showCreatePlaylistDialog.value = false
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun unfollowTeam(teamId: String) {
        viewModelScope.launch {
            followedTeamRepository.unfollowTeam(teamId)
        }
    }

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

    fun playLikedSong(item: LikedSongEntity) {
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

    fun unlikeSong(songId: String, providerId: String) {
        viewModelScope.launch {
            likedSongsRepository.unlikeSong(songId, providerId)
        }
    }

    fun isSongLiked(songId: String): Flow<Boolean> {
        return likedSongsRepository.isSongLiked(songId)
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
}
