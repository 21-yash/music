package com.synq.features.music.sleeptimer

import com.synq.features.music.jam.JamSessionManager
import com.synq.features.music.jam.JamState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private class FakeSleepTimerPlayerBridge : SleepTimerPlayerBridge {
        override var onTrackEndedListener: (() -> Unit)? = null
        var pauseCalled = false

        override fun pause() {
            pauseCalled = true
        }
    }

    private lateinit var playerBridge: FakeSleepTimerPlayerBridge
    private lateinit var jamManager: JamSessionManager
    private val jamStateFlow = MutableStateFlow(JamState())
    private lateinit var manager: SleepTimerManager

    @Before
    fun setup() {
        playerBridge = FakeSleepTimerPlayerBridge()
        jamManager = mockk(relaxed = true)
        every { jamManager.jamState } returns jamStateFlow
    }

    @Test
    fun endOfTrackInJamMode_callsStepOutLocallyAndCancelsTimer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        manager = SleepTimerManager(
            playerManager = playerBridge,
            jamSessionManagerProvider = Provider { jamManager },
            coroutineScope = testScope
        )

        // Given user is in a Jam
        jamStateFlow.value = JamState(isInJam = true, sessionCode = "JAM123")

        // When setting timer to "End of track"
        manager.setTimer("End of track")
        assertEquals("End of track", manager.selectedOption.value)
        assertNotNull(playerBridge.onTrackEndedListener)

        // And track ends
        playerBridge.onTrackEndedListener?.invoke()

        // Then JamSessionManager.stepOutLocally is called, normal pause is NOT called, and timer cancels
        verify(exactly = 1) { jamManager.stepOutLocally() }
        assertFalse(playerBridge.pauseCalled)
        assertEquals("Off", manager.selectedOption.value)
        assertNull(manager.remainingSeconds.value)
    }

    @Test
    fun endOfTrackOutsideJamMode_callsPauseAndCancelsTimer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        manager = SleepTimerManager(
            playerManager = playerBridge,
            jamSessionManagerProvider = Provider { jamManager },
            coroutineScope = testScope
        )

        // Given user is NOT in a Jam
        jamStateFlow.value = JamState(isInJam = false)

        // When setting timer to "End of track"
        manager.setTimer("End of track")
        assertEquals("End of track", manager.selectedOption.value)
        assertNotNull(playerBridge.onTrackEndedListener)

        // And track ends
        playerBridge.onTrackEndedListener?.invoke()

        // Then playerBridge.pause is called, and timer cancels
        assertTrue(playerBridge.pauseCalled)
        verify(exactly = 0) { jamManager.stepOutLocally() }
        assertEquals("Off", manager.selectedOption.value)
        assertNull(manager.remainingSeconds.value)
    }
}
