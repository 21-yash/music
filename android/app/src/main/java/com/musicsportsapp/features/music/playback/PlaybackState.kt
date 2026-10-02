package com.musicsportsapp.features.music.playback

import com.musicsportsapp.features.music.domain.model.Song

/**
 * Represents the current state of the music player.
 */
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) {
            (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
