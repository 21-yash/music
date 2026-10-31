package com.synq.features.music.playback

object JamSeekRouter {
    enum class Route {
        PASS_THROUGH,
        ROUTE_TO_JAM,
        DROP
    }

    fun route(
        isInJam: Boolean,
        isExternalController: Boolean,
        canPlayback: Boolean
    ): Route {
        if (!isInJam) return Route.PASS_THROUGH
        if (!isExternalController) return Route.PASS_THROUGH
        return if (canPlayback) Route.ROUTE_TO_JAM else Route.DROP
    }
}
