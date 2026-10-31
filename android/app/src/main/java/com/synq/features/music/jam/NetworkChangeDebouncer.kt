package com.synq.features.music.jam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkChangeDebouncer(
    private val scope: CoroutineScope,
    private val debounceDelayMs: Long = 3000L,
    private val onTrigger: () -> Unit
) {
    private var debounceJob: Job? = null

    fun onNetworkChanged() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceDelayMs)
            onTrigger()
        }
    }

    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
    }
}

