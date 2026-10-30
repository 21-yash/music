package com.synq.features.music.jam

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamClosedEventTest {

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()
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

        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
            return nextEmitResult
        }
    }

    private fun sampleRoomJson(code: String, hostUserId: String): JSONObject {
        return JSONObject().apply {
            put("code", code)
            put("version", 1L)
            put("hostUserId", hostUserId)
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
                put("item", JSONObject.NULL)
                put("status", "IDLE")
                put("positionMs", 0L)
                put("anchorServerTs", 1000L)
                put("trackSeq", 1L)
            })
            put("serverNow", 1000L)
        }
    }

    private fun createClient(transport: JamTransport, scope: kotlinx.coroutines.CoroutineScope, dispatcher: kotlinx.coroutines.CoroutineDispatcher): Pair<JamClient, JamStateStore> {
        val stateStore = JamStateStore("u1")
        val clockSync = ClockSync()
        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = scope,
            ioDispatcher = dispatcher,
            tokenProvider = { "token123" },
            autoClockSync = false,
            monotonicNowMs = { 1000L },
            wallClockNowMs = { 1000L }
        )
        return client to stateStore
    }

    @Test
    fun testEndRoomPlusServerEcho_emitsExactlyOneEvent() = runTest {
        val transport = FakeJamTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (client, _) = createClient(transport, backgroundScope, dispatcher)

        val closedEvents = mutableListOf<JamClosedEvent>()
        val job = backgroundScope.launch(dispatcher) {
            client.closedEvents.collect { closedEvents.add(it) }
        }

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", sampleRoomJson("ROOM1", "u1"))
        })

        client.createRoom()
        runCurrent()

        transport.nextEmitResult = Result.success(JSONObject().apply { put("ok", true) })
        client.endRoom()
        runCurrent()

        // Server echo
        transport.emitEvent(JamTransport.Event.Closed("ended"))
        runCurrent()

        assertEquals(1, closedEvents.size)
        assertEquals("ended", closedEvents[0].reason)
        assertTrue(closedEvents[0].initiatedByMe)
    }

    @Test
    fun testLeaveRoom_emitsExactlyOneEventWithLeftReason() = runTest {
        val transport = FakeJamTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (client, _) = createClient(transport, backgroundScope, dispatcher)

        val closedEvents = mutableListOf<JamClosedEvent>()
        backgroundScope.launch(dispatcher) {
            client.closedEvents.collect { closedEvents.add(it) }
        }

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", sampleRoomJson("ROOM2", "host_user"))
        })

        client.joinRoom("ROOM2")
        runCurrent()

        client.leaveRoom()
        runCurrent()

        // Server echo
        transport.emitEvent(JamTransport.Event.Closed("left"))
        runCurrent()

        assertEquals(1, closedEvents.size)
        assertEquals("left", closedEvents[0].reason)
        assertTrue(closedEvents[0].initiatedByMe)
    }

    @Test
    fun testServerKicked_emitsOneEventNotInitiatedByMe() = runTest {
        val transport = FakeJamTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (client, _) = createClient(transport, backgroundScope, dispatcher)

        val closedEvents = mutableListOf<JamClosedEvent>()
        backgroundScope.launch(dispatcher) {
            client.closedEvents.collect { closedEvents.add(it) }
        }

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", sampleRoomJson("ROOM3", "host_user"))
        })

        client.joinRoom("ROOM3")
        runCurrent()

        transport.emitEvent(JamTransport.Event.Closed("kicked"))
        runCurrent()

        assertEquals(1, closedEvents.size)
        assertEquals("kicked", closedEvents[0].reason)
        assertFalse(closedEvents[0].initiatedByMe)
    }

    @Test
    fun testEndRoomFailure_emitsZeroEvents() = runTest {
        val transport = FakeJamTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (client, _) = createClient(transport, backgroundScope, dispatcher)

        val closedEvents = mutableListOf<JamClosedEvent>()
        backgroundScope.launch(dispatcher) {
            client.closedEvents.collect { closedEvents.add(it) }
        }

        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", sampleRoomJson("ROOM4", "u1"))
        })

        client.createRoom()
        runCurrent()

        transport.nextEmitResult = Result.failure(Exception("network timeout"))
        val endRes = client.endRoom()
        runCurrent()

        assertTrue(endRes.isFailure)
        assertEquals(0, closedEvents.size)
    }

    @Test
    fun testNewJoinAfterClosedSession_canEmitAgain() = runTest {
        val transport = FakeJamTransport()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (client, _) = createClient(transport, backgroundScope, dispatcher)

        val closedEvents = mutableListOf<JamClosedEvent>()
        backgroundScope.launch(dispatcher) {
            client.closedEvents.collect { closedEvents.add(it) }
        }

        // Session 1: Join & Leave
        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", sampleRoomJson("ROOM5", "host_user"))
        })
        client.joinRoom("ROOM5")
        runCurrent()

        client.leaveRoom()
        runCurrent()
        assertEquals(1, closedEvents.size)

        // Session 2: Join & get kicked
        transport.nextEmitResult = Result.success(JSONObject().apply {
            put("ok", true)
            put("room", sampleRoomJson("ROOM6", "host_user"))
        })
        client.joinRoom("ROOM6")
        runCurrent()

        transport.emitEvent(JamTransport.Event.Closed("kicked"))
        runCurrent()

        assertEquals(2, closedEvents.size)
        assertEquals("left", closedEvents[0].reason)
        assertEquals("kicked", closedEvents[1].reason)
    }
}

