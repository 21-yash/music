package com.synq.features.music.jam

import android.os.SystemClock

data class PingSample(
    val localSendMonotonicMs: Long,
    val serverTimeMs: Long,
    val localReceiveMonotonicMs: Long
) {
    val rttMs: Long
        get() = maxOf(0L, localReceiveMonotonicMs - localSendMonotonicMs)

    val estimatedOffsetMs: Long
        get() = serverTimeMs - (localSendMonotonicMs + rttMs / 2)
}

class ClockSync(
    private val monotonicNowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockNowMs: () -> Long = { System.currentTimeMillis() }
) {
    var estimatedOffsetMs: Long = 0L
        private set

    private var anchorElapsedMs: Long = 0L
    private var anchorServerTimeMs: Long = 0L
    var isSynchronized: Boolean = false
        private set

    /**
     * Updates clock sync given a collection of 1-3 ping samples, selecting
     * the sample with the lowest round-trip time (lowest network jitter).
     */
    fun updateWithSamples(samples: List<PingSample>) {
        if (samples.isEmpty()) return
        val best = samples.minByOrNull { it.rttMs } ?: return

        estimatedOffsetMs = best.estimatedOffsetMs
        anchorElapsedMs = best.localReceiveMonotonicMs
        anchorServerTimeMs = best.serverTimeMs + (best.rttMs / 2)
        isSynchronized = true
    }

    /**
     * Convenience method to record a single ping sample.
     */
    fun recordSample(localSendMonotonicMs: Long, serverTimeMs: Long, localReceiveMonotonicMs: Long) {
        updateWithSamples(listOf(PingSample(localSendMonotonicMs, serverTimeMs, localReceiveMonotonicMs)))
    }

    /**
     * Projects the current server timestamp monotonically based on elapsed realtime
     * since the anchor measurement. Immune to manual client wall-clock changes.
     */
    fun serverNow(): Long {
        if (!isSynchronized) {
            return wallClockNowMs()
        }
        val elapsedSinceAnchor = maxOf(0L, monotonicNowMs() - anchorElapsedMs)
        return anchorServerTimeMs + elapsedSinceAnchor
    }

    /**
     * Computes the deterministic expected playback position in milliseconds.
     * Clamped between 0 and track durationMs if duration is known (> 0).
     */
    fun expectedPosition(nowPlaying: JamNowPlaying, now: Long = serverNow()): Long {
        return when (nowPlaying.status) {
            JamPlaybackStatus.PLAYING -> {
                val elapsed = maxOf(0L, now - nowPlaying.anchorServerTs)
                val rawPosition = nowPlaying.positionMs + elapsed
                val durationSec = nowPlaying.item?.song?.durationSeconds ?: 0
                if (durationSec > 0) {
                    val durationMs = durationSec * 1000L
                    minOf(rawPosition, durationMs)
                } else {
                    rawPosition
                }
            }
            JamPlaybackStatus.PAUSED,
            JamPlaybackStatus.IDLE -> {
                nowPlaying.positionMs
            }
        }
    }

    fun reset() {
        isSynchronized = false
        estimatedOffsetMs = 0L
        anchorElapsedMs = 0L
        anchorServerTimeMs = 0L
    }
}
