package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PersonalQueueStash
import com.synq.features.music.playback.PlaybackState
import com.synq.features.music.sleeptimer.SleepTimerPlayerBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamF1F2Test {

    // ─────────────────────────────────────────────────────────────
    // F2: PersonalQueueStash pure unit tests
    // ─────────────────────────────────────────────────────────────

    private fun sampleSong(id: String): Song {
        return Song(
            id = id,
            title = "Song $id",
            artists = emptyList(),
            album = null,
            durationSeconds = 180,
            imageUrl = null,
            year = "2024",
            language = "English",
            hasLyrics = false,
            playCount = 0,
            label = "Label",
            streamRef = "ref_$id",
            providerId = "test"
        )
    }

    @Test
    fun testPersonalQueueStash_stashTwiceKeepsFirst() {
        val stash = PersonalQueueStash()
        val queue1 = listOf(sampleSong("1"), sampleSong("2"))
        val queue2 = listOf(sampleSong("3"))

        val firstSuccess = stash.stash(queue = queue1, currentIndex = 0)
        assertTrue(firstSuccess)
        assertTrue(stash.isStashed)

        // Second stash attempt must be ignored
        val secondSuccess = stash.stash(queue = queue2, currentIndex = 1)
        assertFalse(secondSuccess)

        val restored = stash.restore()
        assertNotNull(restored)
        assertEquals(2, restored!!.queue.size)
        assertEquals("1", restored.queue[0].id)
        assertFalse(stash.isStashed)
    }

    @Test
    fun testPersonalQueueStash_restoreWithoutStashIsNoOp() {
        val stash = PersonalQueueStash()
        assertFalse(stash.isStashed)
        val restored = stash.restore()
        assertNull(restored)
    }

    // ─────────────────────────────────────────────────────────────
    // F1: JamSessionManager failure recovery tests
    // ─────────────────────────────────────────────────────────────

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = true

        val sentCommands = mutableListOf<Pair<String, Any?>>()
        var nextEmitResult: Result<JSONObject> = Result.success(JSONObject().apply { put("ok", true) })
        var connectFailure: Throwable? = null

        fun emitEvent(event: JamTransport.Event) {
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            if (connectFailure != null) {
                isConnected = false
                emitEvent(JamTransport.Event.ConnectError(connectFailure!!))
            } else {
                isConnected = true
                emitEvent(JamTransport.Event.Connected)
            }
        }

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> {
            return if (isConnected) Result.success(Unit) else Result.failure(connectFailure ?: IllegalStateException("Socket is not connected"))
        }

        override fun disconnect() {
            isConnected = false
            emitEvent(JamTransport.Event.Disconnected("test"))
        }

        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
            sentCommands.add(event to data)
            return nextEmitResult
        }
    }

    private class FakeJamPreferences : JamPreferencesBridge {
        var savedCode: String? = null
        override fun saveLastJam(code: String, avatarUrl: String?) { savedCode = code }
        override fun clearLastJam() { savedCode = null }
        override fun rejoinCandidate(): String? = savedCode
    }

    private class FakeJamPlayerBridge : JamPlayerBridge, SleepTimerPlayerBridge {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override var jamInterceptor: JamPlaybackInterceptor? = null
        override var onTrackEndedListener: (() -> Unit)? = null

        var currentPosition: Long = 0L
        var playWhenReady: Boolean = false
        var playbackStateInt: Int = androidx.media3.common.Player.STATE_IDLE
        var speed: Float = 1.0f
        var duration: Long = 180_000L

        var stashed = false
        var restored = false

        override fun getPlayerCurrentPosition(): Long = currentPosition
        override fun getPlayerPlayWhenReady(): Boolean = playWhenReady
        override fun getPlayerPlaybackState(): Int = playbackStateInt
        override fun getPlaybackSpeed(): Float = speed
        override fun getPlayerDuration(): Long = duration

        override fun playJamSyncSong(song: Song, initialPositionMs: Long, playWhenReady: Boolean, targetQid: String?) {}
        override fun prefetchTrack(song: Song) {}
        override fun stop() {}
        override fun pause() {}
        override fun applyJamPlay() {}
        override fun applyJamPause() {}
        override fun applyJamSeek(positionMs: Long) {}
        override fun applyJamSpeed(factor: Float) {}
        override fun setJamQueue(jamQueue: List<Song>, targetSong: Song?, targetQid: String?, queueQids: List<String>, recommendedQids: Set<String>) {}

        override fun stashPersonalQueue() {
            stashed = true
        }

        override fun restorePersonalQueue() {
            restored = true
            stashed = false
        }

        override fun setPlaybackSpeed(speed: Float) { this.speed = speed }
        override fun invalidateStreamCache(songId: String) {}
    }

    @Test
    fun testF1_joinJamNotFound_resetsCleanlyAndAllowsSecondJoin() = runTest {
        val transport = FakeJamTransport()
        val playerBridge = FakeJamPlayerBridge()
        val prefs = FakeJamPreferences()
        val toasts = mutableListOf<String>()
        val stateStore = JamStateStore("user_1")
        val clockSync = ClockSync()
        var fakeTime = 1000L

        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = this.backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = this.testScheduler),
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { fakeTime },
            wallClockNowMs = { fakeTime }
        )

        val sessionManager = JamSessionManager(
            playerManager = playerBridge,
            jamPreferences = prefs,
            coroutineScope = this.backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            currentTimeProvider = { fakeTime },
            toastEmitter = { toasts.add(it) }
        )

        // First attempt fails with NOT_FOUND
        transport.nextEmitResult = Result.failure(JamProtocolException("NOT_FOUND", "Room not found"))

        var firstResult: Result<RoomSnapshot>? = null
        sessionManager.joinJam("BAD1", "user_1", "User1") { res ->
            firstResult = res
        }
        runCurrent()

        assertTrue(firstResult != null && firstResult!!.isFailure)
        assertFalse("Must not be in jam after failure", sessionManager.jamState.value.isInJam)
        assertNull("jamInterceptor must be cleared", playerBridge.jamInterceptor)
        assertTrue("Personal queue must be restored", playerBridge.restored)
        assertTrue("JamClient must be Disconnected", client.clientState.value is JamClientState.Disconnected)

        // Second attempt with valid room succeeds without "Already in a Jam session" error
        val validRoom = JSONObject().apply {
            put("code", "GOOD")
            put("version", 1L)
            put("serverNow", fakeTime)
            put("hostUserId", "user_host")
            put("createdAt", fakeTime)
            put("members", JSONArray())
            put("queue", JSONArray())
            put("history", JSONArray())
            put("nowPlaying", JSONObject().apply {
                put("status", "idle")
                put("trackSeq", 0L)
                put("positionMs", 0L)
                put("anchorServerTs", fakeTime)
            })
        }
        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", validRoom)
        })

        var secondResult: Result<RoomSnapshot>? = null
        sessionManager.joinJam("GOOD", "user_1", "User1") { res ->
            secondResult = res
        }
        runCurrent()

        assertTrue("Second join must succeed", secondResult != null && secondResult!!.isSuccess)
        assertTrue(sessionManager.jamState.value.isInJam)
        assertEquals("GOOD", sessionManager.jamState.value.sessionCode)
    }

    @Test
    fun testF1_createJamConnectFailure_resetsCleanlyAndAllowsRetry() = runTest {
        val transport = FakeJamTransport()
        val playerBridge = FakeJamPlayerBridge()
        val prefs = FakeJamPreferences()
        val toasts = mutableListOf<String>()
        val stateStore = JamStateStore("user_1")
        val clockSync = ClockSync()
        var fakeTime = 1000L

        transport.connectFailure = Exception("Connection refused")

        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = this.backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = this.testScheduler),
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { fakeTime },
            wallClockNowMs = { fakeTime }
        )

        val sessionManager = JamSessionManager(
            playerManager = playerBridge,
            jamPreferences = prefs,
            coroutineScope = this.backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            currentTimeProvider = { fakeTime },
            toastEmitter = { toasts.add(it) }
        )

        var firstResult: Result<RoomSnapshot>? = null
        sessionManager.createJam("user_1", "User1") { res ->
            firstResult = res
        }
        runCurrent()

        assertTrue(firstResult != null && firstResult!!.isFailure)
        assertFalse("Must not be in jam after connect failure", sessionManager.jamState.value.isInJam)
        assertNull(playerBridge.jamInterceptor)
        assertTrue(playerBridge.restored)

        // Fix connection and try again
        transport.connectFailure = null
        val validRoom = JSONObject().apply {
            put("code", "NEW1")
            put("version", 1L)
            put("serverNow", fakeTime)
            put("hostUserId", "user_1")
            put("createdAt", fakeTime)
            put("members", JSONArray())
            put("queue", JSONArray())
            put("history", JSONArray())
            put("nowPlaying", JSONObject().apply {
                put("status", "idle")
                put("trackSeq", 0L)
                put("positionMs", 0L)
                put("anchorServerTs", fakeTime)
            })
        }
        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", validRoom)
        })

        var secondResult: Result<RoomSnapshot>? = null
        sessionManager.createJam("user_1", "User1") { res ->
            secondResult = res
        }
        runCurrent()

        assertTrue("Second create must succeed", secondResult != null && secondResult!!.isSuccess)
        assertTrue(sessionManager.jamState.value.isInJam)
    }

    @Test
    fun testF1_leaveJamAfterFailedJoin_isCleanAndDoesNotThrow() = runTest {
        val transport = FakeJamTransport()
        val playerBridge = FakeJamPlayerBridge()
        val prefs = FakeJamPreferences()
        val stateStore = JamStateStore("user_1")
        val clockSync = ClockSync()
        var fakeTime = 1000L

        transport.nextEmitResult = Result.failure(JamProtocolException("NOT_FOUND", "Room not found"))

        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = this.backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = this.testScheduler),
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { fakeTime },
            wallClockNowMs = { fakeTime }
        )

        val sessionManager = JamSessionManager(
            playerManager = playerBridge,
            jamPreferences = prefs,
            coroutineScope = this.backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            currentTimeProvider = { fakeTime }
        )

        sessionManager.joinJam("FAIL", "user_1", "User1")
        runCurrent()

        // Calling leaveJam right after failed join must not throw and ensure state is clean
        sessionManager.leaveJam()
        runCurrent()

        assertFalse(sessionManager.jamState.value.isInJam)
        assertNull(playerBridge.jamInterceptor)
    }
}

