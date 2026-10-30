package com.synq.features.music.playback

import androidx.media3.common.Player

object PlaybackCommandRouter {
    enum class Action {
        TOGGLE,
        NEXT,
        PREV,
        SEEK_BACK,
        SEEK_FWD,
        SKIP_ONLY,
        STOP
    }

    fun isExternalController(
        isNotificationController: Boolean,
        controllerPackage: String,
        appPackage: String
    ): Boolean {
        return isNotificationController || controllerPackage != appPackage
    }

    fun shouldIntercept(
        isExternal: Boolean,
        isInJam: Boolean,
        playerCommand: Int
    ): Boolean {
        if (!isInJam || !isExternal) return false
        return when (playerCommand) {
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_STOP -> true
            else -> false
        }
    }

    fun mapJamCommand(playerCommand: Int, canPlayback: Boolean = true): Action? {
        return when (playerCommand) {
            Player.COMMAND_PLAY_PAUSE -> Action.TOGGLE
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> Action.NEXT
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Action.PREV
            Player.COMMAND_SEEK_BACK -> if (canPlayback) Action.SEEK_BACK else Action.SKIP_ONLY
            Player.COMMAND_SEEK_FORWARD -> if (canPlayback) Action.SEEK_FWD else Action.SKIP_ONLY
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> Action.SKIP_ONLY
            Player.COMMAND_STOP -> Action.STOP
            else -> null
        }
    }
}
