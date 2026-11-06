package com.synq.features.music.playback

object StreamResolutionPlan {
    enum class Plan {
        DIRECT_YOUTUBE,
        BACKEND_THEN_FALLBACK
    }

    private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun choose(providerId: String?, songId: String): Plan {
        val p = providerId?.lowercase()?.trim()
        if (p == "youtube") {
            return Plan.DIRECT_YOUTUBE
        }
        if (p == "jiosaavn" || p == "spotify") {
            return Plan.BACKEND_THEN_FALLBACK
        }
        if (YOUTUBE_ID_REGEX.matches(songId)) {
            return Plan.DIRECT_YOUTUBE
        }
        return Plan.BACKEND_THEN_FALLBACK
    }
}
