package com.synq.features.sports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synq.features.music.playback.MusicPlayerManager
import com.synq.features.sports.data.remote.SportsSocketService
import com.synq.features.sports.data.dto.MatchDetailsResponseDto
import com.synq.features.sports.domain.model.HighlightSource
import com.synq.features.sports.domain.model.MatchDetails
import com.synq.features.sports.domain.model.MatchHighlight
import com.synq.features.sports.domain.model.MatchState
import com.synq.features.sports.domain.model.Scorecard
import com.synq.features.sports.domain.model.Squad
import com.synq.features.sports.domain.repository.SportsRepository
import com.synq.features.sports.highlights.HighlightStreamResolver
import com.synq.features.sports.highlights.ResolvedHighlightPlayback
import com.synq.features.sports.highlights.YouTubeDeviceHighlightSearcher
import com.synq.features.sports.live.LiveEventType
import com.synq.features.sports.live.LiveMatchEvent
import com.synq.features.sports.live.LiveMatchEventDetector
import com.synq.features.sports.live.RecentBallItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class MatchDetailsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val details: MatchDetails? = null,
    val scorecard: Scorecard? = null,
    val squads: Squad? = null,
    val highlights: List<MatchHighlight> = emptyList(),
    val isHighlightsLoading: Boolean = false,
    val hasLoadedHighlights: Boolean = false,
    val highlightsError: String? = null,
    val selectedHighlight: MatchHighlight? = null,
    val resolvedPlayback: ResolvedHighlightPlayback? = null,
    val isResolvingStream: Boolean = false,
    val streamError: String? = null,
    val activeLiveEvent: LiveMatchEvent? = null,
    val recentBallItems: List<RecentBallItem> = emptyList(),
    val lastWicketPulseId: Long? = null
)

@HiltViewModel
class MatchDetailsViewModel @Inject constructor(
    private val repository: SportsRepository,
    private val socketService: SportsSocketService,
    private val highlightStreamResolver: HighlightStreamResolver,
    private val youTubeDeviceHighlightSearcher: YouTubeDeviceHighlightSearcher,
    private val musicPlayerManager: MusicPlayerManager,
    private val json: Json,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val matchId: String = checkNotNull(savedStateHandle["matchId"])
    private var highlightsJob: Job? = null
    private var scorecardJob: Job? = null
    private var streamResolutionJob: Job? = null
    private var eventClearJob: Job? = null
    private var recentBallsClearJob: Job? = null
    private var lastWicketClearJob: Job? = null
    private var lastScorecardFetchMs: Long = 0L
    private var nextEventId: Long = 1L
    private var nextRecentBallId: Long = 1L
    private var nextWicketPulseId: Long = 1L

    private val _uiState = MutableStateFlow(MatchDetailsUiState())
    val uiState: StateFlow<MatchDetailsUiState> = _uiState.asStateFlow()

    init {
        loadData()
        setupSocket()
    }

    private fun setupSocket() {
        socketService.connect()
        socketService.joinMatch(matchId)
        
        viewModelScope.launch {
            socketService.matchUpdates.collect { payload ->
                try {
                    val response = json.decodeFromString<MatchDetailsResponseDto>(payload)
                    applyMatchDetailsUpdate(response.match.toDomain())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            repository.getMatchDetails(matchId).collect { result ->
                result.fold(
                    onSuccess = { details ->
                        applyMatchDetailsUpdate(details, isLoading = false)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
                )
            }
        }

        refreshScorecard(force = true)

        viewModelScope.launch {
            repository.getSquads(matchId).collect { result ->
                result.onSuccess { squads ->
                    _uiState.update { it.copy(squads = squads) }
                }
            }
        }
    }

    fun refreshScorecard(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastScorecardFetchMs < 15_000L) return
        if (!force && scorecardJob?.isActive == true) return
        lastScorecardFetchMs = now

        scorecardJob?.cancel()
        scorecardJob = viewModelScope.launch {
            repository.getScorecard(matchId).collect { result ->
                result.onSuccess { incoming ->
                    _uiState.update { state ->
                        if (LiveMatchEventDetector.isScorecardStale(state.scorecard, incoming)) {
                            state
                        } else {
                            val synced = state.details?.let {
                                LiveMatchEventDetector.reconcileScorecardWithLiveDetails(incoming, it)
                            } ?: incoming
                            state.copy(scorecard = synced)
                        }
                    }
                }
            }
        }
    }

    private fun applyMatchDetailsUpdate(
        newDetails: MatchDetails,
        isLoading: Boolean? = null
    ) {
        var emittedEvent: LiveMatchEvent? = null
        var hasNewRecentBalls = false
        var emittedLastWicketPulseId: Long? = null
        var shouldRefreshLiveScorecard = false

        _uiState.update { currentState ->
            val sameMatchPrev = currentState.details?.takeIf { it.summary.id == newDetails.summary.id }
            if (sameMatchPrev != null &&
                LiveMatchEventDetector.isStaleRollback(sameMatchPrev, newDetails)
            ) {
                return@update currentState
            }

            if (sameMatchPrev != null) {
                emittedEvent = LiveMatchEventDetector.detect(
                    previous = sameMatchPrev,
                    next = newDetails,
                    eventId = nextEventId
                )?.also { nextEventId++ }

                if (newDetails.lastWicket.isNotBlank() && newDetails.lastWicket != sameMatchPrev.lastWicket) {
                    emittedLastWicketPulseId = nextWicketPulseId++
                }
            }

            // Build RecentBallItems preserving existing IDs so LazyRow slides smoothly and new duplicates pop cleanly
            val nextTokens = LiveMatchEventDetector.parseBalls(newDetails.recentBalls)
            val updatedRecentBallItems = when {
                nextTokens.isEmpty() -> emptyList()
                sameMatchPrev == null || currentState.recentBallItems.isEmpty() -> {
                    nextTokens.reversed().map { token ->
                        RecentBallItem(id = nextRecentBallId++, label = token, isNew = false)
                    }
                }
                newDetails.recentBalls != sameMatchPrev.recentBalls && newDetails.summary.isLive -> {
                    val addedChrono = LiveMatchEventDetector.extractNewRecentBalls(
                        previousRaw = sameMatchPrev.recentBalls,
                        nextRaw = newDetails.recentBalls
                    )
                    if (addedChrono.isEmpty()) {
                        nextTokens.reversed().map { token ->
                            RecentBallItem(id = nextRecentBallId++, label = token, isNew = false)
                        }
                    } else {
                        hasNewRecentBalls = true
                        val keptCount = (nextTokens.size - addedChrono.size).coerceAtLeast(0)
                        val olderKept = currentState.recentBallItems.take(keptCount).map { it.copy(isNew = false) }
                        val expectedOlderLabels = nextTokens.dropLast(addedChrono.size).reversed()
                        val newOnes = addedChrono.reversed().map { token ->
                            RecentBallItem(id = nextRecentBallId++, label = token, isNew = true)
                        }
                        if (olderKept.map { it.label } == expectedOlderLabels) {
                            newOnes + olderKept
                        } else {
                            // Clean rebuild keeping new arrivals marked as new
                            val oldestKept = expectedOlderLabels.map { token ->
                                RecentBallItem(id = nextRecentBallId++, label = token, isNew = false)
                            }
                            newOnes + oldestKept
                        }
                    }
                }
                else -> currentState.recentBallItems
            }

            val hasBallOrScoreProgression = sameMatchPrev != null && (
                newDetails.recentBalls != sameMatchPrev.recentBalls ||
                    newDetails.summary.team1Score != sameMatchPrev.summary.team1Score ||
                    newDetails.summary.team2Score != sameMatchPrev.summary.team2Score
                )
            if (hasBallOrScoreProgression && newDetails.summary.isLive) {
                shouldRefreshLiveScorecard = true
            }

            val nextActiveEvent = when {
                emittedEvent != null -> emittedEvent
                currentState.activeLiveEvent?.hasPersistentState() == true && hasBallOrScoreProgression -> null
                else -> currentState.activeLiveEvent
            }

            val syncedScorecard = currentState.scorecard?.let {
                LiveMatchEventDetector.reconcileScorecardWithLiveDetails(it, newDetails)
            }

            currentState.copy(
                isLoading = isLoading ?: currentState.isLoading,
                details = newDetails,
                scorecard = syncedScorecard,
                activeLiveEvent = nextActiveEvent,
                recentBallItems = updatedRecentBallItems,
                lastWicketPulseId = emittedLastWicketPulseId ?: currentState.lastWicketPulseId
            )
        }

        if (shouldRefreshLiveScorecard) {
            refreshScorecard(force = false)
        }

        emittedEvent?.let { event ->
            eventClearJob?.cancel()
            val needsTransientCleanup = event.matchCardType != null ||
                event.bowlerEvents.isNotEmpty() ||
                event.isWicketSuperseded ||
                event.batterEvents.values.any { it == LiveEventType.FOUR || it == LiveEventType.SIX }

            if (needsTransientCleanup || !event.hasPersistentState()) {
                eventClearJob = viewModelScope.launch {
                    delay(if (event.isWicketSuperseded) 3200L else 2800L)
                    _uiState.update { state ->
                        if (state.activeLiveEvent?.id == event.id) {
                            state.copy(activeLiveEvent = state.activeLiveEvent.retainedPersistentOnly())
                        } else {
                            state
                        }
                    }
                }
            }
        }

        if (hasNewRecentBalls) {
            recentBallsClearJob?.cancel()
            recentBallsClearJob = viewModelScope.launch {
                delay(1100L)
                _uiState.update { state ->
                    state.copy(recentBallItems = state.recentBallItems.map { it.copy(isNew = false) })
                }
            }
        }

        emittedLastWicketPulseId?.let { pulseId ->
            lastWicketClearJob?.cancel()
            lastWicketClearJob = viewModelScope.launch {
                delay(1500L)
                _uiState.update { state ->
                    if (state.lastWicketPulseId == pulseId) state.copy(lastWicketPulseId = null) else state
                }
            }
        }
    }

    fun loadHighlights(forceRefresh: Boolean = false) {
        val current = _uiState.value
        if (!forceRefresh && (current.hasLoadedHighlights || current.isHighlightsLoading)) {
            return
        }

        highlightsJob?.cancel()
        _uiState.update { it.copy(isHighlightsLoading = true, highlightsError = null) }

        highlightsJob = viewModelScope.launch {
            repository.getHighlights(matchId).collect { result ->
                result.fold(
                    onSuccess = { backendList ->
                        val hasOfficialBcciOrIpl = backendList.any {
                            it.source == HighlightSource.BCCI || it.source == HighlightSource.IPLT20
                        }
                        val summary = _uiState.value.details?.summary

                        // If backend returned no BCCI/IPL highlights, also search YouTube from the user's
                        // own IP so India-geo-restricted channels (Sony LIV, FanCode) appear even when
                        // the backend runs in another region (e.g. Singapore).
                        val merged = if (!hasOfficialBcciOrIpl && summary != null && summary.state != MatchState.UPCOMING) {
                            val deviceList = youTubeDeviceHighlightSearcher.search(summary)
                            (backendList + deviceList).distinctBy { it.streamTarget }
                        } else {
                            backendList
                        }

                        _uiState.update {
                            it.copy(
                                isHighlightsLoading = false,
                                hasLoadedHighlights = true,
                                highlights = merged,
                                highlightsError = null
                            )
                        }
                    },
                    onFailure = { error ->
                        val summary = _uiState.value.details?.summary
                        val deviceList = if (summary != null && summary.state != MatchState.UPCOMING) {
                            youTubeDeviceHighlightSearcher.search(summary)
                        } else {
                            emptyList()
                        }
                        if (deviceList.isNotEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isHighlightsLoading = false,
                                    hasLoadedHighlights = true,
                                    highlights = deviceList,
                                    highlightsError = null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isHighlightsLoading = false,
                                    highlightsError = error.message ?: "Failed to load highlights"
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    fun pauseMusicForHighlight() {
        musicPlayerManager.pause()
    }

    fun selectHighlight(highlight: MatchHighlight) {
        // Pause active music playback before starting match highlight video
        musicPlayerManager.pause()

        streamResolutionJob?.cancel()
        _uiState.update {
            it.copy(
                selectedHighlight = highlight,
                resolvedPlayback = null,
                isResolvingStream = true,
                streamError = null
            )
        }

        streamResolutionJob = viewModelScope.launch {
            val result = highlightStreamResolver.resolve(highlight)
            result.fold(
                onSuccess = { resolved ->
                    _uiState.update {
                        it.copy(
                            resolvedPlayback = resolved,
                            isResolvingStream = false,
                            streamError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            streamError = error.message ?: "Unable to play this highlight stream"
                        )
                    }
                }
            )
        }
    }

    fun selectQuality(option: com.synq.features.sports.highlights.VideoQualityOption) {
        _uiState.update { state ->
            val current = state.resolvedPlayback ?: return@update state
            state.copy(resolvedPlayback = current.copy(activeQuality = option))
        }
    }

    fun clearSelectedHighlight() {
        streamResolutionJob?.cancel()
        _uiState.update {
            it.copy(
                selectedHighlight = null,
                resolvedPlayback = null,
                isResolvingStream = false,
                streamError = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketService.leaveMatch(matchId)
    }
}
