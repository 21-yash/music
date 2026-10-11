package com.musicsportsapp.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.data.local.db.entity.RecentlyPlayedEntity
import com.musicsportsapp.data.repository.RecentlyPlayedRepository
import com.musicsportsapp.features.music.domain.model.ArtistCredit
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    recentlyPlayedRepository: RecentlyPlayedRepository,
    private val playerManager: MusicPlayerManager,
) : ViewModel() {

    val recentlyPlayed: StateFlow<List<RecentlyPlayedEntity>> = recentlyPlayedRepository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playSong(item: RecentlyPlayedEntity) {
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
}
