package com.synq.features.music.playback

import com.synq.features.music.domain.model.Song

object QueuePrefetchPolicy {

    /**
     * Decides whether to prefetch the next track in the queue for personal mode (no Jam).
     *
     * @param currentIndex Current playing index in the queue.
     * @param queue Current playback queue.
     * @param isRepeatOne Whether repeat-one mode is active.
     * @param lastPrefetchedNextId ID of the next song already prefetched for the current song.
     * @param isJamActive Whether Jam mode is active (jamInterceptor != null).
     * @param isOnline Whether device has active internet connectivity.
     * @param isFullyCached Lambda checking if a given song is already fully cached.
     * @return The next Song to prefetch, or null if prefetching should be skipped.
     */
    fun decideNextTrackPrefetch(
        currentIndex: Int,
        queue: List<Song>,
        isRepeatOne: Boolean,
        lastPrefetchedNextId: String?,
        isJamActive: Boolean,
        isOnline: Boolean,
        isFullyCached: (String) -> Boolean = { false }
    ): Song? {
        if (isJamActive) return null
        if (!isOnline) return null
        if (isRepeatOne) return null
        if (currentIndex < 0 || currentIndex >= queue.size) return null

        val currentSong = queue[currentIndex]
        val nextIndex = currentIndex + 1
        if (nextIndex >= queue.size) return null

        val nextSong = queue[nextIndex]
        if (nextSong.id == currentSong.id) return null
        if (nextSong.id == lastPrefetchedNextId) return null
        if (isFullyCached(nextSong.id)) return null

        return nextSong
    }
}
