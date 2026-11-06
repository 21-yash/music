package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import org.junit.Assert.*
import org.junit.Test

class JamReconcilerTest {

    private fun makeSong(id: String, durationSec: Int = 180): Song = Song(
        id = id,
        title = "Title $id",
        artists = emptyList(),
        album = null,
        durationSeconds = durationSec,
        imageUrl = null,
        year = "2024",
        language = "en",
        hasLyrics = false,
        playCount = 0,
        label = "",
        streamRef = "",
        providerId = "jiosaavn"
    )

    private fun makeRoom(
        songId: String,
        trackSeq: Long,
        status: JamPlaybackStatus,
        positionMs: Long,
        anchorTs: Long
    ): RoomSnapshot {
        val song = makeSong(songId)
        return RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = anchorTs,
            hostUserId = "u1",
            createdAt = anchorTs,
            members = emptyList(),
            defaults = JamPermissions(),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem(qid = "q_$songId", song = song),
                status = status,
                positionMs = positionMs,
                anchorServerTs = anchorTs,
                trackSeq = trackSeq
            )
        )
    }

    private val fixedClockSync = ClockSync(
        monotonicNowMs = { 10_000L },
        wallClockNowMs = { 10_000L }
    ).apply {
        recordSample(10_000L, 50_000L, 10_000L) // anchorServerTime = 50,000ms
    }

    @Test
    fun `returns ChangeTrack when trackSeq differs`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5000L,
            playWhenReady = true
        )
        val room = makeRoom(songId = "song_2", trackSeq = 2L, status = JamPlaybackStatus.PLAYING, positionMs = 0L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.ChangeTrack)
        val ct = action as ReconcilerAction.ChangeTrack
        assertEquals("song_2", ct.song?.id)
        assertEquals(2L, ct.trackSeq)
        assertFalse("New track must load paused (playWhenReady=false)", ct.playWhenReady)
        assertEquals(0L, ct.positionMs)
    }

    @Test
    fun `returns SetPlayWhenReady when play pause state differs`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5000L,
            playWhenReady = false // Local paused
        )
        // Server playing
        val room = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.SetPlayWhenReady)
        assertTrue((action as ReconcilerAction.SetPlayWhenReady).playWhenReady)
    }

    @Test
    fun `returns Seek when delta exceeds 2000ms`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 10_000L,
            playWhenReady = true
        )
        // Expected pos = 5000 + 1000 = 6000ms. Local = 10_000ms -> Delta = 4000ms (> 2000ms)
        val room = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 49_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.Seek)
        assertEquals(6000L, (action as ReconcilerAction.Seek).positionMs)
    }

    data class DriftBoundaryCase(
        val name: String,
        val deltaMs: Long,
        val initialSpeed: Float,
        val expectedAction: String, // "Nothing", "Seek", "SpeedRamp"
        val expectedSpeed: Float? = null,
        val expectedSeekPos: Long? = null
    )

    @Test
    fun `table-driven tests for drift thresholds and hysteresis boundaries`() {
        val basePos = 5000L
        val room = makeRoom(
            songId = "song_1",
            trackSeq = 1L,
            status = JamPlaybackStatus.PLAYING,
            positionMs = basePos,
            anchorTs = 50_000L
        )

        val testCases = listOf(
            // Boundary: > 2000ms hard seek
            DriftBoundaryCase("ahead hard seek (>2s)", deltaMs = +2001L, initialSpeed = 1.0f, expectedAction = "Seek", expectedSeekPos = basePos),
            DriftBoundaryCase("behind hard seek (>2s)", deltaMs = -2001L, initialSpeed = 1.0f, expectedAction = "Seek", expectedSeekPos = basePos),

            // Boundary: exactly 2000ms is soft speed ramp (not hard seek)
            DriftBoundaryCase("ahead 2000ms ramp", deltaMs = +2000L, initialSpeed = 1.0f, expectedAction = "SpeedRamp", expectedSpeed = 0.96f),
            DriftBoundaryCase("behind 2000ms ramp", deltaMs = -2000L, initialSpeed = 1.0f, expectedAction = "SpeedRamp", expectedSpeed = 1.04f),

            // Boundary: exactly 200ms entry threshold
            DriftBoundaryCase("ahead 200ms entry", deltaMs = +200L, initialSpeed = 1.0f, expectedAction = "SpeedRamp", expectedSpeed = 0.96f),
            DriftBoundaryCase("behind 200ms entry", deltaMs = -200L, initialSpeed = 1.0f, expectedAction = "SpeedRamp", expectedSpeed = 1.04f),

            // Below 200ms when speed is 1.0: do nothing
            DriftBoundaryCase("below 200ms ahead (199ms)", deltaMs = +199L, initialSpeed = 1.0f, expectedAction = "Nothing"),
            DriftBoundaryCase("below 200ms behind (-199ms)", deltaMs = -199L, initialSpeed = 1.0f, expectedAction = "Nothing"),
            DriftBoundaryCase("in sync (0ms)", deltaMs = 0L, initialSpeed = 1.0f, expectedAction = "Nothing"),

            // Flapping around 200ms: hysteresis maintains active speed ramp
            DriftBoundaryCase("flapping below 200ms maintains 1.04 (-199ms)", deltaMs = -199L, initialSpeed = 1.04f, expectedAction = "Nothing"),
            DriftBoundaryCase("flapping below 200ms maintains 0.96 (+199ms)", deltaMs = +199L, initialSpeed = 0.96f, expectedAction = "Nothing"),
            DriftBoundaryCase("hysteresis boundary at 100ms maintains 1.04 (-100ms)", deltaMs = -100L, initialSpeed = 1.04f, expectedAction = "Nothing"),
            DriftBoundaryCase("hysteresis boundary at 100ms maintains 0.96 (+100ms)", deltaMs = +100L, initialSpeed = 0.96f, expectedAction = "Nothing"),

            // Restore speed 1.0 ONLY below 100ms
            DriftBoundaryCase("restore 1.0 below 100ms from 1.04 (-99ms)", deltaMs = -99L, initialSpeed = 1.04f, expectedAction = "SpeedRamp", expectedSpeed = 1.0f),
            DriftBoundaryCase("restore 1.0 below 100ms from 0.96 (+99ms)", deltaMs = +99L, initialSpeed = 0.96f, expectedAction = "SpeedRamp", expectedSpeed = 1.0f),
            DriftBoundaryCase("restore 1.0 at 0ms drift from 1.04", deltaMs = 0L, initialSpeed = 1.04f, expectedAction = "SpeedRamp", expectedSpeed = 1.0f),
            DriftBoundaryCase("restore 1.0 at 0ms drift from 0.96", deltaMs = 0L, initialSpeed = 0.96f, expectedAction = "SpeedRamp", expectedSpeed = 1.0f)
        )

        for (tc in testCases) {
            val local = LocalPlayerState(
                currentSongId = "song_1",
                currentTrackSeq = 1L,
                currentPositionMs = basePos + tc.deltaMs,
                playWhenReady = true,
                playbackSpeed = tc.initialSpeed
            )
            val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)

            when (tc.expectedAction) {
                "Nothing" -> assertEquals("${tc.name}: expected Nothing", ReconcilerAction.Nothing, action)
                "Seek" -> {
                    assertTrue("${tc.name}: expected Seek", action is ReconcilerAction.Seek)
                    assertEquals("${tc.name}: expected seek position", tc.expectedSeekPos, (action as ReconcilerAction.Seek).positionMs)
                }
                "SpeedRamp" -> {
                    assertTrue("${tc.name}: expected SpeedRamp", action is ReconcilerAction.SpeedRamp)
                    assertEquals("${tc.name}: expected speed factor", tc.expectedSpeed!!, (action as ReconcilerAction.SpeedRamp).factor, 0.001f)
                }
            }
        }
    }

    @Test
    fun `does not send SetPlayWhenReady or Seek while BUFFERING or not READY`() {
        // Local is BUFFERING with playWhenReady = false, but server is PLAYING
        val localBuffering = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5000L,
            playWhenReady = false,
            playbackState = Player.STATE_BUFFERING
        )
        val roomPlaying = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 50_000L)
        val action1 = JamReconciler.decide(localBuffering, roomPlaying, fixedClockSync, now = 50_000L)
        assertEquals(ReconcilerAction.Nothing, action1)

        // Local is BUFFERING with position drift > 2000ms
        val localBufferingDrift = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 10_000L,
            playWhenReady = true,
            playbackState = Player.STATE_BUFFERING
        )
        val action2 = JamReconciler.decide(localBufferingDrift, roomPlaying, fixedClockSync, now = 50_000L)
        assertEquals(ReconcilerAction.Nothing, action2)

        // Local is IDLE (not READY)
        val localIdle = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5000L,
            playWhenReady = false,
            playbackState = Player.STATE_IDLE
        )
        val action3 = JamReconciler.decide(localIdle, roomPlaying, fixedClockSync, now = 50_000L)
        assertEquals(ReconcilerAction.Nothing, action3)
    }

    @Test
    fun `does not re-trigger ChangeTrack while in LOADING state for same trackSeq`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 0L,
            playWhenReady = false,
            trackState = ReconcilerTrackState.LOADING,
            loadingTrackSeq = 2L
        )
        val room = makeRoom(songId = "song_2", trackSeq = 2L, status = JamPlaybackStatus.PLAYING, positionMs = 0L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertEquals(ReconcilerAction.Nothing, action)
    }

    @Test
    fun `re-triggers ChangeTrack while LOADING if server advanced to a newer trackSeq`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 0L,
            playWhenReady = false,
            trackState = ReconcilerTrackState.LOADING,
            loadingTrackSeq = 2L
        )
        // Server advanced to track 3
        val room = makeRoom(songId = "song_3", trackSeq = 3L, status = JamPlaybackStatus.PLAYING, positionMs = 0L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.ChangeTrack)
        val ct = action as ReconcilerAction.ChangeTrack
        assertEquals(3L, ct.trackSeq)
        assertFalse(ct.playWhenReady)
    }
}
