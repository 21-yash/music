package com.synq.features.music.sleeptimer

import com.synq.features.music.jam.JamSessionManager
import com.synq.features.music.playback.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SleepTimerManager(
    private val playerManager: SleepTimerPlayerBridge,
    private val jamSessionManagerProvider: Provider<JamSessionManager>,
    coroutineScope: CoroutineScope? = null
) {
    @Inject
    constructor(
        playerManager: MusicPlayerManager,
        jamSessionManagerProvider: Provider<JamSessionManager>
    ) : this(playerManager as SleepTimerPlayerBridge, jamSessionManagerProvider, null)

    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    private val _selectedOption = MutableStateFlow("Off")
    val selectedOption: StateFlow<String> = _selectedOption.asStateFlow()

    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds.asStateFlow()

    private fun handleTimerFinished() {
        val jamManager = jamSessionManagerProvider.get()
        if (jamManager.jamState.value.isInJam) {
            jamManager.stepOutLocally()
        } else {
            playerManager.pause()
        }
    }

    fun setTimer(option: String) {
        countdownJob?.cancel()
        countdownJob = null
        playerManager.onTrackEndedListener = null

        _selectedOption.value = option

        when (option) {
            "Off" -> {
                _remainingSeconds.value = null
            }
            "End of track" -> {
                _remainingSeconds.value = null
                playerManager.onTrackEndedListener = {
                    handleTimerFinished()
                    cancel()
                }
            }
            else -> {
                val minutes = when (option) {
                    "15 mins" -> 15
                    "30 mins" -> 30
                    "45 mins" -> 45
                    "60 mins" -> 60
                    else -> 0
                }
                if (minutes > 0) {
                    var remaining = minutes * 60L
                    _remainingSeconds.value = remaining
                    countdownJob = scope.launch {
                        while (isActive && remaining > 0) {
                            delay(1000)
                            remaining--
                            _remainingSeconds.value = remaining
                        }
                        if (remaining <= 0) {
                            handleTimerFinished()
                            _selectedOption.value = "Off"
                            _remainingSeconds.value = null
                        }
                    }
                } else {
                    _remainingSeconds.value = null
                }
            }
        }
    }

    fun cancel() {
        setTimer("Off")
    }
}
