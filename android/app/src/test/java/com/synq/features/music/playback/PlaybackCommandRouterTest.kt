package com.synq.features.music.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCommandRouterTest {

    private val appPackage = "com.synq"

    @Test
    fun testInAppControllerNeverInterceptedInJam() {
        val isExternal = PlaybackCommandRouter.isExternalController(
            isNotificationController = false,
            controllerPackage = appPackage,
            appPackage = appPackage
        )
        assertFalse("In-app controller must not be treated as external", isExternal)

        val interceptedPlayPause = PlaybackCommandRouter.shouldIntercept(
            isExternal = isExternal,
            isInJam = true,
            playerCommand = Player.COMMAND_PLAY_PAUSE
        )
        assertFalse("In-app play/pause must pass through to super unchanged", interceptedPlayPause)

        val interceptedNext = PlaybackCommandRouter.shouldIntercept(
            isExternal = isExternal,
            isInJam = true,
            playerCommand = Player.COMMAND_SEEK_TO_NEXT
        )
        assertFalse("In-app seek_to_next must pass through to super unchanged", interceptedNext)
    }

    @Test
    fun testNotificationControllerInterceptedInJam() {
        val isExternal = PlaybackCommandRouter.isExternalController(
            isNotificationController = true,
            controllerPackage = appPackage,
            appPackage = appPackage
        )
        assertTrue("Notification controller must be treated as external", isExternal)

        val intercepted = PlaybackCommandRouter.shouldIntercept(
            isExternal = isExternal,
            isInJam = true,
            playerCommand = Player.COMMAND_PLAY_PAUSE
        )
        assertTrue("Notification play/pause must be intercepted for Jam", intercepted)
    }

    @Test
    fun testOtherPackageControllerInterceptedInJam() {
        val isExternal = PlaybackCommandRouter.isExternalController(
            isNotificationController = false,
            controllerPackage = "com.android.bluetooth",
            appPackage = appPackage
        )
        assertTrue("Bluetooth / external package controller must be external", isExternal)

        val intercepted = PlaybackCommandRouter.shouldIntercept(
            isExternal = isExternal,
            isInJam = true,
            playerCommand = Player.COMMAND_PLAY_PAUSE
        )
        assertTrue("Bluetooth play/pause must be intercepted for Jam", intercepted)
    }

    @Test
    fun testExternalControllerAllowedWhenNotInJam() {
        val isExternal = PlaybackCommandRouter.isExternalController(
            isNotificationController = true,
            controllerPackage = appPackage,
            appPackage = appPackage
        )
        val intercepted = PlaybackCommandRouter.shouldIntercept(
            isExternal = isExternal,
            isInJam = false,
            playerCommand = Player.COMMAND_PLAY_PAUSE
        )
        assertFalse("Notification controller must not be intercepted when not in Jam", intercepted)
    }

    @Test
    fun testExternalSeekAndStopCommandsInterceptedInJam() {
        val isExternal = true
        val isInJam = true

        val commands = listOf(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )

        for (cmd in commands) {
            assertTrue(
                "Command $cmd must be intercepted when external and in Jam",
                PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, cmd)
            )
        }
    }

    @Test
    fun testUnrelatedCommandsNeverIntercepted() {
        val isExternal = true
        val isInJam = true

        assertFalse(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SET_SHUFFLE_MODE))
        assertFalse(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SET_REPEAT_MODE))
        assertFalse(PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, Player.COMMAND_SET_SPEED_AND_PITCH))
    }

    @Test
    fun testNonPermittedMemberPlayPauseMapsToToggle() {
        val action = PlaybackCommandRouter.mapJamCommand(Player.COMMAND_PLAY_PAUSE, canPlayback = false)
        assertEquals(PlaybackCommandRouter.Action.TOGGLE, action)
    }

    @Test
    fun testSeekInCurrentMapsToSkipOnly() {
        val action = PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, canPlayback = true)
        assertEquals(PlaybackCommandRouter.Action.SKIP_ONLY, action)

        val actionNonPermitted = PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, canPlayback = false)
        assertEquals(PlaybackCommandRouter.Action.SKIP_ONLY, actionNonPermitted)
    }

    @Test
    fun testSeekBackAndForwardGatedByCanPlayback() {
        assertEquals(PlaybackCommandRouter.Action.SEEK_BACK, PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_BACK, canPlayback = true))
        assertEquals(PlaybackCommandRouter.Action.SKIP_ONLY, PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_BACK, canPlayback = false))

        assertEquals(PlaybackCommandRouter.Action.SEEK_FWD, PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_FORWARD, canPlayback = true))
        assertEquals(PlaybackCommandRouter.Action.SKIP_ONLY, PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_FORWARD, canPlayback = false))
    }
}
