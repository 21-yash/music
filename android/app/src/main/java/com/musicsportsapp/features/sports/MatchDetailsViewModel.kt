package com.musicsportsapp.features.sports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.features.sports.domain.model.MatchDetails
import com.musicsportsapp.features.sports.domain.model.Scorecard
import com.musicsportsapp.features.sports.domain.model.Squad
import com.musicsportsapp.features.sports.domain.repository.SportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchDetailsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val details: MatchDetails? = null,
    val scorecard: Scorecard? = null,
    val squads: Squad? = null
)

@HiltViewModel
class MatchDetailsViewModel @Inject constructor(
    private val repository: SportsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Expecting matchId from navigation arguments
    private val matchId: String = checkNotNull(savedStateHandle["matchId"])

    private val _uiState = MutableStateFlow(MatchDetailsUiState())
    val uiState: StateFlow<MatchDetailsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            repository.getMatchDetails(matchId).collect { result ->
                result.fold(
                    onSuccess = { details ->
                        _uiState.update { it.copy(isLoading = false, details = details) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
            }
        }

        viewModelScope.launch {
            repository.getScorecard(matchId).collect { result ->
                result.onSuccess { scorecard ->
                    _uiState.update { it.copy(scorecard = scorecard) }
                }
            }
        }

        viewModelScope.launch {
            repository.getSquads(matchId).collect { result ->
                result.onSuccess { squads ->
                    _uiState.update { it.copy(squads = squads) }
                }
            }
        }
    }
}
