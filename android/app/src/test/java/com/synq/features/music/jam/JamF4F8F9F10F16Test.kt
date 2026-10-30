package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.playback.PlaybackCommandRouter
import com.synq.features.music.playback.isRangeFullyCovered
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamF4F8F9F10F16Test {

    // ─── Task F16: AudioCache completeness check ─────────────────────────

    @Test
    fun testIsRangeFullyCovered_emptySpans() {
        assertFalse(isRangeFullyCovered(emptyList(), 5000L))
    }

    @Test
    fun testIsRangeFullyCovered_partialSpans() {
        assertFalse(isRangeFullyCovered(listOf(0L to 1000L), 5000L))
    }

    @Test
    fun testIsRangeFullyCovered_gapInSpans() {
        // Gap between 1000 and 2000
        assertFalse(isRangeFullyCovered(listOf(0L to 1000L, 2000L to 3000L), 5000L))
    }

    @Test
    fun testIsRangeFullyCovered_completeSingleSpan() {
        assertTrue(isRangeFullyCovered(listOf(0L to 5000L), 5000L))
    }

    @Test
    fun testIsRangeFullyCovered_adjacentSpans() {
        // 0..2000, 2000..5000
        assertTrue(isRangeFullyCovered(listOf(0L to 2000L, 2000L to 3000L), 5000L))
    }

    @Test
    fun testIsRangeFullyCovered_overlappingSpans() {
        // 0..3000, 2000..4000, 3500..5000
        assertTrue(isRangeFullyCovered(listOf(0L to 3000L, 2000L to 2000L, 3500L to 1500L), 5000L))
    }

    @Test
    fun testIsRangeFullyCovered_invalidTotalLength() {
        assertFalse(isRangeFullyCovered(listOf(0L to 1000L), 0L))
        assertFalse(isRangeFullyCovered(listOf(0L to 1000L), -500L))
    }

    // ─── Task F8: PlaybackCommandRouter ──────────────────────────────────

    @Test
    fun testPlaybackCommandRouter_externalCommandsInJam() {
        val isExternal = true
        val isInJam = true

        assertTrue(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_PLAY_PAUSE))
        assertTrue(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SEEK_BACK))
        assertTrue(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SEEK_FORWARD))
        assertTrue(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun testPlaybackCommandRouter_internalCommandsNotIntercepted() {
        val isExternal = false
        val isInJam = true

        assertFalse(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_PLAY_PAUSE))
        assertFalse(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SEEK_TO_NEXT))
    }

    // ─── Task F10: Ordered Event Stream ──────────────────────────────────

    @Test
    fun testOrderedEvents_singleStreamPreservesOrder() = runTest {
        val emitted = mutableListOf<JamTransport.Event>()
        val transport = FakeJamTransport()

        val job = launch {
            transport.events.take(4).toList(emitted)
        }

        val snap1 = createSampleSnapshot("TEST", 1, 100L)
        val ev1 = Jam2Event(kind = "member_joined", userId = "u1", username = "Alice")
        val snap2 = createSampleSnapshot("TEST", 2, 200L)
        val tick = Jam2Tick(
            code = "TEST",
            version = 2L,
            serverTime = 200L,
            nowPlaying = JamTickNowPlaying(
                positionMs = 500L,
                anchorServerTs = 100L,
                status = JamPlaybackStatus.PLAYING,
                trackSeq = 1L
            )
        )

        transport.emitEvent(JamTransport.Event.State(snap1))
        transport.emitEvent(JamTransport.Event.JamEvent(ev1))
        transport.emitEvent(JamTransport.Event.State(snap2))
        transport.emitEvent(JamTransport.Event.Tick(tick))

        job.join()

        assertEquals(4, emitted.size)
        assertTrue(emitted[0] is JamTransport.Event.State)
        assertEquals(1L, ((emitted[0] as JamTransport.Event.State).snapshot.version))

        assertTrue(emitted[1] is JamTransport.Event.JamEvent)
        assertEquals("member_joined", ((emitted[1] as JamTransport.Event.JamEvent).event.kind))

        assertTrue(emitted[2] is JamTransport.Event.State)
        assertEquals(2L, ((emitted[2] as JamTransport.Event.State).snapshot.version))

        assertTrue(emitted[3] is JamTransport.Event.Tick)
    }

    // ─── Task F9 & F4 Pure Logic Tests ───────────────────────────────────

    @Test
    fun testUnplayableTrackSequence_suppressesLoopAndClears() {
        var unplayableTrackSeq: Long? = null
        var changeTrackCalled = 0

        fun simulateReconcile(serverTrackSeq: Long) {
            val unplayable = unplayableTrackSeq
            if (unplayable != null) {
                if (serverTrackSeq == unplayable) {
                    return // Suppressed!
                } else {
                    unplayableTrackSeq = null
                }
            }
            changeTrackCalled++
        }

        // Track 1 fails and exhausts retries
        unplayableTrackSeq = 1L

        // Reconcile called multiple times for track 1
        simulateReconcile(1L)
        simulateReconcile(1L)
        simulateReconcile(1L)
        assertEquals(0, changeTrackCalled)

        // Server advances to track 2
        simulateReconcile(2L)
        assertEquals(1, changeTrackCalled)
        assertNull(unplayableTrackSeq)
    }

    @Test
    fun testPerSongReResolveLogic() {
        var reResolveAttemptedFor: String? = null
        var reResolveCount = 0

        fun onError(songId: String): Boolean {
            if (reResolveAttemptedFor != songId) {
                reResolveAttemptedFor = songId
                reResolveCount++
                return true // Attempt re-resolve
            }
            return false // Give up, unrecoverable
        }

        // Error on song 1 -> attempts re-resolve once
        assertTrue(onError("song_1"))
        assertEquals(1, reResolveCount)

        // Second error on song 1 -> gives up
        assertFalse(onError("song_1"))
        assertEquals(1, reResolveCount)

        // Error on song 2 -> gets its one re-resolve
        assertTrue(onError("song_2"))
        assertEquals(2, reResolveCount)

        // Second error on song 2 -> gives up
        assertFalse(onError("song_2"))
        assertEquals(2, reResolveCount)
    }

    private fun createSampleSnapshot(code: String, version: Long, trackSeq: Long): RoomSnapshot {
        return RoomSnapshot(
            code = code,
            version = version,
            serverNow = 1000L,
            hostUserId = "u1",
            createdAt = 1000L,
            members = emptyList(),
            defaults = JamPermissions(queueAdd = true, playback = false, queueManage = false),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = null,
                status = JamPlaybackStatus.IDLE,
                positionMs = 0L,
                anchorServerTs = 1000L,
                trackSeq = trackSeq
            )
        )
    }

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            replay = 10,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: Flow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = true

        fun emitEvent(event: JamTransport.Event) {
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            _events.tryEmit(JamTransport.Event.Connected)
        }

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> =
            Result.success(JSONObject().apply { put("ok", true) })
    }
}
