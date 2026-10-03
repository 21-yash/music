package com.musicsportsapp.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.features.music.data.repository.MusicRepository
import com.musicsportsapp.features.music.domain.model.SearchResults
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.playback.MusicPlayerManager
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
    val results: SearchResults? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchHistory: List<String> = emptyList() // Could be backed by Room/DataStore later
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        
        // Debounce search requests
        searchJob?.cancel()
        
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(results = null, isLoading = false, error = null) }
            return
        }

        searchJob = viewModelScope.launch {
            // Wait 500ms before triggering the API call
            delay(500)
            
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            when (val result = repository.search(newQuery)) {
                is AppResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            results = result.data, 
                            isLoading = false
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
        }
    }
    
    fun playSong(song: Song) {
        playerManager.playSong(song)
    }
}
