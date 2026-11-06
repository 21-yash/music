package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamPlaybackMirrorTest {

    private fun sampleSong(id: String, title: String = "Test Song", duration: Int = 180): Song {
        return Song(
            id = id,
            title = title,
            artists = emptyList(),
            album = null,
            durationSeconds = duration,
            imageUrl = "https://example.com/art.jpg",
            year = "2024",
            language = "English",
            hasLyrics = false,
            playCount = 100,
            label = "Label",
            streamRef = "stream_$id",
            providerId = "saavn"
        )
    }

    @Test
    fun testJamInterceptorDefaultMethodsReturnFalse() {
        val interceptor = object : JamPlaybackInterceptor {
            override fun onPlaySong(song: Song): Boolean = true
            override fun onPlayNext(song: Song): Boolean = true
            override fun onAddToQueue(song: Song): Boolean = true
            override fun onPlayQueue(songs: List<Song>, startIndex: Int): Boolean = true
            override fun onStartArtistRadio(artist: com.synq.features.music.domain.model.Artist): Boolean = true
        }

        assertFalse("Default onTogglePlayPause should return false", interceptor.onTogglePlayPause())
        assertFalse("Default onPlay should return false", interceptor.onPlay())
        assertFalse("Default onPause should return false", interceptor.onPause())
        assertFalse("Default onSeekTo should return false", interceptor.onSeekTo(1000L))
        assertFalse("Default onSkipToNext should return false", interceptor.onSkipToNext())
        assertFalse("Default onSkipToPrevious should return false", interceptor.onSkipToPrevious())
        assertFalse("Default onPlayQueueIndex should return false", interceptor.onPlayQueueIndex(0))
        assertFalse("Default onRemoveFromQueue should return false", interceptor.onRemoveFromQueue(0))
        assertFalse("Default onReorderQueue should return false", interceptor.onReorderQueue(0, 1))
        assertFalse("Default onToggleShuffle should return false", interceptor.onToggleShuffle())
        assertFalse("Default onToggleRepeat should return false", interceptor.onToggleRepeat())
        assertFalse("Default onTrackEnded should return false", interceptor.onTrackEnded())
    }

    @Test
    fun testJamPlaybackInterceptorActiveOverridesAllTransports() {
        var playPauseToggled = false
        var nextSkipped = false
        var trackEndedReported = false

        val activeInterceptor = object : JamPlaybackInterceptor {
            override fun onPlaySong(song: Song): Boolean = true
            override fun onPlayNext(song: Song): Boolean = true
            override fun onAddToQueue(song: Song): Boolean = true
            override fun onPlayQueue(songs: List<Song>, startIndex: Int): Boolean = true
            override fun onStartArtistRadio(artist: com.synq.features.music.domain.model.Artist): Boolean = true

            override fun onTogglePlayPause(): Boolean {
                playPauseToggled = true
                return true
            }

            override fun onSkipToNext(): Boolean {
                nextSkipped = true
                return true
            }

            override fun onTrackEnded(): Boolean {
                trackEndedReported = true
                return true
            }

            override fun onToggleShuffle(): Boolean = true
            override fun onToggleRepeat(): Boolean = true
        }

        assertTrue(activeInterceptor.onTogglePlayPause())
        assertTrue(playPauseToggled)

        assertTrue(activeInterceptor.onSkipToNext())
        assertTrue(nextSkipped)

        assertTrue(activeInterceptor.onTrackEnded())
        assertTrue(trackEndedReported)

        assertTrue(activeInterceptor.onToggleShuffle())
        assertTrue(activeInterceptor.onToggleRepeat())
    }

    @Test
    fun testJamStateMappingFromSnapshot() {
        val song1 = sampleSong("s1", "Song 1")
        val song2 = sampleSong("s2", "Song 2")

        val members = listOf(
            JamMember("u1", "HostUser", null, 1000L, true, 1000L, JamPermissions(playback = true, queueAdd = true, queueManage = true)),
            JamMember("u2", "GuestUser", null, 1005L, true, 1005L, JamPermissions(playback = false, queueAdd = true, queueManage = false))
        )

        val queue = listOf(
            JamQueueItem("q1", song1, "u1", 1000L, false),
            JamQueueItem("q2", song2, "u2", 1005L, true) // recommended
        )

        val snapshot = RoomSnapshot(
            code = "ABCD",
            version = 42L,
            serverNow = 2000L,
            hostUserId = "u1",
            createdAt = 1000L,
            members = members,
            defaults = JamPermissions(playback = false, queueAdd = true, queueManage = false),
            queue = queue,
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = queue[0],
                status = JamPlaybackStatus.PLAYING,
                positionMs = 5000L,
                anchorServerTs = 2000L,
                trackSeq = 1L
            )
        )

        // Verify participant mapping
        val isHost = snapshot.hostUserId == "u1"
        assertTrue("User u1 should be host", isHost)

        val participants = snapshot.members.map { m ->
            JamParticipantUi(
                socketId = m.userId,
                userId = m.userId,
                username = m.username,
                avatarUrl = m.avatarUrl,
                isHost = m.userId == snapshot.hostUserId
            )
        }
        assertEquals(2, participants.size)
        assertTrue("First participant should be marked as host", participants[0].isHost)
        assertFalse("Second participant should not be marked as host", participants[1].isHost)

        // Verify queue partitioning: user items vs recommended
        val userItems = snapshot.queue.filter { !it.recommended }
        val recommendedItems = snapshot.queue.filter { it.recommended }
        assertEquals(1, userItems.size)
        assertEquals("q1", userItems[0].qid)
        assertEquals(1, recommendedItems.size)
        assertEquals("q2", recommendedItems[0].qid)
    }

    @Test
    fun testExpectedPositionCalculation() {
        val clock = ClockSync(
            monotonicNowMs = { 10_000L },
            wallClockNowMs = { 10_000L }
        )
        // Record single sample
        clock.recordSample(10_000L, 10_510L, 10_020L)

        val song = sampleSong("s1", duration = 200)
        val nowPlaying = JamNowPlaying(
            item = JamQueueItem("q1", song, "u1", 1000L, false),
            status = JamPlaybackStatus.PLAYING,
            positionMs = 10_000L,
            anchorServerTs = 10_000L, // anchored at server time 10000
            trackSeq = 1L
        )

        // At server time 10500: expected = 10000 + (10500 - 10000) = 10500
        val expected = clock.expectedPosition(nowPlaying, now = 10_500L)
        assertEquals(10_500L, expected)

        // When paused at 10000: expected remains 10000 regardless of serverNow
        val paused = nowPlaying.copy(status = JamPlaybackStatus.PAUSED)
        val pausedExpected = clock.expectedPosition(paused, now = 10_500L)
        assertEquals(10_000L, pausedExpected)
    }
}
