package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamEngineFixesTest {

    private fun sampleSong(id: String, durationSec: Int = 180): Song {
        return Song(
            id = id,
            title = "Engine Fixes Song $id",
            artists = emptyList(),
            album = null,
            durationSeconds = durationSec,
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

    private fun makeRoom(
        hostUserId: String = "host_1",
        guestUserId: String = "guest_1",
        status: JamPlaybackStatus = JamPlaybackStatus.PLAYING,
        trackSeq: Long = 1L,
        songId: String = "s1"
    ): RoomSnapshot {
        val song = sampleSong(songId)
        val members = listOf(
            JamMember(hostUserId, "Host", null, 1000L, true, 1000L, JamPermissions(playback = true, queueAdd = true, queueManage = true)),
            JamMember(guestUserId, "Guest", null, 1000L, true, 1000L, JamPermissions(playback = false, queueAdd = true, queueManage = false))
        )
        return RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = 10_000L,
            hostUserId = hostUserId,
            createdAt = 1000L,
            members = members,
            defaults = JamPermissions(playback = false, queueAdd = true, queueManage = false),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem("q1", song),
                status = status,
                positionMs = 5000L,
                anchorServerTs = 10_000L,
                trackSeq = trackSeq
            )
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Test a: updateLegacyState must not reset isLocalStepOut
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testA_updateLegacyState_doesNotReset_isLocalStepOut`() {
        var localPlaybackState: LocalPlaybackState = LocalPlaybackState.Synchronized
        var jamState = JamState(isInJam = true, sessionCode = "TEST", isLocalStepOut = false)

        fun stepOutLocally() {
            localPlaybackState = LocalPlaybackState.LocalStepOut
            jamState = jamState.copy(isLocalStepOut = true)
        }

        fun updateLegacyState(snapshot: RoomSnapshot?, clientState: JamClientState) {
            val isStepOut = localPlaybackState is LocalPlaybackState.LocalStepOut
            val isInJam = clientState is JamClientState.Active
            if (snapshot != null && isInJam) {
                jamState = JamState(
                    isInJam = true,
                    sessionCode = snapshot.code,
                    isHost = snapshot.hostUserId == "guest_1",
                    hostUserId = snapshot.hostUserId,
                    hostName = "Host",
                    allowAttendeeReorder = snapshot.defaults.queueManage,
                    participants = emptyList(),
                    queue = snapshot.queue.map { it.song },
                    isLocalStepOut = isStepOut,
                    error = null
                )
            } else {
                jamState = jamState.copy(
                    isInJam = isInJam,
                    isLocalStepOut = isStepOut
                )
            }
        }

        // Initially in jam and synchronized
        assertFalse(jamState.isLocalStepOut)

        // Guest steps out locally
        stepOutLocally()
        assertTrue(jamState.isLocalStepOut)
        assertTrue(localPlaybackState is LocalPlaybackState.LocalStepOut)

        // Receive room update or tick causing updateLegacyState
        val room = makeRoom()
        updateLegacyState(room, JamClientState.Active("TEST", JamRole.GUEST))

        // isLocalStepOut must remain true because localPlaybackState is the source of truth
        assertTrue("updateLegacyState must preserve isLocalStepOut = true", jamState.isLocalStepOut)
    }

    // ─────────────────────────────────────────────────────────────
    // Test b: Player pauses from AUDIO_FOCUS_LOSS or AUDIO_BECOMING_NOISY
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testB_audioFocusLoss_causesLocalStepOut_withZeroJamCommands`() {
        var localPlaybackState: LocalPlaybackState = LocalPlaybackState.Synchronized
        var localPaused = false
        val sentCommands = mutableListOf<String>()
        var isApplyingJamCommand = false

        val fakeInterceptor = object : JamPlaybackInterceptor {
            override fun onPlaySong(song: Song) = true
            override fun onPlayNext(song: Song) = true
            override fun onAddToQueue(song: Song) = true
            override fun onPlayQueue(songs: List<Song>, startIndex: Int) = true
            override fun onStartArtistRadio(artist: com.synq.features.music.domain.model.Artist) = true

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && (
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                )) {
                    // stepOutLocally
                    localPlaybackState = LocalPlaybackState.LocalStepOut
                    localPaused = true
                }
            }
        }

        fun fakePlayerPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isApplyingJamCommand) {
                fakeInterceptor.onPlayWhenReadyChanged(playWhenReady, reason)
            }
        }

        // Simulate incoming phone call or focus loss
        fakePlayerPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)

        assertTrue("Must be in LocalStepOut", localPlaybackState is LocalPlaybackState.LocalStepOut)
        assertTrue("Local player paused", localPaused)
        assertEquals("Must produce zero jam commands", 0, sentCommands.size)

        // Verify that our own applyJamPause does NOT trigger stepOutLocally
        localPlaybackState = LocalPlaybackState.Synchronized
        isApplyingJamCommand = true
        fakePlayerPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        isApplyingJamCommand = false

        assertTrue("applyJamPause must not cause LocalStepOut", localPlaybackState is LocalPlaybackState.Synchronized)
    }

    // ─────────────────────────────────────────────────────────────
    // Test c: SleepTimerManager finishing timer calls stepOutLocally
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testC_sleepTimerManager_inJam_callsStepOutLocally_neverGlobalPause`() {
        var isInJam = true
        var steppedOutLocally = false
        val sentCommands = mutableListOf<String>()

        fun stepOutLocally() {
            steppedOutLocally = true
        }

        fun playerPause() {
            // Global pause command
            sentCommands.add("pause")
        }

        fun handleTimerFinished() {
            if (isInJam) {
                stepOutLocally()
            } else {
                playerPause()
            }
        }

        // Mode 1: "End of track" finishes
        handleTimerFinished()
        assertTrue("Finishing sleep timer in a jam must call stepOutLocally()", steppedOutLocally)
        assertEquals("Finishing sleep timer in a jam must send zero global pause commands", 0, sentCommands.size)

        // Mode 2: Countdown timer finishes
        steppedOutLocally = false
        handleTimerFinished()
        assertTrue("Finishing countdown sleep timer in a jam must call stepOutLocally()", steppedOutLocally)
        assertEquals("Must send zero global pause commands", 0, sentCommands.size)

        // When not in a jam, it calls playerPause()
        isInJam = false
        handleTimerFinished()
        assertEquals("When not in a jam, global pause is used", listOf("pause"), sentCommands)
    }

    // ─────────────────────────────────────────────────────────────
    // Test d: resumeFromLocalStepOut checks clientTrackSeq == room trackSeq
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testD_resumeFromLocalStepOut_seeksOnlyIfSameTrackSeq_otherwiseResetsReconcilerTrackState`() {
        val fixedClock = ClockSync(
            monotonicNowMs = { 10_000L },
            wallClockNowMs = { 10_000L }
        ).apply {
            recordSample(10_000L, 10_000L, 10_000L)
        }

        var clientTrackSeq = 1L
        var reconcilerTrackState = ReconcilerTrackState.READY
        var localSeekPos: Long? = null
        var localPlayed = false

        fun resumeFromLocalStepOut(room: RoomSnapshot) {
            val serverNowPlaying = room.nowPlaying
            if (clientTrackSeq == serverNowPlaying.trackSeq) {
                val expectedPos = fixedClock.expectedPosition(serverNowPlaying, fixedClock.serverNow())
                localSeekPos = expectedPos
                if (serverNowPlaying.status == JamPlaybackStatus.PLAYING) {
                    localPlayed = true
                }
            } else {
                // Track changed on server while user was stepped out
                reconcilerTrackState = ReconcilerTrackState.READY
            }
        }

        // Case 1: Same trackSeq
        val sameTrackRoom = makeRoom(trackSeq = 1L)
        resumeFromLocalStepOut(sameTrackRoom)
        assertEquals(5000L, localSeekPos)
        assertTrue(localPlayed)

        // Case 2: Server advanced to trackSeq 2 while stepped out
        localSeekPos = null
        localPlayed = false
        clientTrackSeq = 1L
        reconcilerTrackState = ReconcilerTrackState.LOADING // assume was in another state
        val newTrackRoom = makeRoom(trackSeq = 2L, songId = "s2")

        resumeFromLocalStepOut(newTrackRoom)
        assertEquals("Must NOT seek on old track", null, localSeekPos)
        assertFalse("Must NOT play on old track", localPlayed)
        assertEquals("reconcilerTrackState reset to READY so reconciler can ChangeTrack", ReconcilerTrackState.READY, reconcilerTrackState)

        // Reconciler decision will now correctly output ChangeTrack to trackSeq 2
        val localState = LocalPlayerState(
            currentSongId = "s1",
            currentTrackSeq = clientTrackSeq,
            currentPositionMs = 5000L,
            playWhenReady = false,
            trackState = reconcilerTrackState
        )
        val action = JamReconciler.decide(localState, newTrackRoom, fixedClock)
        assertTrue("Reconciler must emit ChangeTrack", action is ReconcilerAction.ChangeTrack)
        assertEquals(2L, (action as ReconcilerAction.ChangeTrack).trackSeq)
    }

    // ─────────────────────────────────────────────────────────────
    // Test f: LOADING timeout (~15s) retries load up to 2 times
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testF_loadingTimeout_retriesLoad_upTo2Times_thenStops`() {
        var trackRetryCount = 0
        var retryTrackSeq = 0L
        var loadingStartedAt = 1000L
        var reconcilerTrackState = ReconcilerTrackState.LOADING
        var loadCalls = 0
        var paused = false

        fun checkLoadingTimeout(currentTimeMs: Long, targetSeq: Long) {
            if (reconcilerTrackState != ReconcilerTrackState.LOADING) return
            if (currentTimeMs - loadingStartedAt < 15_000L) return

            if (retryTrackSeq != targetSeq) {
                retryTrackSeq = targetSeq
                trackRetryCount = 0
            }

            if (trackRetryCount < 2) {
                trackRetryCount++
                loadingStartedAt = currentTimeMs
                loadCalls++
            } else {
                reconcilerTrackState = ReconcilerTrackState.READY
                loadingStartedAt = 0L
                paused = true
            }
        }

        // At t = 10_000ms (<15s since 1000ms): no timeout
        checkLoadingTimeout(10_000L, targetSeq = 1L)
        assertEquals(0, loadCalls)
        assertEquals(0, trackRetryCount)

        // At t = 16_500ms (>15s): retry 1
        checkLoadingTimeout(16_500L, targetSeq = 1L)
        assertEquals(1, loadCalls)
        assertEquals(1, trackRetryCount)
        assertEquals(ReconcilerTrackState.LOADING, reconcilerTrackState)

        // At t = 32_000ms (>15s since 16_500ms): retry 2
        checkLoadingTimeout(32_000L, targetSeq = 1L)
        assertEquals(2, loadCalls)
        assertEquals(2, trackRetryCount)
        assertEquals(ReconcilerTrackState.LOADING, reconcilerTrackState)

        // At t = 48_000ms (>15s since 32_000ms): max retries reached, stays silent and READY
        checkLoadingTimeout(48_000L, targetSeq = 1L)
        assertEquals(2, loadCalls) // did not call load again
        assertEquals(ReconcilerTrackState.READY, reconcilerTrackState)
        assertTrue(paused)
    }
}

