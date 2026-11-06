package com.synq.features.music.playback

object PlaybackBufferConfig {
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 50_000
    const val BUFFER_FOR_PLAYBACK_MS = 1_000
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
}
