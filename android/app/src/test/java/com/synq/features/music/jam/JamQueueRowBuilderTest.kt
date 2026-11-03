package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamQueueRowBuilderTest {

    private fun makeQueueItem(qid: String, songId: String, recommended: Boolean = false): JamQueueItem {
        return JamQueueItem(
            qid = qid,
            song = Song(
                id = songId,
                title = "Title $songId",
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
                providerId = "jiosaavn"
            ),
            addedByUserId = "user_1",
            addedAt = 1000L,
            recommended = recommended
        )
    }

    @Test
    fun testEmptyQueueReturnsNowPlayingItemRowOnly() {
        val nowPlaying = makeQueueItem("np_1", "song_np")
        val rows = JamQueueRowBuilder.buildJamQueueRows(nowPlaying, emptyList())

        assertEquals(1, rows.size)
        assertEquals("np_1", rows[0].item.qid)
        assertTrue("NowPlaying item row must have isActive = true", rows[0].isActive)
    }

    @Test
    fun testActiveFlagTrueOnNowPlayingAndFalseOnTheRest() {
        val nowPlaying = makeQueueItem("np_1", "song_np")
        val q1 = makeQueueItem("q_1", "song_1")
        val q2 = makeQueueItem("q_2", "song_2")

        val rows = JamQueueRowBuilder.buildJamQueueRows(nowPlaying, listOf(q1, q2))

        assertEquals(3, rows.size)
        assertTrue(rows[0].isActive)
        assertFalse(rows[1].isActive)
        assertFalse(rows[2].isActive)
    }

    @Test
    fun testTitleReflectsTotalCountCorrectly() {
        assertEquals("Jam Queue (0 songs)", JamQueueRowBuilder.formatJamQueueTitle(0))
        assertEquals("Jam Queue (1 songs)", JamQueueRowBuilder.formatJamQueueTitle(1))
        assertEquals("Jam Queue (5 songs)", JamQueueRowBuilder.formatJamQueueTitle(5))
    }

    @Test
    fun testPreservesOrderOfInputQueue() {
        val nowPlaying = makeQueueItem("np_1", "song_np")
        val q1 = makeQueueItem("q_1", "song_1")
        val q2 = makeQueueItem("q_2", "song_2")
        val q3 = makeQueueItem("q_3", "song_3")

        val rows = JamQueueRowBuilder.buildJamQueueRows(nowPlaying, listOf(q1, q2, q3))

        assertEquals(4, rows.size)
        assertEquals("np_1", rows[0].item.qid)
        assertEquals("q_1", rows[1].item.qid)
        assertEquals("q_2", rows[2].item.qid)
        assertEquals("q_3", rows[3].item.qid)
    }
}
