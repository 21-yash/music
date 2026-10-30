package com.synq.features.music.jam

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackState
import com.synq.features.music.sleeptimer.SleepTimerPlayerBridge
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamPartDTest {

    // ─────────────────────────────────────────────────────────────
    // Fakes
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

        fun emitEvent(event: JamTransport.Event) {
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            isConnected = true
            emitEvent(JamTransport.Event.Connected)
        }

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> {
            return if (isConnected) Result.success(Unit) else Result.failure(IllegalStateException("Socket is not connected"))
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
        var savedAvatar: String? = null
        var rejoinCandidateValue: String? = null

        override fun saveLastJam(code: String, avatarUrl: String?) {
            savedCode = code
            savedAvatar = avatarUrl
        }

        override fun getLastAvatarUrl(): String? = savedAvatar

        override fun rejoinCandidate(): String? = rejoinCandidateValue ?: savedCode

        override fun clearLastJam() {
            savedCode = null
            savedAvatar = null
            rejoinCandidateValue = null
        }
    }

    private class FakeJamPlayerBridge : JamPlayerBridge, SleepTimerPlayerBridge {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override var jamInterceptor: JamPlaybackInterceptor? = null
        override var onTrackEndedListener: (() -> Unit)? = null

        var currentPosition: Long = 0L
        var playWhenReady: Boolean = false
        var playbackStateInt: Int = Player.STATE_IDLE
        var speed: Float = 1.0f
        var duration: Long = 180_000L

        var paused = false
        var applyJamPauseCount = 0

        override fun getPlayerCurrentPosition(): Long = currentPosition
        override fun getPlayerPlayWhenReady(): Boolean = playWhenReady
        override fun getPlayerPlaybackState(): Int = playbackStateInt
        override fun getPlaybackSpeed(): Float = speed
        override fun getPlayerDuration(): Long = duration

        override fun playJamSyncSong(
            song: Song,
            initialPositionMs: Long,
            playWhenReady: Boolean,
            targetQid: String?
        ) {
            this.playWhenReady = playWhenReady
            this.currentPosition = initialPositionMs
            this.paused = !playWhenReady
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = playWhenReady,
                    playbackPositionMs = initialPositionMs
                )
            }
        }

        override fun prefetchTrack(song: Song) {}
        override fun stop() {
            paused = true
            playWhenReady = false
        }
        override fun pause() {
            paused = true
            playWhenReady = false
        }
        override fun applyJamPlay() {
            paused = false
            playWhenReady = true
            _playbackState.update { it.copy(isPlaying = true) }
        }
        override fun applyJamPause() {
            paused = true
            applyJamPauseCount++
            playWhenReady = false
            _playbackState.update { it.copy(isPlaying = false) }
        }
        override fun applyJamSeek(positionMs: Long) {
            currentPosition = positionMs
            _playbackState.update { it.copy(playbackPositionMs = positionMs) }
        }
        override fun applyJamSpeed(factor: Float) {
            speed = factor
        }
        override fun setJamQueue(
            jamQueue: List<Song>,
            targetSong: Song?,
            targetQid: String?,
            queueQids: List<String>,
            recommendedQids: Set<String>
        ) {}
        override fun stashPersonalQueue() {}
        override fun restorePersonalQueue() {}
        override fun setPlaybackSpeed(speed: Float) {
            this.speed = speed
        }
        override fun invalidateStreamCache(songId: String) {}
    }

    private fun sampleSong(id: String = "s1", title: String = "Test Song", duration: Int = 180): Song {
        return Song(
            id = id,
            title = title,
            artists = emptyList(),
            album = null,
            durationSeconds = duration,
            imageUrl = null,
            year = "2024",
            language = "English",
            hasLyrics = false,
            playCount = 0,
            label = "TestLabel",
            streamRef = "stream_$id",
            providerId = "test"
        )
    }

    // ─────────────────────────────────────────────────────────────
    // D3: JSONObject.optStringOrNull tests
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOptStringOrNull_handlesNullVariations() {
        val json = JSONObject().apply {
            put("nullObject", JSONObject.NULL)
            put("nullString", "null")
            put("emptyString", "")
            put("whitespaceString", "   ")
            put("validString", "https://avatar.example.com/pic.png")
        }

        assertNull(json.optStringOrNull("missingKey"))
        assertNull(json.optStringOrNull("nullObject"))
        assertNull(json.optStringOrNull("nullString"))
        assertNull(json.optStringOrNull("emptyString"))
        assertNull(json.optStringOrNull("whitespaceString"))
        assertEquals("https://avatar.example.com/pic.png", json.optStringOrNull("validString"))
    }

    // ─────────────────────────────────────────────────────────────
    // D4: Jam2Tick serverNow and fallback
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testJam2Tick_parsesServerNowWithFallback() {
        // Modern server tick with serverNow
        val modernJson = JSONObject().apply {
            put("code", "JAM001")
            put("serverNow", 1700000001000L)
            put("nowPlaying", JSONObject().apply {
                put("positionMs", 42000L)
                put("anchorServerTs", 1700000001000L)
                put("status", "playing")
                put("trackSeq", 5L)
            })
        }
        val modernTick = Jam2Tick.fromJson(modernJson)
        assertEquals(1700000001000L, modernTick.serverNow)
        assertEquals(1700000001000L, modernTick.serverTime)
        assertEquals(42000L, modernTick.nowPlaying.positionMs)

        // Legacy server tick with serverTime only
        val legacyJson = JSONObject().apply {
            put("code", "JAM001")
            put("serverTime", 1700000002000L)
            put("nowPlaying", JSONObject().apply {
                put("positionMs", 10000L)
                put("anchorServerTs", 1700000002000L)
                put("status", "paused")
                put("trackSeq", 3L)
            })
        }
        val legacyTick = Jam2Tick.fromJson(legacyJson)
        assertEquals(1700000002000L, legacyTick.serverNow)
        assertEquals(1700000002000L, legacyTick.serverTime)
    }

    // ─────────────────────────────────────────────────────────────
    // D1: RoomSnapshot / JamQueueItem safe parsing
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testRoomSnapshot_throwsJamDecodeExceptionOnCorruptedNowPlaying() {
        val corruptedJson = JSONObject().apply {
            put("code", "JAM001")
            put("version", 1L)
            put("serverNow", 1000L)
            put("hostUserId", "user_1")
            put("createdAt", 1000L)
            put("members", JSONArray())
            put("queue", JSONArray())
            put("history", JSONArray())
            put("nowPlaying", JSONObject().apply {
                put("status", "playing")
                put("trackSeq", 1L)
                put("positionMs", 0L)
                put("anchorServerTs", 1000L)
                put("item", JSONObject().apply {
                    // missing song object entirely
                    put("qid", "q1")
                })
            })
        }

        try {
            RoomSnapshot.fromJson(corruptedJson)
            fail("Expected JamDecodeException for corrupted nowPlaying item")
        } catch (e: JamDecodeException) {
            assertTrue(e.message!!.contains("nowPlaying"))
        }
    }

    @Test
    fun testRoomSnapshot_throwsJamDecodeExceptionOnCorruptedQueueItem() {
        val corruptedJson = JSONObject().apply {
            put("code", "JAM001")
            put("version", 1L)
            put("serverNow", 1000L)
            put("hostUserId", "user_1")
            put("createdAt", 1000L)
            put("members", JSONArray())
            put("queue", JSONArray().apply {
                put(JSONObject().apply {
                    put("qid", "corrupted_q")
                    // missing song
                })
            })
            put("history", JSONArray())
            put("nowPlaying", JSONObject().apply {
                put("status", "idle")
                put("trackSeq", 0L)
                put("positionMs", 0L)
                put("anchorServerTs", 1000L)
            })
        }

        try {
            RoomSnapshot.fromJson(corruptedJson)
            fail("Expected JamDecodeException for corrupted queue item")
        } catch (e: JamDecodeException) {
            assertTrue(e.message!!.contains("queue"))
        }
    }

    @Test
    fun testJamClient_triggersSyncOnParseError() = runTest {
        val transport = FakeJamTransport()
        val stateStore = JamStateStore("user_1")
        val clockSync = ClockSync()
        var fakeTimeMs = 1000L
        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = this.backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = this.testScheduler),
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { fakeTimeMs },
            wallClockNowMs = { fakeTimeMs }
        )

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", JSONObject().apply {
                put("code", "JAM001")
                put("version", 1L)
                put("serverNow", 1000L)
                put("hostUserId", "user_1")
                put("createdAt", 1000L)
                put("members", JSONArray())
                put("queue", JSONArray())
                put("history", JSONArray())
                put("nowPlaying", JSONObject().apply {
                    put("status", "idle")
                    put("trackSeq", 0L)
                    put("positionMs", 0L)
                    put("anchorServerTs", 1000L)
                })
            })
        })

        client.joinRoom("JAM001")
        runCurrent()

        // Emitting a ParseError event
        transport.emitEvent(JamTransport.Event.ParseError("jam2:state"))
        runCurrent()

        // Verify client attempted a jam2:sync command in response to ParseError
        val syncCommand = transport.sentCommands.find { it.first == "jam2:sync" }
        assertNotNull(syncCommand)
    }

    // ─────────────────────────────────────────────────────────────
    // D5: avatarUrl persistence in JamPreferences
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testJamPreferences_persistsAvatarUrl() {
        val prefs = FakeJamPreferences()
        prefs.saveLastJam("TEST99", "https://cdn.example.com/avatar.jpg")

        assertEquals("TEST99", prefs.rejoinCandidate())
        assertEquals("https://cdn.example.com/avatar.jpg", prefs.getLastAvatarUrl())

        prefs.clearLastJam()
        assertNull(prefs.rejoinCandidate())
        assertNull(prefs.getLastAvatarUrl())
    }

    // ─────────────────────────────────────────────────────────────
    // D6: Surface playback errors after retries exhausted
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPlaybackErrors_retriesExhaustedSurfacesToastAndStopsLooping() = runTest {
        val transport = FakeJamTransport()
        val playerBridge = FakeJamPlayerBridge()
        val prefs = FakeJamPreferences()
        val toasts = mutableListOf<String>()
        val stateStore = JamStateStore("user_1")
        var fakeTimeMs = 1000000L
        val clockSync = ClockSync(
            monotonicNowMs = { fakeTimeMs },
            wallClockNowMs = { fakeTimeMs }
        )

        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = this.backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = this.testScheduler),
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { fakeTimeMs },
            wallClockNowMs = { fakeTimeMs }
        )

        val sessionManager = JamSessionManager(
            playerManager = playerBridge,
            jamPreferences = prefs,
            coroutineScope = this.backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            currentTimeProvider = { fakeTimeMs },
            toastEmitter = { toasts.add(it) }
        )

        playerBridge.jamInterceptor = sessionManager

        val roomJson = JSONObject().apply {
            put("code", "ROOM1")
            put("version", 1L)
            put("serverNow", fakeTimeMs)
            put("hostUserId", "user_1")
            put("createdAt", fakeTimeMs)
            put("members", JSONArray().apply {
                put(JSONObject().apply {
                    put("userId", "user_1")
                    put("username", "Host")
                    put("isOnline", true)
                })
            })
            put("queue", JSONArray())
            put("history", JSONArray())
            put("nowPlaying", JSONObject().apply {
                put("status", "playing")
                put("trackSeq", 10L)
                put("positionMs", 10000L)
                put("anchorServerTs", fakeTimeMs)
                put("item", JSONObject().apply {
                    put("qid", "q10")
                    put("addedByUserId", "user_1")
                    put("addedAt", fakeTimeMs)
                    put("song", JSONObject().apply {
                        put("id", "song_fail")
                        put("title", "Broken Song")
                        put("providerId", "test")
                        put("duration", 200)
                    })
                })
            })
        }

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", roomJson)
        })

        sessionManager.joinJam("ROOM1", "user_1", "Host")
        runCurrent()

        stateStore.applySnapshot(RoomSnapshot.fromJson(roomJson), isEpoch = true)
        runCurrent()

        assertTrue("Must be in Jam", sessionManager.jamState.value.isInJam)

        // Transition track from LOADING to READY
        sessionManager.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()
        assertFalse(playerBridge.paused)

        val fakeError = mockk<PlaybackException>(relaxed = true)

        // 1st error trigger (retry 1/2)
        val handled1 = playerBridge.jamInterceptor?.onPlayerError(fakeError)
        assertTrue("Error 1 handled", handled1 == true)
        runCurrent()
        assertEquals(0, toasts.size)
        assertFalse(playerBridge.paused)

        // 2nd error trigger (retry 2/2)
        val handled2 = playerBridge.jamInterceptor?.onPlayerError(fakeError)
        assertTrue("Error 2 handled", handled2 == true)
        runCurrent()
        assertEquals(0, toasts.size)
        assertFalse(playerBridge.paused)

        // 3rd error trigger (retries exhausted)
        val handled3 = playerBridge.jamInterceptor?.onPlayerError(fakeError)
        assertTrue("Error 3 handled", handled3 == true)
        runCurrent()

        // Must show toast and pause player to stop looping
        assertEquals(1, toasts.size)
        assertEquals("Couldn't play Broken Song", toasts.first())
        assertTrue(playerBridge.paused)

        // Subsequent errors for the same trackSeq should not spam duplicate toasts
        playerBridge.jamInterceptor?.onPlayerError(fakeError)
        runCurrent()
        assertEquals(1, toasts.size)
    }
}
