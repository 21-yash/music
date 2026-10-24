package com.musicsportsapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.remote.dto.UserDto
import com.musicsportsapp.data.repository.AuthRepository
import com.musicsportsapp.data.repository.LikedSongsRepository
import com.musicsportsapp.data.repository.PlaylistRepository
import com.musicsportsapp.features.music.domain.model.Artist
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.jam.JamSessionManager
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import com.musicsportsapp.features.music.sleeptimer.SleepTimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val playerManager: MusicPlayerManager,
    private val likedSongsRepository: LikedSongsRepository,
    private val authRepository: AuthRepository,
    private val playlistRepository: PlaylistRepository,
    val sleepTimerManager: SleepTimerManager,
    val jamSessionManager: JamSessionManager
) : ViewModel() {
    val playbackState = playerManager.playbackState
    val queue = playerManager.queue
    val currentIndex = playerManager.currentIndex
    val isRepeatOne = playerManager.isRepeatOne
    val isShuffle = playerManager.isShuffle
    val currentLyrics = playerManager.currentLyrics
    val isLyricsLoading = playerManager.isLyricsLoading
    val recommendedSongIds = playerManager.recommendedSongIds

    val sleepSelected = sleepTimerManager.selectedOption
    val sleepRemainingSeconds = sleepTimerManager.remainingSeconds

    val jamState = jamSessionManager.jamState

    val isLoggedIn = authRepository.isLoggedIn

    val likedSongIds: StateFlow<Set<String>> = likedSongsRepository.likedSongs
        .map { list -> list.map { it.songId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _songForPlaylistAdd = MutableStateFlow<Song?>(null)
    val songForPlaylistAdd: StateFlow<Song?> = _songForPlaylistAdd.asStateFlow()

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.cachedUser.collect { cached ->
                if (cached != null) {
                    _user.value = cached
                }
            }
        }
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

    private val _showSignOutDialog = MutableStateFlow(false)
    val showSignOutDialog: StateFlow<Boolean> = _showSignOutDialog.asStateFlow()

    fun promptSignOut() {
        _showSignOutDialog.value = true
    }

    fun dismissSignOut() {
        _showSignOutDialog.value = false
    }

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    private val _isPlaylistEditing = MutableStateFlow(false)
    val isPlaylistEditing: StateFlow<Boolean> = _isPlaylistEditing.asStateFlow()

    fun setPlaylistEditing(editing: Boolean) {
        _isPlaylistEditing.value = editing
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        playerManager.setQueue(songs, startIndex)
    }

    fun startArtistRadio(artist: Artist) {
        playerManager.startArtistRadio(artist)
    }

    fun seekToRatio(ratio: Float) {
        playerManager.seekToRatio(ratio)
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun playNext(song: Song) {
        playerManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }

    fun playQueueIndex(index: Int) {
        playerManager.playQueueIndex(index)
    }

    fun removeQueueItem(index: Int) {
        playerManager.removeQueueItem(index)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        playerManager.reorderQueue(fromIndex, toIndex)
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun setShuffle(enabled: Boolean) {
        playerManager.setShuffle(enabled)
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

    // Sleep timer
    fun setSleepTimer(option: String) {
        sleepTimerManager.setTimer(option)
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
    }

    // Jam session
    fun startJam() {
        val currUser = _user.value
        val userId = currUser?.id ?: "guest_${System.currentTimeMillis() % 10000}"
        val username = currUser?.username ?: "You"
        jamSessionManager.createJam(userId, username, null)
    }

    fun joinJam(code: String) {
        val currUser = _user.value
        val userId = currUser?.id ?: "guest_${System.currentTimeMillis() % 10000}"
        val username = currUser?.username ?: "Guest"
        jamSessionManager.joinJam(code, userId, username, null)
    }

    fun leaveJam() {
        jamSessionManager.leaveJam()
    }

    fun addSongToJam(song: Song) {
        jamSessionManager.addSongToJam(song)
    }

    fun removeSongFromJam(index: Int) {
        jamSessionManager.removeSongFromJam(index)
    }

    fun reorderJamQueue(fromIndex: Int, toIndex: Int) {
        jamSessionManager.reorderJamQueue(fromIndex, toIndex)
    }

    fun setAttendeeReorderPermission(allow: Boolean) {
        jamSessionManager.setAttendeeReorderPermission(allow)
    }

    // Playlist Add Modal actions
    fun showAddToPlaylist(song: Song) {
        _songForPlaylistAdd.value = song
        viewModelScope.launch {
            playlistRepository.syncPlaylists()
        }
    }

    fun dismissAddToPlaylist() {
        _songForPlaylistAdd.value = null
    }

    fun addSongToExistingPlaylist(playlistId: String, song: Song, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = playlistRepository.addSongToPlaylist(playlistId, song)) {
                is AppResult.Success -> {
                    onComplete?.invoke(true, "Added to playlist")
                    dismissAddToPlaylist()
                }
                is AppResult.Error -> {
                    onComplete?.invoke(false, result.error.message ?: "Failed to add song")
                }
            }
        }
    }

    fun createPlaylistAndAddSong(name: String, song: Song, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            when (val createResult = playlistRepository.createPlaylist(name)) {
                is AppResult.Success -> {
                    val newPlaylistId = createResult.data.id
                    when (val addResult = playlistRepository.addSongToPlaylist(newPlaylistId, song)) {
                        is AppResult.Success -> {
                            onComplete?.invoke(true, "Created \"$name\" and added song")
                            dismissAddToPlaylist()
                        }
                        is AppResult.Error -> {
                            onComplete?.invoke(false, addResult.error.message ?: "Playlist created, but failed to add song")
                            dismissAddToPlaylist()
                        }
                    }
                }
                is AppResult.Error -> {
                    onComplete?.invoke(false, createResult.error.message ?: "Failed to create playlist")
                }
            }
        }
    }
}
