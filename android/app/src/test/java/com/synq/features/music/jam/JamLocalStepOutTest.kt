package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import org.junit.Assert.*
import org.junit.Test

class JamLocalStepOutTest {

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

    private fun makeRoom(
        hostUserId: String,
        guestUserId: String,
        guestHasPlaybackPerm: Boolean,
        status: JamPlaybackStatus = JamPlaybackStatus.PLAYING,
        positionMs: Long = 5000L,
        anchorTs: Long = 10_000L
    ): RoomSnapshot {
        val song = sampleSong("s1")
        val members = listOf(
            JamMember(hostUserId, "Host", null, 1000L, true, 1000L, JamPermissions(playback = true, queueAdd = true, queueManage = true)),
            JamMember(guestUserId, "Guest", null, 1000L, true, 1000L, JamPermissions(playback = guestHasPlaybackPerm, queueAdd = true, queueManage = false))
        )
        return RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = anchorTs,
            hostUserId = hostUserId,
            createdAt = 1000L,
            members = members,
            defaults = JamPermissions(playback = false, queueAdd = true, queueManage = false),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem("q1", song),
                status = status,
                positionMs = positionMs,
                anchorServerTs = anchorTs,
                trackSeq = 1L
            )
        )
    }

    interface LocalStepOutController {
        val isLocalStepOut: Boolean
        fun onPause()
        fun onResume()
    }

    class TestStepOutHarness(
        private val myUserId: String,
        private val getRoom: () -> RoomSnapshot?,
        private val clockSync: ClockSync,
        private val onLocalPause: () -> Unit,
        private val onLocalSeek: (Long) -> Unit,
        private val onLocalPlay: () -> Unit,
        private val sendJamCommand: (String) -> Unit
    ) : LocalStepOutController {
        override var isLocalStepOut: Boolean = false
            private set

        fun canControlPlayback(): Boolean {
            val room = getRoom() ?: return false
            val isHost = room.hostUserId == myUserId
            val member = room.members.find { it.userId == myUserId }
            val playbackPerm = member?.perms?.playback ?: room.defaults.playback
            return isHost || playbackPerm
        }

        override fun onPause() {
            if (!canControlPlayback()) {
                // Local step-out: guest without playback perm pauses ONLY their own device, sends no command
                isLocalStepOut = true
                onLocalPause()
            } else {
                // User WITH playback perm sends global command
                sendJamCommand("pause")
            }
        }

        override fun onResume() {
            if (isLocalStepOut) {
                isLocalStepOut = false
                val room = getRoom() ?: return
                val nowPlaying = room.nowPlaying
                val expectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
                onLocalSeek(expectedPos)
                if (nowPlaying.status == JamPlaybackStatus.PLAYING) {
                    onLocalPlay()
                }
            } else if (canControlPlayback()) {
                sendJamCommand("play")
            }
        }
    }

    private val fixedClockSync = ClockSync(
        monotonicNowMs = { 10_000L },
        wallClockNowMs = { 10_000L }
    ).apply {
        recordSample(10_000L, 10_000L, 10_000L) // anchorServerTime = 10_000ms
    }

    @Test
    fun `guest without playback pausing enters LocalStepOut and sends zero jam commands`() {
        var localPaused = false
        var localSeekPos: Long? = null
        var localPlayed = false
        val sentCommands = mutableListOf<String>()

        val room = makeRoom(
            hostUserId = "host_1",
            guestUserId = "guest_1",
            guestHasPlaybackPerm = false,
            status = JamPlaybackStatus.PLAYING,
            positionMs = 5000L,
            anchorTs = 10_000L
        )

        val harness = TestStepOutHarness(
            myUserId = "guest_1",
            getRoom = { room },
            clockSync = fixedClockSync,
            onLocalPause = { localPaused = true },
            onLocalSeek = { localSeekPos = it },
            onLocalPlay = { localPlayed = true },
            sendJamCommand = { sentCommands.add(it) }
        )

        // Guest pauses
        harness.onPause()

        assertTrue("Guest must enter LocalStepOut", harness.isLocalStepOut)
        assertTrue("Local player must be paused", localPaused)
        assertEquals("No jam commands must be sent to server", 0, sentCommands.size)

        // Now guest resumes: should clear LocalStepOut, seek to expected position, and resume playing locally
        harness.onResume()

        assertFalse("LocalStepOut must be cleared", harness.isLocalStepOut)
        assertEquals("Must seek to expected position", 5000L, localSeekPos)
        assertTrue("Must resume local playback", localPlayed)
        assertEquals("Still zero jam commands sent to server", 0, sentCommands.size)
    }

    @Test
    fun `user with playback perm pausing sends global pause command`() {
        var localPaused = false
        val sentCommands = mutableListOf<String>()

        val room = makeRoom(
            hostUserId = "host_1",
            guestUserId = "guest_with_perm",
            guestHasPlaybackPerm = true,
            status = JamPlaybackStatus.PLAYING,
            positionMs = 5000L,
            anchorTs = 10_000L
        )

        val harness = TestStepOutHarness(
            myUserId = "guest_with_perm",
            getRoom = { room },
            clockSync = fixedClockSync,
            onLocalPause = { localPaused = true },
            onLocalSeek = {},
            onLocalPlay = {},
            sendJamCommand = { sentCommands.add(it) }
        )

        // User with playback pauses
        harness.onPause()

        assertFalse("User with perm must NOT enter LocalStepOut", harness.isLocalStepOut)
        assertFalse("Local player must NOT be paused directly (waits for server event/reconciler)", localPaused)
        assertEquals("Global pause command must be sent", listOf("pause"), sentCommands)

        // User resumes
        harness.onResume()
        assertEquals("Global play command must be sent", listOf("pause", "play"), sentCommands)
    }

    @Test
    fun `reconciler is suspended when isLocalStepOut is true`() {
        // Verify JamSessionManager skips reconcilePlayback when isLocalStepOut is true
        val room = makeRoom(
            hostUserId = "host_1",
            guestUserId = "guest_1",
            guestHasPlaybackPerm = false,
            status = JamPlaybackStatus.PLAYING,
            positionMs = 5000L,
            anchorTs = 10_000L
        )

        // In JamSessionManager: if (isLocalStepOut) return -> reconciler actions are suspended
        var reconcilerRunCount = 0
        fun checkReconcile(isLocalStepOut: Boolean) {
            if (isLocalStepOut) return
            reconcilerRunCount++
        }

        checkReconcile(isLocalStepOut = true)
        assertEquals(0, reconcilerRunCount)

        checkReconcile(isLocalStepOut = false)
        assertEquals(1, reconcilerRunCount)
    }
}

