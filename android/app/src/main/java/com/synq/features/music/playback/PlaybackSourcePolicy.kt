package com.synq.features.music.playback

object PlaybackSourcePolicy {

    enum class SourceType {
        CACHE_WITH_SAVED_URI,
        CACHE_FAKE_URL,
        NETWORK
    }

    enum class ErrorAction {
        EVICT_AND_RETRY_NETWORK,
        RE_RESOLVE_ONCE,
        GIVE_UP
    }

    fun chooseSource(fullyCached: Boolean, savedUri: String?): SourceType {
        if (!fullyCached) {
            return SourceType.NETWORK
        }
        return if (!savedUri.isNullOrBlank()) {
            SourceType.CACHE_WITH_SAVED_URI
        } else {
            SourceType.CACHE_FAKE_URL
        }
    }

    fun onErrorAction(
        playingFromFullCache: Boolean,
        alreadyRetried: Boolean,
        online: Boolean
    ): ErrorAction {
        if (!online) return ErrorAction.GIVE_UP
        if (playingFromFullCache) {
            return if (!alreadyRetried) ErrorAction.EVICT_AND_RETRY_NETWORK else ErrorAction.GIVE_UP
        }
        return if (!alreadyRetried) ErrorAction.RE_RESOLVE_ONCE else ErrorAction.GIVE_UP
    }
}

