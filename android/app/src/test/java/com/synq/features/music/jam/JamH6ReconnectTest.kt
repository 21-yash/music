package com.synq.features.music.jam

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamH6ReconnectTest {

    @Test
    fun testHealthCheckPolicyDecisions() {
        // first ok -> nothing
        val firstOk = HealthCheckPolicy.onFirstPingResult(success = true)
        assertTrue(firstOk is HealthCheckAction.DoNothing)

        // first fail -> retry
        val firstFail = HealthCheckPolicy.onFirstPingResult(success = false)
        assertTrue(firstFail is HealthCheckAction.Retry)
        assertEquals(HealthCheckPolicy.RETRY_DELAY_MS, (firstFail as HealthCheckAction.Retry).delayMs)
        assertEquals(HealthCheckPolicy.PING_TIMEOUT_MS, firstFail.timeoutMs)

        // second ok -> nothing
        val secondOk = HealthCheckPolicy.onRetryPingResult(success = true)
        assertTrue(secondOk is HealthCheckAction.DoNothing)

        // second fail -> reconnect
        val secondFail = HealthCheckPolicy.onRetryPingResult(success = false)
        assertTrue(secondFail is HealthCheckAction.DisconnectAndReconnect)
    }

    @Test
    fun testNetworkChangeDebouncer_trailingDebounce() = runTest {
        var invocationCount = 0
        val debouncer = NetworkChangeDebouncer(
            scope = backgroundScope,
            debounceDelayMs = 3000L,
            onTrigger = { invocationCount++ }
        )

        // 3 calls within 3s -> 1 invocation
        debouncer.onNetworkChanged()
        advanceTimeBy(1000L)
        debouncer.onNetworkChanged()
        advanceTimeBy(1000L)
        debouncer.onNetworkChanged()
        advanceTimeBy(3001L)

        assertEquals(1, invocationCount)

        // Calls 5s apart -> 2 invocations total
        advanceTimeBy(2000L) // 5s total since previous call
        debouncer.onNetworkChanged()
        advanceTimeBy(3001L)
        assertEquals(2, invocationCount)
    }

    @Test
    fun testJamClient_slowPingSucceedsOnRetry_doesNotDisconnectOrEmitReconnecting() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        var pingCount = 0
        var disconnected = false
        val eventsFlow = MutableSharedFlow<JamTransport.Event>()

        val fakeTransport = object : JamTransport {
            override val events: Flow<JamTransport.Event> get() = eventsFlow
            override val isConnected: Boolean get() = true
            override fun connect(token: String?) {}
            override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> = Result.success(Unit)
            override fun disconnect() {
                disconnected = true
            }
            override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
                if (event == "jam2:time") {
                    pingCount++
                    return if (pingCount == 1) {
                        Result.failure(Exception("Ack timeout"))
                    } else {
                        Result.success(JSONObject(mapOf("serverTime" to 12345L)))
                    }
                }
                return Result.success(JSONObject())
            }
        }

        val client = JamClient(
            transport = fakeTransport,
            stateStore = JamStateStore(),
            clockSync = ClockSync(),
            tokenProvider = { "test-token" },
            scope = backgroundScope,
            ioDispatcher = testDispatcher,
            monotonicNowMs = { System.currentTimeMillis() },
            autoClockSync = false
        )

        // Put client into Active state with an active room
        client.setActiveForTesting("TESTROOM", JamRole.GUEST)

        assertEquals(JamClientState.Active("TESTROOM", JamRole.GUEST), client.clientState.value)

        // Invoke onNetworkRestored
        client.onNetworkRestored()

        // Advance through first ping timeout and retry delay
        testScheduler.advanceTimeBy(HealthCheckPolicy.RETRY_DELAY_MS + 100L)

        assertFalse("Transport must not be disconnected when retry succeeds", disconnected)
        assertTrue(
            "ClientState must remain Active and not enter Reconnecting",
            client.clientState.value is JamClientState.Active
        )
        assertEquals(2, pingCount)
    }
}
