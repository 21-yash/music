package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamQueueSyncTest {

    private fun sampleSong(id: String, title: String = "Song $id"): Song {
        return Song(
            id = id,
            title = title,
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

    class QueueSyncHarness(
        private val getRoom: () -> RoomSnapshot?,
        private val onSetJamQueue: (List<Song>, Song?, Set<String>) -> Unit,
        private val onSendPlayItem: (String) -> Unit,
        private val onSendQueueRemove: (String) -> Unit,
        private val onSendQueueMove: (String, Int) -> Unit
    ) {
        var lastAppliedSnapshotVersion: Long? = null
            private set
        var lastAppliedNowPlayingQid: String? = null
            private set

        fun getFullQueue(): List<JamQueueItem> {
            val room = getRoom() ?: return emptyList()
            return listOfNotNull(room.nowPlaying.item) + room.queue
        }

        fun syncJamQueue(room: RoomSnapshot, force: Boolean = false) {
            val currentNowPlayingQid = room.nowPlaying.item?.qid
            val versionChanged = room.version != lastAppliedSnapshotVersion
            val nowPlayingChanged = currentNowPlayingQid != lastAppliedNowPlayingQid

            if (force || versionChanged || nowPlayingChanged) {
                lastAppliedSnapshotVersion = room.version
                lastAppliedNowPlayingQid = currentNowPlayingQid

                val fullQueue = listOfNotNull(room.nowPlaying.item) + room.queue
                val fullQueueSongs = fullQueue.map { it.song }
                val recIds = room.queue.filter { it.recommended }.map { it.song.id }.toSet()
                val targetSong = room.nowPlaying.item?.song

                onSetJamQueue(fullQueueSongs, targetSong, recIds)
            }
        }

        fun playQueueIndex(index: Int) {
            val room = getRoom() ?: return
            val fullQueue = getFullQueue()
            val item = fullQueue.getOrNull(index) ?: return
            if (item.qid == room.nowPlaying.item?.qid) {
                // Currently playing item, do not send playItem
                return
            }
            onSendPlayItem(item.qid)
        }

        fun removeSongFromJam(index: Int) {
            val room = getRoom() ?: return
            val fullQueue = getFullQueue()
            val item = fullQueue.getOrNull(index) ?: return
            if (item.qid == room.nowPlaying.item?.qid) {
                return
            }
            onSendQueueRemove(item.qid)
        }

        fun reorderJamQueue(fromIndex: Int, toIndex: Int) {
            val room = getRoom() ?: return
            val fullQueue = getFullQueue()
            val item = fullQueue.getOrNull(fromIndex) ?: return
            if (item.qid == room.nowPlaying.item?.qid) {
                return
            }
            val hasNowPlaying = room.nowPlaying.item != null
            val offset = if (hasNowPlaying) 1 else 0
            val targetQueueIndex = (toIndex - offset).coerceAtLeast(0)
            onSendQueueMove(item.qid, targetQueueIndex)
        }
    }

    @Test
    fun `full queue includes nowPlaying item followed by queue`() {
        val sNow = sampleSong("s_now", "Now Song")
        val s1 = sampleSong("s1", "Song 1")
        val s2 = sampleSong("s2", "Song 2")

        val nowItem = JamQueueItem("qid_now", sNow, "u1", 1000L, false)
        val qItem1 = JamQueueItem("qid_1", s1, "u1", 1005L, false)
        val qItem2 = JamQueueItem("qid_2", s2, "u2", 1010L, true)

        var room: RoomSnapshot? = RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = 2000L,
            hostUserId = "u1",
            createdAt = 1000L,
            members = emptyList(),
            defaults = JamPermissions(),
            queue = listOf(qItem1, qItem2),
            history = emptyList(),
            nowPlaying = JamNowPlaying(item = nowItem, status = JamPlaybackStatus.PLAYING, trackSeq = 1L)
        )

        var setQueueCalls = 0
        var recordedQueue: List<Song>? = null
        var recordedTarget: Song? = null
        var recordedRecIds: Set<String>? = null

        val harness = QueueSyncHarness(
            getRoom = { room },
            onSetJamQueue = { q, target, recs ->
                setQueueCalls++
                recordedQueue = q
                recordedTarget = target
                recordedRecIds = recs
            },
            onSendPlayItem = {},
            onSendQueueRemove = {},
            onSendQueueMove = { _, _ -> }
        )

        val fullQueue = harness.getFullQueue()
        assertEquals(3, fullQueue.size)
        assertEquals("qid_now", fullQueue[0].qid)
        assertEquals("qid_1", fullQueue[1].qid)
        assertEquals("qid_2", fullQueue[2].qid)

        harness.syncJamQueue(room!!)
        assertEquals(1, setQueueCalls)
        assertEquals(listOf(sNow, s1, s2), recordedQueue)
        assertEquals(sNow, recordedTarget)
        assertEquals(setOf("s2"), recordedRecIds)
    }

    @Test
    fun `version guard prevents setJamQueue on 1s tick`() {
        val sNow = sampleSong("s_now")
        val nowItem = JamQueueItem("qid_now", sNow, "u1", 1000L, false)

        val room = RoomSnapshot(
            code = "TEST",
            version = 5L,
            serverNow = 2000L,
            hostUserId = "u1",
            createdAt = 1000L,
            members = emptyList(),
            defaults = JamPermissions(),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(item = nowItem, status = JamPlaybackStatus.PLAYING, trackSeq = 1L)
        )

        var setQueueCalls = 0
        val harness = QueueSyncHarness(
            getRoom = { room },
            onSetJamQueue = { _, _, _ -> setQueueCalls++ },
            onSendPlayItem = {},
            onSendQueueRemove = {},
            onSendQueueMove = { _, _ -> }
        )

        // First application with version 5
        harness.syncJamQueue(room)
        assertEquals(1, setQueueCalls)

        // Simulate 5 periodic 1-second ticks where version is still 5
        repeat(5) {
            harness.syncJamQueue(room)
        }
        assertEquals("setJamQueue must not be called during 1s ticks without version change", 1, setQueueCalls)

        // Snapshot with updated version 6 (e.g. song added)
        val roomV6 = room.copy(version = 6L)
        harness.syncJamQueue(roomV6)
        assertEquals("setJamQueue must be called when snapshot version changes", 2, setQueueCalls)
    }

    @Test
    fun `queue manipulation uses server qid and handles duplicates correctly`() {
        // Two duplicate songs in queue with identical song ID "dup_song"
        val sNow = sampleSong("s_now")
        val sDup1 = sampleSong("dup_song", "Duplicate Track")
        val sOther = sampleSong("s_other", "Other Track")
        val sDup2 = sampleSong("dup_song", "Duplicate Track")

        val nowItem = JamQueueItem("qid_now", sNow, "u1", 1000L, false)
        val q1 = JamQueueItem("qid_dup_1", sDup1, "u1", 1001L, false)
        val q2 = JamQueueItem("qid_other", sOther, "u2", 1002L, false)
        val q3 = JamQueueItem("qid_dup_2", sDup2, "u1", 1003L, false)

        val room = RoomSnapshot(
            code = "TEST",
            version = 1L,
            serverNow = 2000L,
            hostUserId = "u1",
            createdAt = 1000L,
            members = emptyList(),
            defaults = JamPermissions(),
            queue = listOf(q1, q2, q3),
            history = emptyList(),
            nowPlaying = JamNowPlaying(item = nowItem, status = JamPlaybackStatus.PLAYING, trackSeq = 1L)
        )

        var lastPlayQid: String? = null
        var lastRemovedQid: String? = null
        var lastMovedQid: String? = null
        var lastMovedTargetIdx: Int? = null

        val harness = QueueSyncHarness(
            getRoom = { room },
            onSetJamQueue = { _, _, _ -> },
            onSendPlayItem = { lastPlayQid = it },
            onSendQueueRemove = { lastRemovedQid = it },
            onSendQueueMove = { qid, idx ->
                lastMovedQid = qid
                lastMovedTargetIdx = idx
            }
        )

        // In fullQueue:
        // index 0 -> qid_now (nowPlaying)
        // index 1 -> qid_dup_1 (first duplicate)
        // index 2 -> qid_other
        // index 3 -> qid_dup_2 (second duplicate)

        // 1. Remove index 3 (second duplicate) -> must use "qid_dup_2" specifically, not "dup_song" or "qid_dup_1"
        harness.removeSongFromJam(3)
        assertEquals("qid_dup_2", lastRemovedQid)

        // 2. Play index 1 (first duplicate) -> must send "qid_dup_1"
        harness.playQueueIndex(1)
        assertEquals("qid_dup_1", lastPlayQid)

        // 3. Play index 0 (now playing) -> must not send playItem
        lastPlayQid = null
        harness.playQueueIndex(0)
        assertNull("Playing index 0 (nowPlaying) should not send playItem", lastPlayQid)

        // 4. Reorder index 3 to index 2 (in fullQueue: moving second duplicate before other track)
        // Target index in room.queue is (toIndex - 1) = 2 - 1 = 1
        harness.reorderJamQueue(3, 2)
        assertEquals("qid_dup_2", lastMovedQid)
        assertEquals(1, lastMovedTargetIdx)
    }
}

