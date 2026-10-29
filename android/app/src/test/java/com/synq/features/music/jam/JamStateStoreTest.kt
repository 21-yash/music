package com.synq.features.music.jam

import com.synq.features.music.domain.model.Song
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class JamStateStoreTest {

    private fun makeSong(id: String): Song = Song(
        id = id,
        title = "Title $id",
        artists = emptyList(),
        album = null,
        durationSeconds = 200,
        imageUrl = null,
        year = "2024",
        language = "en",
        hasLyrics = false,
        playCount = 0,
        label = "",
        streamRef = "",
        providerId = "jiosaavn"
    )

    private fun makeSnapshot(code: String, version: Long, hostId: String): RoomSnapshot {
        return RoomSnapshot(
            code = code,
            version = version,
            serverNow = 1000L,
            hostUserId = hostId,
            createdAt = 1000L,
            members = listOf(
                JamMember(
                    userId = hostId,
                    username = "HostUser",
                    perms = JamPermissions(queueAdd = true, playback = true, queueManage = true)
                ),
                JamMember(
                    userId = "guest_1",
                    username = "GuestUser",
                    perms = JamPermissions(queueAdd = true, playback = false, queueManage = false)
                )
            ),
            defaults = JamPermissions(queueAdd = true, playback = false, queueManage = false),
            queue = listOf(
                JamQueueItem(qid = "q1", song = makeSong("s1"), addedByUserId = hostId, recommended = false),
                JamQueueItem(qid = "q2", song = makeSong("rec1"), addedByUserId = null, recommended = true)
            ),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem(qid = "np1", song = makeSong("np"), addedByUserId = hostId),
                status = JamPlaybackStatus.PLAYING,
                positionMs = 5000L,
                anchorServerTs = 1000L,
                trackSeq = 1L
            )
        )
    }

    @Test
    fun `isEpoch unconditionally accepts snapshot even if version is lower or equal`() {
        val store = JamStateStore(myUserId = "guest_1")

        // First apply version 100
        val s100 = makeSnapshot("ROOM", 100L, "host_1")
        store.applySnapshot(s100, isEpoch = false)
        assertEquals(100L, store.room.value?.version)

        // Epoch snapshot with version 5 (e.g. Server restart / new room code)
        val s5 = makeSnapshot("ROOM", 5L, "host_1")
        val accepted = store.applySnapshot(s5, isEpoch = true)

        assertTrue(accepted)
        assertEquals(5L, store.room.value?.version)
    }

    @Test
    fun `non-epoch drops stale snapshot with version less than or equal to current`() {
        val store = JamStateStore(myUserId = "guest_1")

        val s10 = makeSnapshot("ROOM", 10L, "host_1")
        store.applySnapshot(s10, isEpoch = false)

        // Stale snapshot with version 9
        val s9 = makeSnapshot("ROOM", 9L, "host_1")
        val accepted9 = store.applySnapshot(s9, isEpoch = false)
        assertFalse(accepted9)
        assertEquals(10L, store.room.value?.version)

        // Duplicate snapshot with version 10
        val s10Dup = makeSnapshot("ROOM", 10L, "host_1")
        val accepted10 = store.applySnapshot(s10Dup, isEpoch = false)
        assertFalse(accepted10)
        assertEquals(10L, store.room.value?.version)

        // Newer snapshot with version 11
        val s11 = makeSnapshot("ROOM", 11L, "host_1")
        val accepted11 = store.applySnapshot(s11, isEpoch = false)
        assertTrue(accepted11)
        assertEquals(11L, store.room.value?.version)
    }

    @Test
    fun `applyTick updates position and anchor when trackSeq matches`() {
        val store = JamStateStore(myUserId = "host_1")
        store.applySnapshot(makeSnapshot("ROOM", 1L, "host_1"), isEpoch = true)

        val tick = Jam2Tick(
            code = "ROOM",
            serverTime = 15000L,
            nowPlaying = JamTickNowPlaying(
                positionMs = 12000L,
                anchorServerTs = 15000L,
                status = JamPlaybackStatus.PLAYING,
                trackSeq = 1L // Matches current trackSeq
            )
        )

        val updated = store.applyTick(tick)
        assertTrue(updated)
        assertEquals(12000L, store.room.value?.nowPlaying?.positionMs)
        assertEquals(15000L, store.room.value?.nowPlaying?.anchorServerTs)
    }

    @Test
    fun `applyTick ignores tick when trackSeq does not match`() {
        val store = JamStateStore(myUserId = "host_1")
        store.applySnapshot(makeSnapshot("ROOM", 1L, "host_1"), isEpoch = true)

        val staleTick = Jam2Tick(
            code = "ROOM",
            serverTime = 15000L,
            nowPlaying = JamTickNowPlaying(
                positionMs = 99999L,
                anchorServerTs = 15000L,
                status = JamPlaybackStatus.PLAYING,
                trackSeq = 999L // Mismatched trackSeq
            )
        )

        val updated = store.applyTick(staleTick)
        assertFalse(updated)
        assertEquals(5000L, store.room.value?.nowPlaying?.positionMs)
    }

    @Test
    fun `recomputes UiState correctly with user and recommended queues separated`() {
        val store = JamStateStore(myUserId = "host_1")
        store.applySnapshot(makeSnapshot("ROOM", 1L, "host_1"), isEpoch = true)

        val ui = store.uiState.value
        assertTrue(ui.isInJam)
        assertTrue(ui.isHost)
        assertEquals("ROOM", ui.sessionCode)
        assertEquals("HostUser", ui.hostName)
        assertTrue(ui.canControlPlayback)
        assertTrue(ui.canAddSongs)
        assertTrue(ui.canManageQueue)

        assertEquals(2, ui.queue.size)
        assertEquals(1, ui.userQueue.size)
        assertEquals("q1", ui.userQueue[0].qid)
        assertEquals(1, ui.recommendedQueue.size)
        assertEquals("q2", ui.recommendedQueue[0].qid)
    }

    @Test
    fun `applyEvent kicked emits Kicked only for myUserId and Toast for bystander`() = kotlinx.coroutines.test.runTest {
        val bystanderStore = JamStateStore(myUserId = "bystander_id")
        val kickedEvent = Jam2Event(kind = "kicked", userId = "other_user", username = "Bob")

        val bystanderEvents = mutableListOf<JamUiEvent>()
        val job = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            bystanderStore.uiEvents.collect { bystanderEvents.add(it) }
        }

        bystanderStore.applyEvent(kickedEvent)
        assertEquals(1, bystanderEvents.size)
        assertTrue(bystanderEvents[0] is JamUiEvent.Toast)
        assertEquals("Bob was removed", (bystanderEvents[0] as JamUiEvent.Toast).message)

        // Now test for kicked user
        val targetStore = JamStateStore(myUserId = "target_id")
        val targetEvents = mutableListOf<JamUiEvent>()
        val targetJob = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            targetStore.uiEvents.collect { targetEvents.add(it) }
        }

        targetStore.applyEvent(Jam2Event(kind = "kicked", userId = "target_id", username = "Target"))
        assertEquals(1, targetEvents.size)
        assertTrue(targetEvents[0] is JamUiEvent.Kicked)
    }
}
