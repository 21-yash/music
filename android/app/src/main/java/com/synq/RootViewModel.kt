package com.synq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.data.remote.dto.UserDto
import com.synq.data.repository.AuthRepository
import com.synq.data.repository.LikedSongsRepository
import com.synq.data.repository.PlaylistRepository
import com.synq.features.music.domain.model.Artist
import com.synq.features.music.domain.model.Song
import com.synq.features.music.jam.JamSessionManager
import com.synq.features.music.playback.MusicPlayerManager
import com.synq.features.music.sleeptimer.SleepTimerManager
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
    val jamSessionManager: JamSessionManager,
    private val themePreferences: com.synq.data.local.ThemePreferences,
    val jamPreferences: com.synq.features.music.jam.JamPreferences
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
    val jamUiState = jamSessionManager.stateStore.uiState
    val jamRoom = jamSessionManager.stateStore.room
    val jamClientState = jamSessionManager.jamClient.clientState
    val jamLocalPlaybackState = jamSessionManager.localPlaybackState
    val jamUiEvents = jamSessionManager.stateStore.uiEvents
    val jamClosedEvents = jamSessionManager.jamClient.closedEvents
    val jamClockSync = jamSessionManager.clockSync

    fun clearJamError() {
        jamSessionManager.clearError()
    }

    fun retryJamPlayback() {
        jamSessionManager.retryPlayback()
    }

    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            jamSessionManager.leaveJam()
            playerManager.stop()
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

    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme

    fun toggleTheme() {
        themePreferences.toggleTheme()
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    private val _showLeaveJamConfirmDialog = MutableStateFlow(false)
    val showLeaveJamConfirmDialog: StateFlow<Boolean> = _showLeaveJamConfirmDialog.asStateFlow()

    fun dismissLeaveJamConfirmDialog() {
        _showLeaveJamConfirmDialog.value = false
    }

    fun confirmLeaveJamAndStopPlayback() {
        _showLeaveJamConfirmDialog.value = false
        jamSessionManager.leaveJam()
        playerManager.stop()
    }

    fun stopPlayback() {
        if (jamState.value.isInJam) {
            _showLeaveJamConfirmDialog.value = true
        } else {
            playerManager.stop()
        }
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
    fun startJam(avatarUrl: String? = null, onComplete: ((Result<Unit>) -> Unit)? = null) {
        val currUser = _user.value
        val userId = currUser?.id
        if (userId.isNullOrBlank()) {
            val err = "Sign in required to start a Jam"
            jamSessionManager.setError(err)
            onComplete?.invoke(Result.failure(Exception(err)))
            return
        }
        val username = currUser.username
        val userAvatar = avatarUrl ?: jamPreferences.getLastAvatarUrl()
        jamSessionManager.createJam(userId, username, userAvatar) { res ->
            onComplete?.invoke(res.map { })
        }
    }

    fun joinJam(code: String, avatarUrl: String? = null, onComplete: ((Result<Unit>) -> Unit)? = null) {
        val currUser = _user.value
        val userId = currUser?.id
        if (userId.isNullOrBlank()) {
            val err = "Sign in required to join a Jam"
            jamSessionManager.setError(err)
            onComplete?.invoke(Result.failure(Exception(err)))
            return
        }
        val username = currUser.username
        val userAvatar = avatarUrl ?: jamPreferences.getLastAvatarUrl()
        jamSessionManager.joinJam(code, userId, username, userAvatar) { res ->
            onComplete?.invoke(res.map { })
        }
    }

    fun leaveJam() {
        jamSessionManager.leaveJam()
    }

    fun endJam() {
        jamSessionManager.endJam()
    }

    fun stepOutLocally() {
        jamSessionManager.stepOutLocally()
    }

    fun resumeFromLocalStepOut() {
        jamSessionManager.resumeFromLocalStepOut()
    }

    fun toggleJamPlayPause() {
        jamSessionManager.togglePlayPause()
    }

    fun jamSeekTo(positionMs: Long) {
        jamSessionManager.seekTo(positionMs)
    }

    fun jamNextTrack() {
        jamSessionManager.nextTrack()
    }

    fun jamPreviousTrack() {
        jamSessionManager.previousTrack()
    }

    fun addSongToJam(song: Song, playNext: Boolean = false) {
        jamSessionManager.addSongToJam(song, playNext)
    }

    fun removeSongFromJam(index: Int) {
        jamSessionManager.removeSongFromJam(index)
    }

    fun removeByQid(qid: String) {
        jamSessionManager.removeByQid(qid)
    }

    fun playItemByQid(qid: String) {
        jamSessionManager.playItemByQid(qid)
    }

    fun moveByQid(qid: String, toQueueIndex: Int) {
        jamSessionManager.moveByQid(qid, toQueueIndex)
    }

    fun promoteRecommended(qid: String) {
        jamSessionManager.promoteRecommended(qid)
    }

    fun reorderJamQueue(fromIndex: Int, toIndex: Int) {
        jamSessionManager.reorderJamQueue(fromIndex, toIndex)
    }

    fun setAttendeeReorderPermission(allow: Boolean) {
        jamSessionManager.setAttendeeReorderPermission(allow)
    }

    fun setMemberPerms(userId: String, perms: com.synq.features.music.jam.JamPermissions) {
        jamSessionManager.setMemberPerms(userId, perms)
    }

    fun setDefaults(perms: com.synq.features.music.jam.JamPermissions) {
        jamSessionManager.setDefaults(perms, applyToExisting = true)
    }

    fun kickMember(userId: String) {
        jamSessionManager.kickMember(userId)
    }

    fun transferHost(userId: String) {
        jamSessionManager.transferHost(userId)
    }

    // Playlist Add Modal actions
    fun showAddToPlaylist(song: Song) {
        _songForPlaylistAdd.value = song
        viewModelScope.launch {
            playlistRepository.syncPlaylists()
        }
    }

    fun getPlaylistIdsForSong(songId: String): Flow<List<String>> =
        playlistRepository.getPlaylistIdsForSong(songId)

    fun dismissAddToPlaylist() {
        _songForPlaylistAdd.value = null
    }

    fun addSongToExistingPlaylist(playlistId: String, song: Song, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = playlistRepository.addSongToPlaylist(playlistId, song)) {
                is AppResult.Success -> {
                    onComplete?.invoke(true, "Added to playlist")
                }
                is AppResult.Error -> {
                    onComplete?.invoke(false, result.error.message ?: "Failed to add song")
                }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = playlistRepository.removeSongFromPlaylist(playlistId, songId)) {
                is AppResult.Success -> {
                    onComplete?.invoke(true, "Removed from playlist")
                }
                is AppResult.Error -> {
                    onComplete?.invoke(false, result.error.message ?: "Failed to remove song")
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
                        }
                        is AppResult.Error -> {
                            onComplete?.invoke(false, addResult.error.message ?: "Playlist created, but failed to add song")
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
