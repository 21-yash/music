package com.synq.features.music.util

import kotlin.math.abs

/**
 * Pure Kotlin helper to hold a seek target position after user releases progress drag
 * until live/player position catches up, track changes, or safety timeout expires.
 */
data class SeekHold(
    val targetMs: Long,
    val releasedAtMs: Long,
    val trackKey: String,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val catchUpThresholdMs: Long = DEFAULT_CATCH_UP_THRESHOLD_MS
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 2000L
        const val DEFAULT_CATCH_UP_THRESHOLD_MS = 1500L
    }

    /**
     * Determines whether the seek hold should be released.
     * Returns true if:
     * (a) currentPosMs is within catchUpThresholdMs (~1.5s) of targetMs
     * (b) currentTrackKey differs from trackKey
     * (c) nowMs - releasedAtMs >= timeoutMs (~2s)
     */
    fun shouldRelease(currentPosMs: Long, nowMs: Long, currentTrackKey: String): Boolean {
        if (currentTrackKey != trackKey) return true
        if (nowMs - releasedAtMs >= timeoutMs) return true
        if (abs(currentPosMs - targetMs) <= catchUpThresholdMs) return true
        return false
    }
}

