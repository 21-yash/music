package com.synq.features.music.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamProgressCalculatorTest {

    @Test
    fun testMetadataDurationUsedWhenPresent() {
        // meta 215 -> fraction by 215000
        val progress = computeJamProgress(
            metadataDurationSec = 215,
            playerDurationMs = 180_000L,
            positionMs = 107_500L
        )

        assertEquals(215_000L, progress.durationMs)
        assertEquals(0.5f, progress.fraction, 0.001f)
        assertTrue(progress.canSeek)
    }

    @Test
    fun testPlayerDurationUsedWhenMetadataZero() {
        // meta 0 + player 180000 -> by player
        val progress = computeJamProgress(
            metadataDurationSec = 0,
            playerDurationMs = 180_000L,
            positionMs = 90_000L
        )

        assertEquals(180_000L, progress.durationMs)
        assertEquals(0.5f, progress.fraction, 0.001f)
        assertTrue(progress.canSeek)
    }

    @Test
    fun testPlayerDurationUsedWhenMetadataNull() {
        val progress = computeJamProgress(
            metadataDurationSec = null,
            playerDurationMs = 120_000L,
            positionMs = 30_000L
        )

        assertEquals(120_000L, progress.durationMs)
        assertEquals(0.25f, progress.fraction, 0.001f)
        assertTrue(progress.canSeek)
    }

    @Test
    fun testBothZero_fractionZero_canSeekFalse_noDivisionByFakeOne() {
        // both 0 -> fraction 0, canSeek=false, never divides by fake 1
        val progress = computeJamProgress(
            metadataDurationSec = 0,
            playerDurationMs = 0L,
            positionMs = 15_000L
        )

        assertEquals(0L, progress.durationMs)
        assertEquals(0f, progress.fraction, 0.0f)
        assertFalse(progress.canSeek)
    }

    @Test
    fun testNegativeDuration_treatedAsZero() {
        val progress = computeJamProgress(
            metadataDurationSec = -1,
            playerDurationMs = -500L,
            positionMs = 5000L
        )

        assertEquals(0L, progress.durationMs)
        assertEquals(0f, progress.fraction, 0.0f)
        assertFalse(progress.canSeek)
    }

    @Test
    fun testFractionCoercedBetweenZeroAndOne() {
        val underflow = computeJamProgress(
            metadataDurationSec = 100,
            playerDurationMs = 100_000L,
            positionMs = -5000L
        )
        assertEquals(0f, underflow.fraction, 0.0f)

        val overflow = computeJamProgress(
            metadataDurationSec = 100,
            playerDurationMs = 100_000L,
            positionMs = 150_000L
        )
        assertEquals(1f, overflow.fraction, 0.0f)
    }
}
