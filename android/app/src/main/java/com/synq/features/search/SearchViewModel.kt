package com.synq.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.core.domain.AppResult
import com.synq.features.music.data.repository.MusicRepository
import com.synq.features.music.domain.model.SearchResults
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val provider: String = "jiosaavn", // "jiosaavn" or "youtube"
    val results: SearchResults? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val recentSearchManager: RecentSearchManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecentSearches()
    }

    private fun loadRecentSearches() {
        _uiState.update { it.copy(recentSearches = recentSearchManager.getRecentSearches()) }
    }

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        triggerSearch(newQuery, _uiState.value.provider, debounceMs = 500)
    }

    fun onRecentSearchClicked(recentQuery: String) {
        _uiState.update { it.copy(query = recentQuery) }
        triggerSearch(recentQuery, _uiState.value.provider, debounceMs = 0)
    }

    fun removeRecentSearch(query: String) {
        val updated = recentSearchManager.removeSearch(query)
        _uiState.update { it.copy(recentSearches = updated) }
    }

    fun clearRecentSearches() {
        val updated = recentSearchManager.clearAll()
        _uiState.update { it.copy(recentSearches = updated) }
    }

    fun updateProvider(newProvider: String) {
        _uiState.update { it.copy(provider = newProvider) }
        triggerSearch(_uiState.value.query, newProvider, debounceMs = 150)
    }

    private fun triggerSearch(query: String, provider: String, debounceMs: Long = 500) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _uiState.update { it.copy(results = null, isLoading = false, error = null) }
            return
        }

        searchJob = viewModelScope.launch {
            try {
                if (debounceMs > 0) {
                    delay(debounceMs)
                }
                
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                when (val result = repository.search(query, provider)) {
                    is AppResult.Success -> {
                        // Record in local recent searches when search succeeds
                        val updatedRecent = recentSearchManager.addSearch(query)
                        _uiState.update { 
                            it.copy(
                                results = result.data, 
                                isLoading = false,
                                recentSearches = updatedRecent,
                                error = null
                            ) 
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = result.error.message 
                            ) 
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled due to query change or back navigation — ignore
            }
        }
    }
    
    fun playSong(song: Song) {
        playerManager.playSong(song)
    }

    fun playNext(song: Song) {
        playerManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }
}
