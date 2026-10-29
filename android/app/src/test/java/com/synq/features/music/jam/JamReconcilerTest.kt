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

    @Test
    fun `returns SpeedRamp 0_98 when local is ahead between 300ms and 2000ms`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5800L, // 800ms ahead
            playWhenReady = true,
            playbackSpeed = 1.0f
        )
        // Expected pos = 5000ms
        val room = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.SpeedRamp)
        assertEquals(0.98f, (action as ReconcilerAction.SpeedRamp).factor, 0.001f)
    }

    @Test
    fun `returns SpeedRamp 1_02 when local is behind between 300ms and 2000ms`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 4300L, // 700ms behind
            playWhenReady = true,
            playbackSpeed = 1.0f
        )
        // Expected pos = 5000ms
        val room = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.SpeedRamp)
        assertEquals(1.02f, (action as ReconcilerAction.SpeedRamp).factor, 0.001f)
    }

    @Test
    fun `returns SpeedRamp 1_0 to restore normal speed once delta is within 300ms`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5100L, // 100ms ahead (within 300ms)
            playWhenReady = true,
            playbackSpeed = 0.98f // Was previously slowed down
        )
        // Expected pos = 5000ms
        val room = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertTrue(action is ReconcilerAction.SpeedRamp)
        assertEquals(1.0f, (action as ReconcilerAction.SpeedRamp).factor, 0.001f)
    }

    @Test
    fun `returns Nothing when already in sync within 300ms and normal speed`() {
        val local = LocalPlayerState(
            currentSongId = "song_1",
            currentTrackSeq = 1L,
            currentPositionMs = 5050L, // 50ms ahead
            playWhenReady = true,
            playbackSpeed = 1.0f
        )
        val room = makeRoom(songId = "song_1", trackSeq = 1L, status = JamPlaybackStatus.PLAYING, positionMs = 5000L, anchorTs = 50_000L)

        val action = JamReconciler.decide(local, room, fixedClockSync, now = 50_000L)
        assertEquals(ReconcilerAction.Nothing, action)
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
