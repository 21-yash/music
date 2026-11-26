package com.synq.features.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.data.local.SportsPreferences
import com.synq.features.sports.domain.model.MatchSummary
import com.synq.features.sports.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SportsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFilter: String = "live",
    val matches: List<MatchSummary> = emptyList()
)

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val repository: SportsRepository,
    private val sportsPreferences: SportsPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SportsUiState())
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            sportsPreferences.isSportsEnabled.collect { enabled ->
                if (enabled) {
                    if (_uiState.value.matches.isEmpty()) {
                        fetchMatches(_uiState.value.selectedFilter)
                    }
                } else {
                    fetchJob?.cancel()
                    _uiState.update { it.copy(isLoading = false, error = null, matches = emptyList()) }
                }
            }
        }
    }

    fun onFilterSelected(filter: String) {
        if (_uiState.value.selectedFilter == filter) return
        
        _uiState.update { it.copy(selectedFilter = filter) }
        fetchMatches(filter)
    }

    fun retry() {
        fetchMatches(_uiState.value.selectedFilter)
    }

    private fun fetchMatches(filter: String) {
        if (!sportsPreferences.isSportsEnabled.value) {
            fetchJob?.cancel()
            _uiState.update { it.copy(isLoading = false, error = null, matches = emptyList()) }
            return
        }
        fetchJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        fetchJob = viewModelScope.launch {
            repository.getMatches(filter).collect { result ->
                result.fold(
                    onSuccess = { matches ->
                        _uiState.update { it.copy(isLoading = false, matches = matches) }
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = error.message ?: "An unknown error occurred"
                            ) 
                        }
                    }
                )
            }
        }
    }
}
