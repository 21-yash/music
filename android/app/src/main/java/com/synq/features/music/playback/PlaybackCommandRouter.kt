package com.synq.features.music.playback

import androidx.media3.common.Player

object PlaybackCommandRouter {
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
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> false
        }
    }
}
