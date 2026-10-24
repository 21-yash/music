package com.synq.features.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.features.music.data.repository.MusicRepository
import com.synq.features.music.domain.model.Artist
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.synq.features.music.domain.model.HomeData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class MusicUiState(
    val trendingSongs: List<Song> = emptyList(),
    val homeData: HomeData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
    val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MusicUiState(
            trendingSongs = repository.getCachedTrending() ?: emptyList(),
            homeData = repository.getCachedHomeData(),
            isLoading = repository.getCachedHomeData() == null
        )
    )
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasData = _uiState.value.homeData != null
            if (!hasData || forceRefresh) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            
            val trendingDeferred = async { repository.getTrending(forceRefresh = forceRefresh) }
            val homeDataDeferred = async { repository.getHomeData(forceRefresh = forceRefresh) }
            
            val trendingResult = trendingDeferred.await()
            val homeDataResult = homeDataDeferred.await()

            var errorStr: String? = null
            var trending = emptyList<Song>()
            var home: HomeData? = null
            
            if (trendingResult is AppResult.Success) trending = trendingResult.data
            else errorStr = (trendingResult as? AppResult.Error)?.error?.message
            
            if (homeDataResult is AppResult.Success) home = homeDataResult.data
            else errorStr = (homeDataResult as? AppResult.Error)?.error?.message ?: errorStr

            _uiState.update { 
                it.copy(
                    trendingSongs = trending, 
                    homeData = home, 
                    isLoading = false,
                    error = errorStr
                )
            }
        }
    }

    fun playSong(song: Song) {
        playerManager.playSong(song)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        playerManager.setQueue(songs, startIndex)
    }

    fun startArtistRadio(artist: Artist) {
        playerManager.startArtistRadio(artist)
    }

    fun playNext(song: Song) {
        playerManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun setShuffle(enabled: Boolean) {
        playerManager.setShuffle(enabled)
    }
}
