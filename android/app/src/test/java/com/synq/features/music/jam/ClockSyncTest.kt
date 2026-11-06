package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import org.junit.Assert.*
import org.junit.Test

class ClockSyncTest {

    @Test
    fun `picks sample with lowest RTT among multiple samples`() {
        var monotonicTime = 1000L
        val clockSync = ClockSync(
            monotonicNowMs = { monotonicTime },
            wallClockNowMs = { 5000L }
        )

        // Sample 1: RTT = 200ms
        val s1 = PingSample(localSendMonotonicMs = 1000L, serverTimeMs = 50000L, localReceiveMonotonicMs = 1200L)
        // Sample 2: RTT = 50ms (lowest jitter)
        val s2 = PingSample(localSendMonotonicMs = 1300L, serverTimeMs = 50100L, localReceiveMonotonicMs = 1350L)
        // Sample 3: RTT = 300ms
        val s3 = PingSample(localSendMonotonicMs = 1400L, serverTimeMs = 50200L, localReceiveMonotonicMs = 1700L)

        clockSync.updateWithSamples(listOf(s1, s2, s3))

        assertTrue(clockSync.isSynchronized)
        // Best sample is s2: RTT = 50ms, halfRTT = 25ms, anchorServerTime = 50100 + 25 = 50125ms
        // anchorElapsed = 1350ms
        monotonicTime = 1350L
        assertEquals(50125L, clockSync.serverNow())

        // Advancing monotonic time by 500ms advances serverNow by exactly 500ms
        monotonicTime = 1850L
        assertEquals(50625L, clockSync.serverNow())
    }

    @Test
    fun `serverNow projects monotonically immune to wall clock adjustments`() {
        var monotonicTime = 10_000L
        var wallClockTime = 100_000L

        val clockSync = ClockSync(
            monotonicNowMs = { monotonicTime },
            wallClockNowMs = { wallClockTime }
        )

        clockSync.recordSample(
            localSendMonotonicMs = 10_000L,
            serverTimeMs = 500_000L,
            localReceiveMonotonicMs = 10_040L // RTT = 40ms, halfRTT = 20ms
        )
        monotonicTime = 10_040L

        val before = clockSync.serverNow()
        assertEquals(500_020L, before)

        // User manually changes wall clock back by 2 hours
        wallClockTime -= 7_200_000L

        // Monotonic time only advanced by 1000ms
        monotonicTime += 1000L

        val after = clockSync.serverNow()
        assertEquals(501_020L, after)
    }

    @Test
    fun `expectedPosition returns positionMs when paused or idle`() {
        val clockSync = ClockSync()

        val paused = JamNowPlaying(
            item = null,
            status = JamPlaybackStatus.PAUSED,
            positionMs = 12345L,
            anchorServerTs = 1000L
        )
        assertEquals(12345L, clockSync.expectedPosition(paused, now = 50000L))

        val idle = JamNowPlaying(
            item = null,
            status = JamPlaybackStatus.IDLE,
            positionMs = 0L,
            anchorServerTs = 0L
        )
        assertEquals(0L, clockSync.expectedPosition(idle, now = 50000L))
    }

    @Test
    fun `expectedPosition calculates positionMs plus elapsed when playing and clamps to duration`() {
        val clockSync = ClockSync()

        val testSong = Song(
            id = "s1",
            title = "Test",
            artists = emptyList(),
            album = null,
            durationSeconds = 180, // 180,000 ms
            imageUrl = null,
            year = "2024",
            language = "en",
            hasLyrics = false,
            playCount = 0,
            label = "",
            streamRef = "",
            providerId = "jiosaavn"
        )

        val item = JamQueueItem(qid = "q1", song = testSong)

        val playing = JamNowPlaying(
            item = item,
            status = JamPlaybackStatus.PLAYING,
            positionMs = 10_000L,
            anchorServerTs = 50_000L
        )

        // At anchorServerTs: position = 10_000ms
        assertEquals(10_000L, clockSync.expectedPosition(playing, now = 50_000L))

        // 5 seconds later: position = 15_000ms
        assertEquals(15_000L, clockSync.expectedPosition(playing, now = 55_000L))

        // Beyond song duration (e.g. 200 seconds elapsed): clamped to 180,000ms
        assertEquals(180_000L, clockSync.expectedPosition(playing, now = 250_000L))
    }
}
