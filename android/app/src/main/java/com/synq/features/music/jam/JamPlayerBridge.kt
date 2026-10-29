package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface JamPlayerBridge {
    val playbackState: StateFlow<PlaybackState>
    var jamInterceptor: JamPlaybackInterceptor?

    fun getPlayerCurrentPosition(): Long
    fun getPlayerPlayWhenReady(): Boolean
    fun getPlayerPlaybackState(): Int
    fun getPlaybackSpeed(): Float
    fun getPlayerDuration(): Long

    fun playJamSyncSong(
        song: Song,
        initialPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
        targetQid: String? = null
    )

    fun prefetchTrack(song: Song)
    fun stop()
    fun applyJamPlay()
    fun applyJamPause()
    fun applyJamSeek(positionMs: Long)
    fun applyJamSpeed(factor: Float)

    fun setJamQueue(
        jamQueue: List<Song>,
        targetSong: Song? = null,
        targetQid: String? = null,
        queueQids: List<String> = emptyList(),
        recommendedQids: Set<String> = emptySet()
    )

    fun stashPersonalQueue()
    fun restorePersonalQueue()
    fun setPlaybackSpeed(speed: Float)
}

