package com.musicsportsapp.features.music.sleeptimer

import com.musicsportsapp.features.music.playback.MusicPlayerManager
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
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor(
    private val playerManager: MusicPlayerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    private val _selectedOption = MutableStateFlow("Off")
    val selectedOption: StateFlow<String> = _selectedOption.asStateFlow()

    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds.asStateFlow()

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
                    playerManager.pause()
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
                            playerManager.pause()
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
