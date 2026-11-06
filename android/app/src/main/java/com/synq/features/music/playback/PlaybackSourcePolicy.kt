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

    const val PLAY_REUSE_MAX_AGE_MS: Long = 2 * 60 * 60 * 1000L // 2 hours

    enum class SavedUriDecision {
        REUSE,
        EXTRACT
    }

    /**
     * Decides whether to reuse a previously saved stream URI (e.g. from prefetch or direct extraction)
     * even when the song is not fully cached, avoiding redundant NewPipe extraction.
     */
    fun decideSavedUriReuse(
        isFullyCached: Boolean,
        savedUri: String?,
        uriAgeMs: Long?,
        maxAgeMs: Long = PLAY_REUSE_MAX_AGE_MS
    ): SavedUriDecision {
        if (savedUri.isNullOrBlank()) {
            return SavedUriDecision.EXTRACT
        }
        if (uriAgeMs == null || uriAgeMs < 0L || uriAgeMs > maxAgeMs) {
            return SavedUriDecision.EXTRACT
        }
        return SavedUriDecision.REUSE
    }

    fun shouldReuseSavedUri(
        isFullyCached: Boolean,
        savedUri: String?,
        uriAgeMs: Long?,
        maxAgeMs: Long = PLAY_REUSE_MAX_AGE_MS
    ): Boolean {
        return decideSavedUriReuse(isFullyCached, savedUri, uriAgeMs, maxAgeMs) == SavedUriDecision.REUSE
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

