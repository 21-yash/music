package com.synq.features.music.sleeptimer

interface SleepTimerPlayerBridge {
    fun pause()
    var onTrackEndedListener: (() -> Unit)?
}

