package com.synq.features.music.jam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            isConnected = true
            emitEvent(JamTransport.Event.Connected)
        }

        override fun disconnect() {
            isConnected = false
        }

        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
            emittedEvents.add(event to data)
            return nextEmitResult
        }
    }

    private lateinit var transport: FakeJamTransport
    private lateinit var stateStore: JamStateStore
    private lateinit var clockSync: ClockSync

    private var currentTime: Long = 1000L
    private var tokenToProvide: String? = "valid_token"

    @Before
    fun setup() {
        transport = FakeJamTransport()
        stateStore = JamStateStore(myUserId = "user_me")
        clockSync = ClockSync(
            monotonicNowMs = { currentTime },
            wallClockNowMs = { currentTime }
        )
    }

    private fun kotlinx.coroutines.test.TestScope.createClient(): JamClient {
        return JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = testScheduler),
            tokenProvider = { tokenToProvide },
            autoClockSync = false,
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
        assertEquals("The host ended the Jam session.", state.message)
    }

    @Test
    fun `buffers commands when Reconnecting and drops expired commands on replay`() = runTest {
        val client = createClient()
        tokenToProvide = "valid_token"
        transport.nextEmitResult = Result.success(makeRoomJson("BUFF", "user_me"))

        client.createRoom()
        runCurrent()

        // Disconnect to trigger Reconnecting
        transport.emitEvent(JamTransport.Event.Disconnected("network drop"))
        runCurrent()

        assertTrue(client.clientState.value is JamClientState.Reconnecting)

        // Queue command 1 at t = 1000ms
        currentTime = 1000L
        client.sendCommand("play")

        // Advance time by 20s (command 1 now expired: 21000 - 1000 = 20000 > 15000ms TTL)
        currentTime = 21000L
        // Queue command 2 at t = 21000ms (fresh)
        client.sendCommand("pause")

        // Simulate reconnect and successful rejoin
        transport.nextEmitResult = Result.success(makeRoomJson("BUFF", "user_me"))
        transport.emitEvent(JamTransport.Event.Connected)
        runCurrent()

        // Check emitted commands: "play" should have been dropped because TTL expired,
        // and only "pause" should have been replayed!
        val cmdEmits = transport.emittedEvents.filter { it.first == "jam2:cmd" }
        assertEquals(1, cmdEmits.size)
        val payload = cmdEmits[0].second as JSONObject
        assertEquals("pause", payload.getString("type"))
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
}
