package com.synq.features.music.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekHoldTest {

    @Test
    fun `releases when player position catches up within threshold`() {
        val hold = SeekHold(
            targetMs = 30_000L,
            releasedAtMs = 1_000L,
            trackKey = "track_1"
        )

        // 1.5s (1500ms) threshold:
        // Position at 28,500ms is exactly 1500ms away -> should release
        assertTrue(hold.shouldRelease(currentPosMs = 28_500L, nowMs = 1_200L, currentTrackKey = "track_1"))
        // Position at 31_200ms is 1200ms away -> should release
        assertTrue(hold.shouldRelease(currentPosMs = 31_200L, nowMs = 1_200L, currentTrackKey = "track_1"))
        // Position at 30_000ms is exact match -> should release
        assertTrue(hold.shouldRelease(currentPosMs = 30_000L, nowMs = 1_100L, currentTrackKey = "track_1"))
    }

    @Test
    fun `releases immediately when track changes`() {
        val hold = SeekHold(
            targetMs = 30_000L,
            releasedAtMs = 1_000L,
            trackKey = "track_1"
        )

        // Still far away (10,000ms) and within timeout (1,100ms), but trackKey changed -> release
        assertTrue(hold.shouldRelease(currentPosMs = 10_000L, nowMs = 1_100L, currentTrackKey = "track_2"))
    }

    @Test
    fun `releases when safety timeout expires`() {
        val hold = SeekHold(
            targetMs = 30_000L,
            releasedAtMs = 1_000L,
            trackKey = "track_1",
            timeoutMs = 2_000L
        )

        // Position still far away (10,000ms), but 2s safety timeout passed (3,000 - 1,000 = 2,000ms) -> release
        assertTrue(hold.shouldRelease(currentPosMs = 10_000L, nowMs = 3_000L, currentTrackKey = "track_1"))
        assertTrue(hold.shouldRelease(currentPosMs = 10_000L, nowMs = 4_500L, currentTrackKey = "track_1"))
    }

    @Test
    fun `does not release while far away and within timeout`() {
        val hold = SeekHold(
            targetMs = 30_000L,
            releasedAtMs = 1_000L,
            trackKey = "track_1",
            timeoutMs = 2_000L,
            catchUpThresholdMs = 1_500L
        )

        // Position is 10,000ms (diff = 20,000ms > 1,500ms), elapsed = 500ms (< 2,000ms), same track -> do not release
        assertFalse(hold.shouldRelease(currentPosMs = 10_000L, nowMs = 1_500L, currentTrackKey = "track_1"))
        // Position is 28_400ms (diff = 1,600ms > 1,500ms), elapsed = 1,800ms (< 2,000ms), same track -> do not release
        assertFalse(hold.shouldRelease(currentPosMs = 28_400L, nowMs = 2_800L, currentTrackKey = "track_1"))
    }
}

