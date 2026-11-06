package com.synq.features.music.playback

import com.synq.features.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuePrefetchPolicyTest {

    private fun createSong(id: String, title: String = id): Song = Song(
        id = id,
        title = title,
        artists = emptyList(),
        album = null,
        durationSeconds = 180,
        imageUrl = null,
        year = "2024",
        language = "en",
        hasLyrics = false,
        playCount = 0,
        label = "",
        streamRef = "",
        providerId = "youtube"
    )

    @Test
    fun testDecideNextTrackPrefetch_whenJamActive_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = true,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenOffline_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = false
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenRepeatOneActive_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = true,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenCurrentIndexNegative_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = -1,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenCurrentIndexOutOfBounds_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 2,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenAtEndOfQueue_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 1,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenNextSongHasSameIdAsCurrent_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song1"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenNextSongAlreadyPrefetched_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = "song2",
            isJamActive = false,
            isOnline = true
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenNextSongIsFullyCached_returnsNull() {
        val queue = listOf(createSong("song1"), createSong("song2"))
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true,
            isFullyCached = { songId -> songId == "song2" }
        )
        assertNull(result)
    }

    @Test
    fun testDecideNextTrackPrefetch_validNextSong_returnsNextSong() {
        val song1 = createSong("song1")
        val song2 = createSong("song2")
        val queue = listOf(song1, song2)
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = null,
            isJamActive = false,
            isOnline = true,
            isFullyCached = { false }
        )
        assertEquals(song2, result)
    }

    @Test
    fun testDecideNextTrackPrefetch_whenLastPrefetchedIdDifferent_returnsNextSong() {
        val song1 = createSong("song1")
        val song2 = createSong("song2")
        val queue = listOf(song1, song2)
        val result = QueuePrefetchPolicy.decideNextTrackPrefetch(
            currentIndex = 0,
            queue = queue,
            isRepeatOne = false,
            lastPrefetchedNextId = "old_song",
            isJamActive = false,
            isOnline = true,
            isFullyCached = { false }
        )
        assertEquals(song2, result)
    }
}

