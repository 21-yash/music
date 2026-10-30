package com.synq.features.music.playback

import com.synq.features.music.domain.model.Song

data class PersonalQueueSnapshot(
    val queue: List<Song>,
    val currentIndex: Int,
    val recommendedSongIds: Set<String> = emptySet(),
    val sourcePlaylist: List<Song>? = null,
    val nextSourceIndex: Int = 0,
    val activeRadioArtist: String? = null
)

class PersonalQueueStash {
    var snapshot: PersonalQueueSnapshot? = null
        private set

    val isStashed: Boolean
        get() = snapshot != null

    fun stash(
        queue: List<Song>,
        currentIndex: Int,
        recommendedSongIds: Set<String> = emptySet(),
        sourcePlaylist: List<Song>? = null,
        nextSourceIndex: Int = 0,
        activeRadioArtist: String? = null
    ): Boolean {
        if (snapshot != null) return false
        snapshot = PersonalQueueSnapshot(
            queue = queue,
            currentIndex = currentIndex,
            recommendedSongIds = recommendedSongIds,
            sourcePlaylist = sourcePlaylist,
            nextSourceIndex = nextSourceIndex,
            activeRadioArtist = activeRadioArtist
        )
        return true
    }

    fun restore(): PersonalQueueSnapshot? {
        val stashed = snapshot ?: return null
        snapshot = null
        return stashed
    }

    fun clear() {
        snapshot = null
    }
}
