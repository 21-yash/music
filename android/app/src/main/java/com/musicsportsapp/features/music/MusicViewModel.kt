package com.musicsportsapp.features.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.features.music.data.repository.MusicRepository
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.musicsportsapp.features.music.domain.model.HomeData
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

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val trendingDeferred = async { repository.getTrending() }
            val homeDataDeferred = async { repository.getHomeData() }
            
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

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }
}
