package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackCommandRouter
import com.synq.features.music.playback.PlaybackState
import com.synq.features.music.playback.ReResolveGuard
import com.synq.features.music.playback.isRangeFullyCovered
import com.synq.features.music.sleeptimer.SleepTimerManager
import com.synq.features.music.sleeptimer.SleepTimerPlayerBridge
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    // ─── Task F4: ReResolveGuard Production Tests ─────────────────────────

    @Test
    fun testReResolveGuard_allowsOncePerSongId_resetsOnNewSongOrReady() {
        val guard = ReResolveGuard()

        // 1st error on song 1 -> allowed
        assertTrue(guard.shouldReResolve("song_1"))
        assertEquals("song_1", guard.attemptedFor)

        // 2nd error on song 1 -> rejected
        assertFalse(guard.shouldReResolve("song_1"))

        // Error on song 2 -> allowed
        assertTrue(guard.shouldReResolve("song_2"))
        assertEquals("song_2", guard.attemptedFor)

        // 2nd error on song 2 -> rejected
        assertFalse(guard.shouldReResolve("song_2"))

        // New song started -> resets guard for previous songs
        guard.onSongStarted("song_3")
        assertNull(guard.attemptedFor)
        assertTrue(guard.shouldReResolve("song_1"))

        // On ready -> resets guard
        guard.onReady()
        assertNull(guard.attemptedFor)
        assertTrue(guard.shouldReResolve("song_1"))
    }

    // ─── Task F9: Real JamSessionManager Reconciliation Suppression ────────

    @Test
    fun testRealJamSessionManager_unplayableTrackStopsReconcileCalls_newTrackSeqTriggersLoad() = runTest {
        val transport = FakeJamTransport()
        val playerBridge = FakeJamPlayerBridgeForF9()
        val prefs = FakeJamPreferencesForF9()
        val stateStore = JamStateStore("user_1")
        val clockSync = ClockSync()
        var fakeTime = 1000L

        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { fakeTime },
            wallClockNowMs = { fakeTime }
        )

        val sessionManager = JamSessionManager(
            playerManager = playerBridge,
            jamPreferences = prefs,
            coroutineScope = backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            currentTimeProvider = { fakeTime }
        )

        // 1. Join Jam with trackSeq 100L
        val song1 = Song("s1", "Song 1", emptyList(), null, 180, null, "2024", "en", false, 0, "L", "ref1", "test")
        val snap1 = createSampleSnapshot("ROOM", 1L, trackSeq = 100L, song = song1)
        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", JSONObject().apply {
                put("code", "ROOM")
                put("version", 1L)
                put("hostUserId", "user_1")
                put("createdAt", 1000L)
                put("members", JSONArray())
                put("defaults", JSONObject().apply {
                    put("queueAdd", true)
                    put("playback", false)
                    put("queueManage", false)
                })
                put("queue", JSONArray())
                put("history", JSONArray())
                put("nowPlaying", JSONObject().apply {
                    put("item", JSONObject().apply {
                        put("qid", "q1")
                        put("addedByUserId", "user_1")
                        put("addedAt", 1000L)
                        put("recommended", false)
                        put("song", JSONObject().apply {
                            put("id", "s1")
                            put("title", "Song 1")
                            put("artists", JSONArray())
                            put("durationSeconds", 180)
                            put("providerId", "test")
                            put("streamRef", "ref1")
                        })
                    })
                    put("status", "PLAYING")
                    put("positionMs", 0L)
                    put("anchorServerTs", 1000L)
                    put("trackSeq", 100L)
                })
                put("serverNow", 1000L)
            })
        })
        client.joinRoom("ROOM")
        runCurrent()

        // Sync queue so trackSeq 100L is loading
        sessionManager.syncJamQueue(snap1, force = true)
        runCurrent()

        // Exhaust retries for trackSeq 100L (loading timeout after 15s, 30s, 45s)
        fakeTime += 16_000L
        sessionManager.checkLoadingTimeout(fakeTime) // Retry 1
        runCurrent()

        fakeTime += 16_000L
        sessionManager.checkLoadingTimeout(fakeTime) // Retry 2
        runCurrent()

        fakeTime += 16_000L
        sessionManager.checkLoadingTimeout(fakeTime) // Max retries exhausted -> marks unplayableTrackSeq = 100L
        runCurrent()

        val callCountAfterMaxRetries = playerBridge.playJamSyncCalls.size

        // Repeated reconcilePlayback calls for the SAME trackSeq 100L do NOT grow call count
        repeat(5) {
            fakeTime += 1000L
            sessionManager.syncJamQueue(snap1)
            runCurrent()
        }
        assertEquals(callCountAfterMaxRetries, playerBridge.playJamSyncCalls.size)

        // Now server advances to new trackSeq 101L
        val song2 = Song("s2", "Song 2", emptyList(), null, 200, null, "2024", "en", false, 0, "L", "ref2", "test")
        val snap2 = createSampleSnapshot("ROOM", 2L, trackSeq = 101L, song = song2)
        stateStore.applySnapshot(snap2, isEpoch = false)
        runCurrent()

        // Reconcile on new trackSeq triggers a load
        assertTrue(playerBridge.playJamSyncCalls.size > callCountAfterMaxRetries)
        assertEquals("s2", playerBridge.playJamSyncCalls.last().id)
    }

    private fun createSampleSnapshot(code: String, version: Long, trackSeq: Long, song: Song? = null): RoomSnapshot {
        val item = song?.let {
            JamQueueItem(
                qid = "q_${it.id}",
                song = it,
                addedByUserId = "user_1",
                addedAt = 1000L,
                recommended = false
            )
        }
        return RoomSnapshot(
            code = code,
            version = version,
            serverNow = 1000L,
            hostUserId = "user_1",
            createdAt = 1000L,
            members = emptyList(),
            defaults = JamPermissions(queueAdd = true, playback = false, queueManage = false),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = item,
                status = if (item != null) JamPlaybackStatus.PLAYING else JamPlaybackStatus.IDLE,
                positionMs = 0L,
                anchorServerTs = 1000L,
                trackSeq = trackSeq
            )
        )
    }

    private class FakeJamPlayerBridgeForF9 : JamPlayerBridge, SleepTimerPlayerBridge {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override var jamInterceptor: JamPlaybackInterceptor? = null
        override var onTrackEndedListener: (() -> Unit)? = null

        var currentPosition: Long = 0L
        var playWhenReady: Boolean = false
        var playbackStateInt: Int = androidx.media3.common.Player.STATE_BUFFERING
        var speed: Float = 1.0f
        var duration: Long = 180_000L

        val playJamSyncCalls = mutableListOf<Song>()

        override fun getPlayerCurrentPosition(): Long = currentPosition
        override fun getPlayerPlayWhenReady(): Boolean = playWhenReady
        override fun getPlayerPlaybackState(): Int = playbackStateInt
        override fun getPlaybackSpeed(): Float = speed
        override fun getPlayerDuration(): Long = duration

        override fun playJamSyncSong(song: Song, initialPositionMs: Long, playWhenReady: Boolean, targetQid: String?, forceRefresh: Boolean) {
            playJamSyncCalls.add(song)
        }
        override fun prefetchTrack(song: Song) {}
        override fun stop() {}
        override fun pause() {}
        override fun applyJamPlay() {}
        override fun applyJamPause() {}
        override fun applyJamSeek(positionMs: Long) {}
        override fun applyJamSpeed(factor: Float) {}
        override fun setJamQueue(jamQueue: List<Song>, targetSong: Song?, targetQid: String?, queueQids: List<String>, recommendedQids: Set<String>) {}
        override fun stashPersonalQueue() {}
        override fun restorePersonalQueue() {}
        override fun setPlaybackSpeed(speed: Float) { this.speed = speed }
        override fun invalidateStreamCache(songId: String) {}
    }

    private class FakeJamPreferencesForF9 : JamPreferencesBridge {
        override fun saveLastJam(code: String, avatarUrl: String?) {}
        override fun clearLastJam() {}
        override fun rejoinCandidate(): String? = null
    }

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            replay = 10,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: Flow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = true

        var nextEmitResult: Result<JSONObject> = Result.success(JSONObject().apply { put("ok", true) })

        fun emitEvent(event: JamTransport.Event) {
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            _events.tryEmit(JamTransport.Event.Connected)
        }

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> = nextEmitResult
    }
}
