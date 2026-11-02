package com.synq.features.music.jam

import com.synq.data.remote.TokenRefreshResult
import com.synq.data.repository.AuthRepository
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamClientStateMachineTest {

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = false

        val emittedEvents = mutableListOf<Pair<String, Any?>>()
        var nextEmitResult: Result<JSONObject> = Result.success(JSONObject().apply { put("ok", true) })

        fun emitEvent(event: JamTransport.Event) {
            if (event is JamTransport.Event.Disconnected) {
                isConnected = false
            }
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            if (awaitConnectedResult.isSuccess) {
                isConnected = true
                emitEvent(JamTransport.Event.Connected)
            } else {
                isConnected = false
            }
        }

        var awaitConnectedResult: Result<Unit> = Result.success(Unit)

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> {
            return awaitConnectedResult
        }

        override fun disconnect() {
            isConnected = false
        }

        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
            emittedEvents.add(event to data)
            return nextEmitResult
        }
    }

    private class FakeJamPlayerBridge : JamPlayerBridge {
        override val playbackState = kotlinx.coroutines.flow.MutableStateFlow(PlaybackState())
        override var jamInterceptor: JamPlaybackInterceptor? = null
        override fun getPlayerCurrentPosition(): Long = 0L
        override fun getPlayerPlayWhenReady(): Boolean = false
        override fun getPlayerPlaybackState(): Int = 1
        override fun getPlaybackSpeed(): Float = 1.0f
        override fun getPlayerDuration(): Long = 100000L
        override fun playJamSyncSong(song: Song, initialPositionMs: Long, playWhenReady: Boolean, targetQid: String?, forceRefresh: Boolean) {}
        override fun prefetchTrack(song: Song) {}
        override fun stop() {}
        override fun applyJamPlay() {}
        override fun applyJamPause() {}
        override fun applyJamSeek(positionMs: Long) {}
        override fun applyJamSpeed(factor: Float) {}
        override fun setJamQueue(jamQueue: List<Song>, targetSong: Song?, targetQid: String?, queueQids: List<String>, recommendedQids: Set<String>) {}
        override fun stashPersonalQueue() {}
        override fun restorePersonalQueue() {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun invalidateStreamCache(songId: String) {}
    }

    private class FakeJamPreferencesBridge : JamPreferencesBridge {
        override fun saveLastJam(code: String, avatarUrl: String?) {}
        override fun clearLastJam() {}
        override fun rejoinCandidate(): String? = null
        override fun getLastAvatarUrl(): String? = null
    }

    private lateinit var transport: FakeJamTransport
    private lateinit var stateStore: JamStateStore
    private lateinit var clockSync: ClockSync

    private var currentTime: Long = 1000L
    private var tokenToProvide: String? = "valid_token"
    private var tokenResultToProvide: TokenRefreshResult? = null

    @Before
    fun setup() {
        transport = FakeJamTransport()
        stateStore = JamStateStore(myUserId = "user_me")
        clockSync = ClockSync(
            monotonicNowMs = { currentTime },
            wallClockNowMs = { currentTime }
        )
    }

    private fun kotlinx.coroutines.test.TestScope.createClient(autoClockSync: Boolean = false): JamClient {
        return JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = testScheduler),
            tokenProvider = { tokenToProvide },
            tokenResultProvider = {
                tokenResultToProvide ?: if (tokenToProvide != null) {
                    TokenRefreshResult.Success(tokenToProvide!!)
                } else {
                    TokenRefreshResult.Unauthorized
                }
            },
            autoClockSync = autoClockSync,
            monotonicNowMs = { currentTime },
            wallClockNowMs = { currentTime }
        )
    }

    private fun makeRoomJson(code: String, hostId: String): JSONObject {
        return JSONObject().apply {
            put("ok", true)
            put("room", JSONObject().apply {
                put("code", code)
                put("version", 1)
                put("serverNow", 1000L)
                put("hostUserId", hostId)
                put("createdAt", 1000L)
                put("members", org.json.JSONArray())
                put("defaults", JSONObject())
                put("queue", org.json.JSONArray())
                put("history", org.json.JSONArray())
                put("nowPlaying", JSONObject().apply {
                    put("status", "idle")
                    put("positionMs", 0)
                    put("anchorServerTs", 0)
                    put("trackSeq", 0)
                })
            })
        }
    }

    @Test
    fun `initial state is Disconnected`() = runTest {
        val client = createClient()
        assertEquals(JamClientState.Disconnected, client.clientState.value)
    }

    @Test
    fun `successful createRoom transitions Connecting to Active HOST`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("ABCD", "user_me"))

        val res = client.createRoom()
        runCurrent()

        assertTrue(res.isSuccess)
        val state = client.clientState.value
        assertTrue(state is JamClientState.Active)
        assertEquals("ABCD", (state as JamClientState.Active).code)
        assertEquals(JamRole.HOST, state.role)
    }

    @Test
    fun `successful joinRoom transitions Joining to Active GUEST`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("JOIN", "other_host"))

        val res = client.joinRoom("JOIN")
        runCurrent()

        assertTrue(res.isSuccess)
        val state = client.clientState.value
        assertTrue(state is JamClientState.Active)
        assertEquals("JOIN", (state as JamClientState.Active).code)
        assertEquals(JamRole.GUEST, state.role)
    }

    @Test
    fun `connection loss transitions to Reconnecting with attempt 1`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("RECN", "user_me"))

        client.createRoom()
        runCurrent()

        // Simulate transport disconnect
        transport.emitEvent(JamTransport.Event.Disconnected("transport close"))
        runCurrent()

        val state = client.clientState.value
        assertTrue(state is JamClientState.Reconnecting)
        assertEquals("RECN", (state as JamClientState.Reconnecting).code)
        assertEquals(1, state.attempt)
    }

    @Test
    fun `server closed event transitions to Closed with reason`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("CLOS", "other_host"))

        client.joinRoom("CLOS")
        runCurrent()

        // Server emits closed { reason: "ended" }
        transport.emitEvent(JamTransport.Event.Closed("ended"))
        runCurrent()

        val state = client.clientState.value
        assertTrue(state is JamClientState.Closed)
        assertEquals("ended", (state as JamClientState.Closed).reason)
        assertEquals("The host ended this Jam", state.message)
    }

    @Test
    fun `when DISCONNECTED or CLOSED sendCommand fails fast with error Reconnecting try again`() = runTest {
        val client = createClient()
        // Disconnected
        val res = client.sendCommand("play")
        assertTrue(res.isFailure)
        assertEquals("Reconnecting… try again", res.exceptionOrNull()?.message)
    }

    @Test
    fun `when RECONNECTING sendCommand waits up to 4s and fails after timeout`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("FAST", "user_me"))

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        // Drop connection to enter Reconnecting
        transport.emitEvent(JamTransport.Event.Disconnected("network drop"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Keep socket connection failing so reconnect loop stays in Reconnecting
        transport.awaitConnectedResult = Result.failure(IllegalStateException("No internet"))

        var commandResult: Result<Long>? = null
        val cmdJob = launch {
            commandResult = client.sendCommand("play")
        }
        runCurrent()
        assertNull("Command should be waiting, not failed immediately", commandResult)

        // Advance 4001ms to trigger timeout
        advanceTimeBy(4001L)
        runCurrent()

        assertTrue(commandResult!!.isFailure)
        assertEquals("Reconnecting… try again", commandResult!!.exceptionOrNull()?.message)
        cmdJob.join()
    }

    @Test
    fun `when RECONNECTING sendCommand succeeds once active state restored within 4s`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("RECONNECT_CMD", "user_me"))

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        // Drop connection to enter Reconnecting
        transport.emitEvent(JamTransport.Event.Disconnected("network drop"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        transport.emittedEvents.clear()
        transport.nextEmitResult = Result.success(JSONObject().apply { put("version", 42L) })

        var commandResult: Result<Long>? = null
        val cmdJob = launch {
            commandResult = client.sendCommand("play")
        }
        runCurrent()
        assertNull("Command should wait during reconnecting", commandResult)

        // Restore active state within 1s
        advanceTimeBy(1000L)
        client.setActiveForTesting("RECONNECT_CMD", JamRole.HOST)
        runCurrent()

        assertTrue("Command should succeed once Active is restored", commandResult!!.isSuccess)
        assertEquals(42L, commandResult!!.getOrNull())
        assertEquals(1, transport.emittedEvents.filter { it.first == "jam2:cmd" }.size)
        cmdJob.join()
    }

    @Test
    fun `on rejoin NOT_FOUND retries up to 4 times 2s apart before declaring room ended`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("RETRY", "user_me"))

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        // Prepare NOT_FOUND failure for rejoin
        transport.nextEmitResult = Result.failure(JamProtocolException("NOT_FOUND", "Room not found"))

        // Disconnect
        transport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Advance 2s to allow initial backoff (1s+jitter) and attempt 1 to execute
        advanceTimeBy(2000L)
        runCurrent()
        assertTrue("Still reconnecting after attempt 1 NOT_FOUND", client.clientState.value is JamClientState.Reconnecting)

        // Advance 2s: Attempt 2
        advanceTimeBy(2050L)
        runCurrent()
        assertTrue("Still reconnecting after attempt 2 NOT_FOUND", client.clientState.value is JamClientState.Reconnecting)

        // Advance 2s: Attempt 3
        advanceTimeBy(2050L)
        runCurrent()
        assertTrue("Still reconnecting after attempt 3 NOT_FOUND", client.clientState.value is JamClientState.Reconnecting)

        // Advance 2s: Attempt 4
        advanceTimeBy(2050L)
        runCurrent()
        assertTrue("Still reconnecting after attempt 4 NOT_FOUND", client.clientState.value is JamClientState.Reconnecting)

        // Advance 2s: Attempt 5 (exceeds 4 retries) -> must declare room Closed("ended", "This Jam has ended")
        advanceTimeBy(2050L)
        runCurrent()

        val state = client.clientState.value
        assertTrue("State must be Closed after 4 retries", state is JamClientState.Closed)
        val closed = state as JamClientState.Closed
        assertEquals("ended", closed.reason)
        assertTrue(closed.message.contains("This Jam has ended") || closed.message.contains("ended"))
    }

    @Test
    fun `token refresh failure transitions Reconnecting to Closed auth_failed`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("AUTH", "user_me"))

        client.createRoom()
        runCurrent()

        // Disconnect
        transport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()

        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Refresh fails
        tokenToProvide = null

        // Advance past initial reconnect delay (1s + 500ms jitter)
        advanceTimeBy(2000L)
        runCurrent()

        val state = client.clientState.value
        assertTrue(state is JamClientState.Closed)
        assertEquals("auth_failed", (state as JamClientState.Closed).reason)
    }

    @Test
    fun `transient token refresh in reconnect loop keeps retrying and does not close Jam`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("AUTH_TRANSIENT", "user_me"))

        client.createRoom()
        runCurrent()

        // Disconnect
        transport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()

        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Transient failure
        tokenResultToProvide = TokenRefreshResult.Transient(null)

        // Advance through reconnect delay
        advanceTimeBy(2000L)
        runCurrent()

        // Should STILL be in Reconnecting state, NOT Closed("auth_failed")
        assertTrue("State should remain Reconnecting on transient error", client.clientState.value is JamClientState.Reconnecting)

        // Advance through another backoff
        advanceTimeBy(4000L)
        runCurrent()
        assertTrue("State should still remain Reconnecting", client.clientState.value is JamClientState.Reconnecting)

        // Now auth refresh returns Unauthorized -> immediately closes
        tokenResultToProvide = TokenRefreshResult.Unauthorized
        advanceTimeBy(5000L)
        runCurrent()

        val finalState = client.clientState.value
        assertTrue("State should be Closed on real Unauthorized", finalState is JamClientState.Closed)
        assertEquals("auth_failed", (finalState as JamClientState.Closed).reason)
    }

    @Test
    fun `reconnect loop does not restart itself on network restore but skips backoff delay`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("RECONNECT_NO_RESTART", "user_me"))

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        // Drop connection
        transport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        val initialJob = client.reconnectJob
        assertNotNull("Reconnect job should be created", initialJob)
        assertTrue("Reconnect job should be active", initialJob!!.isActive)

        // Keep socket connection failing on attempt 1 so it enters backoff for attempt 2
        transport.awaitConnectedResult = Result.failure(IllegalStateException("No network"))
        advanceTimeBy(2000L)
        runCurrent()

        // It is now waiting in backoff for attempt 2 (e.g. 2s+ delay)
        assertTrue(client.clientState.value is JamClientState.Reconnecting)
        assertEquals("Initial reconnect job should still be the running job", initialJob, client.reconnectJob)

        // Now network restores, socket connect is working, and rejoin will succeed
        transport.awaitConnectedResult = Result.success(Unit)
        transport.nextEmitResult = Result.success(makeRoomJson("RECONNECT_NO_RESTART", "user_me"))

        // Call onNetworkRestored multiple times rapidly
        client.onNetworkRestored()
        client.onNetworkRestored()
        client.onNetworkRestored()

        // The job should NOT have been cancelled or recreated
        assertEquals("Reconnect job instance must not change or be cancelled", initialJob, client.reconnectJob)
        assertTrue("Reconnect job should still be active without being cancelled", initialJob.isActive)

        // Running current immediately processes the skipped backoff without waiting 2000ms+
        runCurrent()

        // Reconnect succeeded into Active state
        val finalState = client.clientState.value
        assertTrue("Client should be active after skipped backoff", finalState is JamClientState.Active)
        assertEquals("RECONNECT_NO_RESTART", (finalState as JamClientState.Active).code)
    }

    @Test
    fun `JamSessionManager default JamClient construction forwards TokenRefreshResult Transient from AuthRepository`() = runTest {
        val mockAuthRepo = mockk<AuthRepository>()
        coEvery { mockAuthRepo.getFreshAccessTokenResult() } returns TokenRefreshResult.Success("valid_token")
        coEvery { mockAuthRepo.getFreshAccessToken() } returns "valid_token"

        val fakeTransport = FakeJamTransport()
        fakeTransport.nextEmitResult = Result.success(makeRoomJson("PROD_WIRING", "user_me"))

        val fakePlayerManager = FakeJamPlayerBridge()
        val fakePrefs = FakeJamPreferencesBridge()

        val jsm = JamSessionManager(
            playerManager = fakePlayerManager,
            authRepository = mockAuthRepo,
            networkMonitorProvider = null,
            jamPreferences = fakePrefs,
            context = null,
            coroutineScope = backgroundScope,
            transportOverride = fakeTransport,
            jamClientOverride = null,
            clockSyncOverride = ClockSync(monotonicNowMs = { 1000L }, wallClockNowMs = { 1000L }),
            stateStoreOverride = JamStateStore("user_me"),
            currentTimeProvider = { 1000L },
            ioDispatcher = StandardTestDispatcher(scheduler = testScheduler)
        )

        // Create room to get into Active state
        jsm.jamClient.createRoom()
        runCurrent()
        assertTrue(jsm.jamClient.clientState.value is JamClientState.Active)

        // Now token refresh returns Transient
        coEvery { mockAuthRepo.getFreshAccessTokenResult() } returns TokenRefreshResult.Transient(null)
        coEvery { mockAuthRepo.getFreshAccessToken() } returns null

        // Disconnect transport
        fakeTransport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()
        assertTrue(jsm.jamClient.clientState.value is JamClientState.Reconnecting)

        // Advance time during reconnect loop
        advanceTimeBy(2500L)
        runCurrent()

        assertTrue("Client must remain Reconnecting on Transient result", jsm.jamClient.clientState.value is JamClientState.Reconnecting)

        // Now mock returns Unauthorized
        coEvery { mockAuthRepo.getFreshAccessTokenResult() } returns TokenRefreshResult.Unauthorized
        advanceTimeBy(10000L)
        runCurrent()

        assertFalse("Jam session must be torn down on Unauthorized", jsm.jamState.value.isInJam)
        assertTrue(
            "Client must transition to Closed/Disconnected on Unauthorized",
            jsm.jamClient.clientState.value is JamClientState.Closed || jsm.jamClient.clientState.value is JamClientState.Disconnected
        )
    }

    @Test
    fun `when awaitConnected fails createRoom returns failure and transitions to Disconnected`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.awaitConnectedResult = Result.failure(IllegalStateException("Timed out waiting for Jam server connection (10s)"))

        val res = client.createRoom()
        runCurrent()

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("Timed out waiting for Jam server") == true)
        assertEquals(JamClientState.Disconnected, client.clientState.value)
    }

    @Test
    fun `repeated ConnectError keeps retrying and rejoins after failures`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("RECN", "user_me"))

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        // Connection lost
        transport.emitEvent(JamTransport.Event.Disconnected("connection lost"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Make connect fail on retry 1
        transport.awaitConnectedResult = Result.failure(IllegalStateException("Socket connection error"))
        advanceTimeBy(2000L)
        runCurrent()

        // Should still be in Reconnecting state, attempt incremented
        val recState = client.clientState.value
        assertTrue(recState is JamClientState.Reconnecting)
        assertEquals(2, (recState as JamClientState.Reconnecting).attempt)

        // Now restore connection and rejoin succeeds
        transport.awaitConnectedResult = Result.success(Unit)
        transport.nextEmitResult = Result.success(makeRoomJson("RECN", "user_me"))
        advanceTimeBy(3000L)
        runCurrent()

        // Successfully rejoined into Active state
        val finalState = client.clientState.value
        assertTrue("Expected Active state after reconnect, but was $finalState", finalState is JamClientState.Active)
        assertEquals("RECN", (finalState as JamClientState.Active).code)
    }

    @Test
    fun `when createRoom fails clockSyncJob is cleaned up and not running`() = runTest {
        val client = createClient(autoClockSync = true)
        tokenToProvide = "valid_token"
        // awaitConnected succeeds, but create emit fails
        transport.nextEmitResult = Result.failure(JamProtocolException("INTERNAL", "Server error"))

        val res = client.createRoom()
        runCurrent()

        assertTrue(res.isFailure)
        assertFalse("clockSyncJob must not leak on createRoom failure", client.isClockSyncRunning)
    }

    @Test
    fun `when joinRoom fails clockSyncJob is cleaned up and not running`() = runTest {
        val client = createClient(autoClockSync = true)
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.failure(JamProtocolException("NOT_FOUND", "Room not found"))

        val res = client.joinRoom("TEST99")
        runCurrent()

        assertTrue(res.isFailure)
        assertFalse("clockSyncJob must not leak on joinRoom failure", client.isClockSyncRunning)
    }

    @Test
    fun `clock sync runs exactly once with 3 samples on reconnect`() = runTest {
        val client = createClient(autoClockSync = true)
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("RECONNECT_CLOCK", "user_me"))

        client.createRoom()
        advanceTimeBy(500L)
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        transport.emittedEvents.clear()

        // Drop connection
        transport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        transport.nextEmitResult = Result.success(makeRoomJson("RECONNECT_CLOCK", "user_me"))

        // Reconnect loop runs
        advanceTimeBy(2500L)
        runCurrent()

        assertTrue(client.clientState.value is JamClientState.Active)
        val timePings = transport.emittedEvents.filter { it.first == "jam2:time" }
        // Exactly 3 pings from a single clock sync run, not 6 or 9!
        assertEquals("Should perform exactly one 3-sample clock sync", 3, timePings.size)
    }

    @Test
    fun `transient token refresh failure uses fallback token and keeps retrying`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("TRAN", "user_me"))

        client.createRoom()
        runCurrent()

        // Connection lost
        transport.emitEvent(JamTransport.Event.Disconnected("drop"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Token refresh returns Transient with fallback token
        tokenResultToProvide = TokenRefreshResult.Transient("cached_fallback_token")
        // Transport connect will receive the fallback token and connect
        transport.nextEmitResult = Result.success(makeRoomJson("TRAN", "user_me"))

        advanceTimeBy(2000L)
        runCurrent()

        // Should not be closed with auth_failed; should successfully rejoin
        val state = client.clientState.value
        assertTrue("Should be Active with fallback token, but was $state", state is JamClientState.Active)
    }

    @Test
    fun `resetToDisconnected allows creating new Jam after Closed`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("ROOMA", "user_me"))

        client.createRoom()
        runCurrent()

        // Host ends Jam
        transport.emitEvent(JamTransport.Event.Closed("ended"))
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Closed)

        // Reset to disconnected
        client.resetToDisconnected()
        assertEquals(JamClientState.Disconnected, client.clientState.value)

        // Create new room succeeds
        transport.nextEmitResult = Result.success(makeRoomJson("ROOMB", "user_me"))
        val res = client.createRoom()
        runCurrent()
        assertTrue(res.isSuccess)
        val state = client.clientState.value
        assertTrue(state is JamClientState.Active)
        assertEquals("ROOMB", (state as JamClientState.Active).code)
    }

    @Test
    fun `stale sync response arriving after newer broadcast is dropped`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("SYNC", "user_me"))

        client.createRoom()
        runCurrent()

        // Newer state broadcast arrives: version 10
        val v10RoomJson = makeRoomJson("SYNC", "user_me").getJSONObject("room").apply {
            put("version", 10)
        }
        val v10Snapshot = RoomSnapshot.fromJson(v10RoomJson)
        stateStore.applySnapshot(v10Snapshot, isEpoch = false)
        assertEquals(10L, stateStore.room.value?.version)

        // Now a stale sync arrives with version 5
        val v5RoomJson = makeRoomJson("SYNC", "user_me").apply {
            getJSONObject("room").put("version", 5)
        }
        transport.nextEmitResult = Result.success(v5RoomJson)

        val syncRes = client.sync()
        assertTrue(syncRes.isSuccess)

        // Version should remain 10 because sync uses isEpoch = false
        assertEquals(10L, stateStore.room.value?.version)
    }

    @Test
    fun `tick with version less than or equal to current room does not call sync`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        val roomJson = makeRoomJson("TICK1", "user_me").apply {
            getJSONObject("room").put("version", 5)
        }
        transport.nextEmitResult = Result.success(roomJson)

        client.createRoom()
        runCurrent()
        assertEquals(5L, stateStore.room.value?.version)
        transport.emittedEvents.clear()

        // Tick with lower version (stale)
        transport.emitEvent(JamTransport.Event.Tick(Jam2Tick(
            code = "TICK1",
            version = 4L,
            serverTime = 2000L,
            nowPlaying = JamTickNowPlaying(100L, 2000L, JamPlaybackStatus.PLAYING, 0L)
        )))
        runCurrent()

        // Tick with same version
        transport.emitEvent(JamTransport.Event.Tick(Jam2Tick(
            code = "TICK1",
            version = 5L,
            serverTime = 3000L,
            nowPlaying = JamTickNowPlaying(200L, 3000L, JamPlaybackStatus.PLAYING, 0L)
        )))
        runCurrent()

        val syncEmits = transport.emittedEvents.filter { it.first == "jam2:sync" }
        assertEquals(0, syncEmits.size)
    }

    @Test
    fun `tick with version greater than current room calls sync`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        val roomJson = makeRoomJson("TICK2", "user_me").apply {
            getJSONObject("room").put("version", 5)
        }
        transport.nextEmitResult = Result.success(roomJson)

        client.createRoom()
        runCurrent()
        assertEquals(5L, stateStore.room.value?.version)
        transport.emittedEvents.clear()

        // Prepare sync response
        transport.nextEmitResult = Result.success(makeRoomJson("TICK2", "user_me").apply {
            getJSONObject("room").put("version", 6)
        })

        // Tick with higher version (server is ahead)
        transport.emitEvent(JamTransport.Event.Tick(Jam2Tick(
            code = "TICK2",
            version = 6L,
            serverTime = 4000L,
            nowPlaying = JamTickNowPlaying(500L, 4000L, JamPlaybackStatus.PLAYING, 0L)
        )))
        runCurrent()

        val syncEmits = transport.emittedEvents.filter { it.first == "jam2:sync" }
        assertEquals(1, syncEmits.size)
    }

    @Test
    fun `closed event with moved reason sets correct user message and policy toast`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        val roomJson = makeRoomJson("MOVE1", "user_me")
        transport.nextEmitResult = Result.success(roomJson)

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)

        transport.emitEvent(JamTransport.Event.Closed("moved"))
        runCurrent()

        val state = client.clientState.value
        assertTrue(state is JamClientState.Closed)
        val closed = state as JamClientState.Closed
        assertEquals("moved", closed.reason)
        assertEquals("You joined another Jam on a different device", closed.message)

        val toastMsg = closedToastFor("moved", alreadyToasted = false)
        assertEquals("You joined another Jam on a different device", toastMsg)
    }

    @Test
    fun `seekTo burst of 20 seeks sends first immediately and latest after window nothing in between`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        val roomJson = makeRoomJson("SEEK_BURST", "user_me")
        transport.nextEmitResult = Result.success(roomJson)

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)
        transport.emittedEvents.clear()

        val fakePlayer = FakeJamPlayerBridge()
        val fakePrefs = FakeJamPreferencesBridge()

        val sessionManager = JamSessionManager(
            playerManager = fakePlayer,
            jamPreferences = fakePrefs,
            coroutineScope = backgroundScope,
            jamClientOverride = client,
            stateStoreOverride = stateStore,
            clockSyncOverride = clockSync,
            ioDispatcher = StandardTestDispatcher(scheduler = testScheduler)
        )

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", roomJson.getJSONObject("room"))
        })

        // First seek goes out immediately
        sessionManager.seekTo(100L)
        runCurrent()

        // Burst of seeks while window is open
        for (i in 2..20) {
            sessionManager.seekTo(i * 100L) // 200, 300, ..., 2000
        }
        runCurrent()

        // Only first seek (100L) should be sent immediately
        val seeksBeforeWindow = transport.emittedEvents.filter {
            it.first == "jam2:cmd" && (it.second as? JSONObject)?.optString("type") == "seek"
        }
        assertEquals(1, seeksBeforeWindow.size)
        assertEquals(100L, (seeksBeforeWindow[0].second as JSONObject).getLong("positionMs"))

        // Advance past the 250ms window
        advanceTimeBy(250L)
        runCurrent()

        // Now latest seek (2000L) is sent after window, nothing in between (total 2 seeks)
        val seeksAfterWindow = transport.emittedEvents.filter {
            it.first == "jam2:cmd" && (it.second as? JSONObject)?.optString("type") == "seek"
        }
        assertEquals(2, seeksAfterWindow.size)
        assertEquals(2000L, (seeksAfterWindow[1].second as JSONObject).getLong("positionMs"))

        sessionManager.teardownJam()
    }

    @Test
    fun `seekTo repeated seeks to 0 separated by time sends both`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        val roomJson = makeRoomJson("SEEK_SAME", "user_me")
        transport.nextEmitResult = Result.success(roomJson)

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)
        transport.emittedEvents.clear()

        val fakePlayer = FakeJamPlayerBridge()
        val fakePrefs = FakeJamPreferencesBridge()

        val sessionManager = JamSessionManager(
            playerManager = fakePlayer,
            jamPreferences = fakePrefs,
            coroutineScope = backgroundScope,
            jamClientOverride = client,
            stateStoreOverride = stateStore,
            clockSyncOverride = clockSync,
            ioDispatcher = StandardTestDispatcher(scheduler = testScheduler)
        )

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", roomJson.getJSONObject("room"))
        })

        // First seek to 0
        sessionManager.seekTo(0L)
        runCurrent()

        val seeks1 = transport.emittedEvents.filter {
            it.first == "jam2:cmd" && (it.second as? JSONObject)?.optString("type") == "seek"
        }
        assertEquals(1, seeks1.size)
        assertEquals(0L, (seeks1[0].second as JSONObject).getLong("positionMs"))

        // Advance 1s
        advanceTimeBy(1000L)
        runCurrent()

        // Second seek to 0 (same target position)
        sessionManager.seekTo(0L)
        runCurrent()

        val seeks2 = transport.emittedEvents.filter {
            it.first == "jam2:cmd" && (it.second as? JSONObject)?.optString("type") == "seek"
        }
        assertEquals(2, seeks2.size)
        assertEquals(0L, (seeks2[1].second as JSONObject).getLong("positionMs"))

        sessionManager.teardownJam()
    }

    @Test
    fun `seekTo teardown cancels pending work`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        val roomJson = makeRoomJson("SEEK_TEARDOWN", "user_me")
        transport.nextEmitResult = Result.success(roomJson)

        client.createRoom()
        runCurrent()
        assertTrue(client.clientState.value is JamClientState.Active)
        transport.emittedEvents.clear()

        val fakePlayer = FakeJamPlayerBridge()
        val fakePrefs = FakeJamPreferencesBridge()

        val sessionManager = JamSessionManager(
            playerManager = fakePlayer,
            jamPreferences = fakePrefs,
            coroutineScope = backgroundScope,
            jamClientOverride = client,
            stateStoreOverride = stateStore,
            clockSyncOverride = clockSync,
            ioDispatcher = StandardTestDispatcher(scheduler = testScheduler)
        )

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", roomJson.getJSONObject("room"))
        })

        // Seek 1 sends immediately
        sessionManager.seekTo(100L)
        runCurrent()

        // Seek 2 is queued during throttle window
        sessionManager.seekTo(500L)
        runCurrent()

        // Teardown before 250ms delay completes
        sessionManager.teardownJam()
        runCurrent()

        // Advance time well past the window
        advanceTimeBy(1000L)
        runCurrent()

        // Seek 500 should never have been sent
        val seeks = transport.emittedEvents.filter {
            it.first == "jam2:cmd" && (it.second as? JSONObject)?.optString("type") == "seek"
        }
        assertEquals(1, seeks.size)
        assertEquals(100L, (seeks[0].second as JSONObject).getLong("positionMs"))
    }
}
