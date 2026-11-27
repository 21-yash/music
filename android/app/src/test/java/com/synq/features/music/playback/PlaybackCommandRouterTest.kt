package com.synq.features.music.playback

import androidx.media3.common.Player
import com.synq.features.music.jam.JamSessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

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
    fun testSeekInCurrentGatedByCanPlayback() {
        val action = PlaybackCommandRouter.mapJamCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, canPlayback = true)
        assertEquals(PlaybackCommandRouter.Action.SEEK_IN_CURRENT, action)

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

    @Test
    fun testWithSkipCommandsAddsAllSkipCommands() {
        val builder = mockk<Player.Commands.Builder>()
        val base = mockk<Player.Commands>()
        val expected = mockk<Player.Commands>()
        every { base.buildUpon() } returns builder
        every {
            builder.addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
        } returns builder
        every { builder.build() } returns expected

        val augmented = PlaybackCommandRouter.withSkipCommands(base)

        assertEquals(expected, augmented)
        verify(exactly = 1) {
            builder.addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            builder.build()
        }

        assertTrue(PlaybackCommandRouter.isSkipCommand(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(PlaybackCommandRouter.isSkipCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(PlaybackCommandRouter.isSkipCommand(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(PlaybackCommandRouter.isSkipCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(PlaybackCommandRouter.isSkipCommand(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun testJamAwarePlayerExposesAndRoutesSkipControls() {
        val baseCommands = mockk<Player.Commands>()
        val augmentedCommands = mockk<Player.Commands>()
        val builder = mockk<Player.Commands.Builder>()
        every { baseCommands.buildUpon() } returns builder
        every {
            builder.addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
        } returns builder
        every { builder.build() } returns augmentedCommands

        val innerPlayer = mockk<Player>(relaxed = true)
        every { innerPlayer.availableCommands } returns baseCommands
        every { innerPlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) } returns true
        every { innerPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) } returns false
        every { innerPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) } returns false

        val jamManager = mockk<JamSessionManager>(relaxed = true)
        val musicPlayerManager = mockk<MusicPlayerManager>(relaxed = true)
        val jamAwarePlayer = PlaybackService.JamAwarePlayer(
            player = innerPlayer,
            jamSessionManagerProvider = Provider { jamManager },
            musicPlayerManagerProvider = Provider { musicPlayerManager }
        )

        assertEquals(augmentedCommands, jamAwarePlayer.availableCommands)

        assertTrue(jamAwarePlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(jamAwarePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(jamAwarePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(jamAwarePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(jamAwarePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

        val listenerSlot = slot<Player.Listener>()
        every { innerPlayer.addListener(capture(listenerSlot)) } returns Unit
        var receivedCommands: Player.Commands? = null
        var receivedIsPlaying: Boolean? = null
        var receivedPlaybackState: Int? = null
        var receivedMediaMetadata: androidx.media3.common.MediaMetadata? = null
        val externalListener = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                receivedCommands = availableCommands
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                receivedIsPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                receivedPlaybackState = playbackState
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                receivedMediaMetadata = mediaMetadata
            }
        }
        jamAwarePlayer.addListener(externalListener)
        listenerSlot.captured.onAvailableCommandsChanged(baseCommands)
        assertEquals(augmentedCommands, receivedCommands)

        val dummyMetadata = mockk<androidx.media3.common.MediaMetadata>()
        listenerSlot.captured.onIsPlayingChanged(true)
        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
        listenerSlot.captured.onMediaMetadataChanged(dummyMetadata)
        assertEquals(true, receivedIsPlaying)
        assertEquals(Player.STATE_READY, receivedPlaybackState)
        assertEquals(dummyMetadata, receivedMediaMetadata)

        jamAwarePlayer.removeListener(externalListener)
        verify { innerPlayer.removeListener(listenerSlot.captured) }

        jamAwarePlayer.seekToNext()
        jamAwarePlayer.seekToNextMediaItem()
        jamAwarePlayer.seekToPrevious()
        jamAwarePlayer.seekToPreviousMediaItem()
        verify(exactly = 2) { musicPlayerManager.skipToNext() }
        verify(exactly = 2) { musicPlayerManager.skipToPrevious() }
    }
}
