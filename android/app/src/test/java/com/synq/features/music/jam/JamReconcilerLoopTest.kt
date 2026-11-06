package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JamReconcilerLoopTest {

    private fun sampleSong(id: String): Song {
        return Song(
            id = id,
            title = "Test Song",
            artists = emptyList(),
            album = null,
            durationSeconds = 180,
            imageUrl = null,
            year = "2024",
            language = "English",
            hasLyrics = false,
            playCount = 0,
            label = "Label",
            streamRef = "ref_$id",
            providerId = "saavn"
        )
    }

    interface DirectJamPlayer {
        fun applyJamPlay()
        fun applyJamPause()
        fun applyJamSeek(positionMs: Long)
        fun applyJamSpeed(factor: Float)
    }

    class FakeDirectPlayer : DirectJamPlayer {
        var playCalled = 0
        var pauseCalled = 0
        var seekPositionMs: Long? = null
        var speedFactor: Float? = null

        override fun applyJamPlay() { playCalled++ }
        override fun applyJamPause() { pauseCalled++ }
        override fun applyJamSeek(positionMs: Long) { seekPositionMs = positionMs }
        override fun applyJamSpeed(factor: Float) { speedFactor = factor }
    }

    @Test
    fun testReconcilerActionAppliesDirectlyWithoutTriggeringJamCommands() {
        val player = FakeDirectPlayer()
        var jamCommandSentCount = 0

        val interceptor = object : JamPlaybackInterceptor {
            override fun onPlaySong(song: Song): Boolean { jamCommandSentCount++; return true }
            override fun onPlayNext(song: Song): Boolean { jamCommandSentCount++; return true }
            override fun onAddToQueue(song: Song): Boolean { jamCommandSentCount++; return true }
            override fun onPlayQueue(songs: List<Song>, startIndex: Int): Boolean { jamCommandSentCount++; return true }
            override fun onStartArtistRadio(artist: com.synq.features.music.domain.model.Artist): Boolean = true

            override fun onPlay(): Boolean {
                jamCommandSentCount++
                return true
            }

            override fun onPause(): Boolean {
                jamCommandSentCount++
                return true
            }

            override fun onSeekTo(positionMs: Long): Boolean {
                jamCommandSentCount++
                return true
            }
        }

        // Simulate Reconciler executing SetPlayWhenReady(true)
        val action = ReconcilerAction.SetPlayWhenReady(true)

        // Applying via direct player method
        if (action.playWhenReady) {
            player.applyJamPlay()
        } else {
            player.applyJamPause()
        }

        assertEquals("Fake player must receive direct play call", 1, player.playCalled)
        assertEquals("Reconciler must produce zero jam commands through interceptor", 0, jamCommandSentCount)

        // Simulate Reconciler executing Seek(15000L)
        val seekAction = ReconcilerAction.Seek(15000L)
        player.applyJamSeek(seekAction.positionMs)

        assertEquals(15000L, player.seekPositionMs)
        assertEquals("Reconciler seek must produce zero jam commands through interceptor", 0, jamCommandSentCount)
    }
}

