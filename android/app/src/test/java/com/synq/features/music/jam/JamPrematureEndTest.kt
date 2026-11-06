package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JamPrematureEndTest {

    private fun sampleSong(id: String, durationSec: Int = 180): Song {
        return Song(
            id = id,
            title = "Premature End Test Song",
            artists = emptyList(),
            album = null,
            durationSeconds = durationSec,
            imageUrl = null,
            year = "2024",
            language = "English",
            hasLyrics = false,
            playCount = 0,
            label = "Label",
            streamRef = "ref_$id",
            providerId = "saavn"
        )
    }

    class PrematureEndHarness(
        private val clockSync: ClockSync,
        private val getRoom: () -> RoomSnapshot?,
        private val onReloadAndSeek: (Song, Long) -> Unit,
        private val onStaySilent: () -> Unit,
        private val onReportTrackEnded: (Long) -> Unit
    ) {
        var trackRetryCount = 0
            private set
        private var retryTrackSeq = 0L
        private var lastReportedEndedTrackSeq = 0L

        fun handleTrackEndedOrError() {
            val room = getRoom() ?: return
            val nowPlaying = room.nowPlaying
            if (nowPlaying.item == null) return

            val durationMs = (nowPlaying.item.song.durationSeconds ?: 0) * 1000L
            val expectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
            val remainingMs = durationMs - expectedPos

            if (durationMs > 0 && remainingMs > 6000L) {
                if (retryTrackSeq != nowPlaying.trackSeq) {
                    retryTrackSeq = nowPlaying.trackSeq
                    trackRetryCount = 0
                }

                if (trackRetryCount < 2) {
                    trackRetryCount++
                    onReloadAndSeek(nowPlaying.item.song, expectedPos)
                } else {
                    onStaySilent()
                }
            } else {
                reportTrackEnded(nowPlaying.trackSeq)
            }
        }

        fun checkNearEndTick() {
            val room = getRoom() ?: return
            val nowPlaying = room.nowPlaying
            if (nowPlaying.item == null || nowPlaying.status != JamPlaybackStatus.PLAYING) return

            val durationMs = (nowPlaying.item.song.durationSeconds ?: 0) * 1000L
            val expectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
            val isNearEnd = if (durationMs > 0L) {
                expectedPos >= durationMs - 6000L
            } else {
                (clockSync.serverNow() - nowPlaying.anchorServerTs + nowPlaying.positionMs) >= 15000L
            }

            if (isNearEnd && trackRetryCount >= 2) {
                reportTrackEnded(nowPlaying.trackSeq)
            }
        }

        private fun reportTrackEnded(trackSeq: Long) {
            if (lastReportedEndedTrackSeq == trackSeq) return
            lastReportedEndedTrackSeq = trackSeq
            onReportTrackEnded(trackSeq)
        }
    }

    private var currentMonotonic = 10_000L
    private val fakeClock = ClockSync(
        monotonicNowMs = { currentMonotonic },
        wallClockNowMs = { currentMonotonic }
    ).apply {
        recordSample(10_000L, 10_000L, 10_000L)
    }

    @Test
    fun `reloads and seeks up to 2 times when ending early then stays silent and reports near end`() {
        val song = sampleSong("s1", durationSec = 100) // 100_000 ms duration
        var room = RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = 10_000L,
            hostUserId = "u1",
            createdAt = 10_000L,
            members = emptyList(),
            defaults = JamPermissions(),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem("q1", song),
                status = JamPlaybackStatus.PLAYING,
                positionMs = 10_000L, // At 10s of 100s -> 90s remaining (> 6s)
                anchorServerTs = 10_000L,
                trackSeq = 1L
            )
        )

        val reloads = mutableListOf<Long>()
        var staySilentCalled = 0
        val endedReports = mutableListOf<Long>()

        val harness = PrematureEndHarness(
            clockSync = fakeClock,
            getRoom = { room },
            onReloadAndSeek = { _, pos -> reloads.add(pos) },
            onStaySilent = { staySilentCalled++ },
            onReportTrackEnded = { endedReports.add(it) }
        )

        // 1st premature failure at 10s (90s remaining)
        harness.handleTrackEndedOrError()
        assertEquals("Must be retry 1", 1, harness.trackRetryCount)
        assertEquals("Must have reloaded at 10s", listOf(10_000L), reloads)
        assertEquals("Must NOT report trackEnded yet", 0, endedReports.size)
        assertEquals(0, staySilentCalled)

        // 2nd premature failure at 20s (80s remaining)
        currentMonotonic = 20_000L // 10s elapsed
        harness.handleTrackEndedOrError()
        assertEquals("Must be retry 2", 2, harness.trackRetryCount)
        assertEquals("Must have reloaded at 20s", listOf(10_000L, 20_000L), reloads)
        assertEquals(0, endedReports.size)
        assertEquals(0, staySilentCalled)

        // 3rd failure at 25s: retries exhausted -> must stay silent
        currentMonotonic = 25_000L
        harness.handleTrackEndedOrError()
        assertEquals("Retries capped at 2", 2, harness.trackRetryCount)
        assertEquals(1, staySilentCalled)
        assertEquals("Must NOT report trackEnded yet while in middle of track", 0, endedReports.size)

        // Later: expected position advances to 95s (5s remaining, in near-end window of 100s song)
        currentMonotonic = 95_000L
        harness.checkNearEndTick()
        assertEquals("Must report trackEnded once entering near-end window", listOf(1L), endedReports)
    }

    @Test
    fun `immediately reports trackEnded when ending within near-end window`() {
        val song = sampleSong("s1", durationSec = 100) // 100_000 ms duration
        // Position at 96s -> 4s remaining (<= 6s window)
        val room = RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = 10_000L,
            hostUserId = "u1",
            createdAt = 10_000L,
            members = emptyList(),
            defaults = JamPermissions(),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem("q1", song),
                status = JamPlaybackStatus.PLAYING,
                positionMs = 96_000L,
                anchorServerTs = 10_000L,
                trackSeq = 5L
            )
        )

        val reloads = mutableListOf<Long>()
        val endedReports = mutableListOf<Long>()

        val harness = PrematureEndHarness(
            clockSync = fakeClock,
            getRoom = { room },
            onReloadAndSeek = { _, pos -> reloads.add(pos) },
            onStaySilent = {},
            onReportTrackEnded = { endedReports.add(it) }
        )

        harness.handleTrackEndedOrError()
        assertEquals("No retries when within near-end window", 0, harness.trackRetryCount)
        assertEquals(0, reloads.size)
        assertEquals("Must immediately report trackEnded", listOf(5L), endedReports)
    }
}

