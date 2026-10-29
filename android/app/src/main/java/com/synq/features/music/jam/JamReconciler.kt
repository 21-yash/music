package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import kotlin.math.abs

data class LocalPlayerState(
    val currentSongId: String?,
    val currentTrackSeq: Long,
    val currentPositionMs: Long,
    val playWhenReady: Boolean,
    val playbackState: Int = Player.STATE_READY,
    val playbackSpeed: Float = 1.0f
)

sealed class ReconcilerAction {
    object Nothing : ReconcilerAction()

    data class ChangeTrack(
        val song: Song?,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val trackSeq: Long
    ) : ReconcilerAction()

    data class SetPlayWhenReady(val playWhenReady: Boolean) : ReconcilerAction()

    data class Seek(val positionMs: Long) : ReconcilerAction()

    data class SpeedRamp(val factor: Float) : ReconcilerAction()
}

object JamReconciler {

    /**
     * Pure decision function: evaluates local player state against authoritative
     * server room state and clock synchronization to determine necessary adjustments.
     */
    fun decide(
        local: LocalPlayerState,
        room: RoomSnapshot?,
        clockSync: ClockSync,
        now: Long = clockSync.serverNow()
    ): ReconcilerAction {
        if (room == null || room.nowPlaying.item == null) {
            return if (local.currentSongId != null) {
                ReconcilerAction.ChangeTrack(
                    song = null,
                    positionMs = 0L,
                    playWhenReady = false,
                    trackSeq = 0L
                )
            } else if (local.playWhenReady) {
                ReconcilerAction.SetPlayWhenReady(false)
            } else {
                ReconcilerAction.Nothing
            }
        }

        val serverNowPlaying = room.nowPlaying
        val serverSong = serverNowPlaying.item.song

        // 1. Track identity & track sequence check
        if (local.currentTrackSeq != serverNowPlaying.trackSeq || local.currentSongId != serverSong.id) {
            val expectedPos = clockSync.expectedPosition(serverNowPlaying, now)
            val playWhenReady = serverNowPlaying.status == JamPlaybackStatus.PLAYING
            return ReconcilerAction.ChangeTrack(
                song = serverSong,
                positionMs = expectedPos,
                playWhenReady = playWhenReady,
                trackSeq = serverNowPlaying.trackSeq
            )
        }

        // 2. Play / Pause & position synchronization checks
        // Guard: Don't send SetPlayWhenReady, Seek, or SpeedRamp while BUFFERING or not READY
        if (local.playbackState != Player.STATE_READY) {
            return ReconcilerAction.Nothing
        }

        val serverIsPlaying = serverNowPlaying.status == JamPlaybackStatus.PLAYING
        if (serverIsPlaying != local.playWhenReady) {
            return ReconcilerAction.SetPlayWhenReady(serverIsPlaying)
        }

        // 3. Position synchronization check
        val expectedPos = clockSync.expectedPosition(serverNowPlaying, now)
        val delta = local.currentPositionMs - expectedPos

        if (!serverIsPlaying) {
            // While paused, accept up to 500ms jitter before seeking
            return if (abs(delta) > 500L) {
                ReconcilerAction.Seek(expectedPos)
            } else {
                ReconcilerAction.Nothing
            }
        }

        // While playing:
        return when {
            // Delta > 2000 ms: hard seek
            abs(delta) > 2000L -> {
                ReconcilerAction.Seek(expectedPos)
            }
            // 300 ms < delta <= 2000 ms: soft speed ramp
            abs(delta) > 300L -> {
                val factor = if (delta > 0) 0.98f else 1.02f
                if (local.playbackSpeed != factor) {
                    ReconcilerAction.SpeedRamp(factor)
                } else {
                    ReconcilerAction.Nothing
                }
            }
            // Within 300 ms: already in sync; restore 1.0f speed if ramp was active
            else -> {
                if (local.playbackSpeed != 1.0f) {
                    ReconcilerAction.SpeedRamp(1.0f)
                } else {
                    ReconcilerAction.Nothing
                }
            }
        }
    }
}
