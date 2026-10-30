package com.synq.features.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipPolicyTest {

    @Test
    fun testThreeSkipsAllowedAndFourthStops() {
        val policy = AutoSkipPolicy(maxConsecutiveSkips = 3)

        // 1st failure -> should skip
        assertTrue(policy.onFailure())
        assertEquals(1, policy.consecutiveFailures)

        // 2nd failure -> should skip
        assertTrue(policy.onFailure())
        assertEquals(2, policy.consecutiveFailures)

        // 3rd failure -> should skip
        assertTrue(policy.onFailure())
        assertEquals(3, policy.consecutiveFailures)

        // 4th failure -> stops and resets
        assertFalse(policy.onFailure())
        assertEquals(0, policy.consecutiveFailures)
    }

    @Test
    fun testResetAfterOnReady() {
        val policy = AutoSkipPolicy(maxConsecutiveSkips = 3)

        policy.onFailure()
        policy.onFailure()
        assertEquals(2, policy.consecutiveFailures)

        policy.onReady()
        assertEquals(0, policy.consecutiveFailures)

        // Next failure starts at 1 again
        assertTrue(policy.onFailure())
        assertEquals(1, policy.consecutiveFailures)
    }

    @Test
    fun testNoNextCase() {
        val policy = AutoSkipPolicy(maxConsecutiveSkips = 3)

        // Middle of queue
        assertTrue(policy.canSkipForward(currentIndex = 0, queueSize = 2))
        assertTrue(policy.canSkipForward(currentIndex = 1, queueSize = 3))

        // End of queue
        assertFalse(policy.canSkipForward(currentIndex = 2, queueSize = 3))
        assertFalse(policy.canSkipForward(currentIndex = 0, queueSize = 1))
        assertFalse(policy.canSkipForward(currentIndex = 0, queueSize = 0))
    }
}

