package com.synq.features.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeWarmUpPolicyTest {

    @Test
    fun testOfflineSkipsWarmUp() {
        val shouldRun = MusicPlayerManager.shouldWarmUp(isOnline = false, alreadyWarmed = false)
        assertFalse("Should skip warm-up when offline even if not yet warmed", shouldRun)
    }

    @Test
    fun testOnlineAndNotYetWarmedRunsWarmUp() {
        val shouldRun = MusicPlayerManager.shouldWarmUp(isOnline = true, alreadyWarmed = false)
        assertTrue("Should run warm-up when online and not yet warmed", shouldRun)
    }

    @Test
    fun testAlreadyWarmedSkipsWarmUpWhenOnline() {
        val shouldRun = MusicPlayerManager.shouldWarmUp(isOnline = true, alreadyWarmed = true)
        assertFalse("Should skip warm-up if already warmed", shouldRun)
    }

    @Test
    fun testAlreadyWarmedSkipsWarmUpWhenOffline() {
        val shouldRun = MusicPlayerManager.shouldWarmUp(isOnline = false, alreadyWarmed = true)
        assertFalse("Should skip warm-up if offline and already warmed", shouldRun)
    }
}

