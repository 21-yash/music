package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamPrefetchQueueHeadTest {

    private class RecordingPlayerBridge : JamPlayerBridge {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override var jamInterceptor: JamPlaybackInterceptor? = null
        val prefetchedSongs = mutableListOf<Song>()

        override fun getPlayerCurrentPosition(): Long = 0L
        override fun getPlayerPlayWhenReady(): Boolean = false
        override fun getPlayerPlaybackState(): Int = Player.STATE_IDLE
        override fun getPlaybackSpeed(): Float = 1.0f
        override fun getPlayerDuration(): Long = 180_000L

        override fun prefetchTrack(song: Song) {
            prefetchedSongs.add(song)
        }

        override fun playJamSyncSong(
            song: Song,
            initialPositionMs: Long,
            playWhenReady: Boolean,
            targetQid: String?,
            forceRefresh: Boolean
        ) {}
        override fun applyJamPlay() {}
        override fun applyJamPause() {}
        override fun stop() {}
        override fun applyJamSeek(positionMs: Long) {}
        override fun applyJamSpeed(factor: Float) {}
        override fun setJamQueue(
            jamQueue: List<Song>,
            targetSong: Song?,
            targetQid: String?,
            queueQids: List<String>,
            recommendedQids: Set<String>
        ) {}
        override fun stashPersonalQueue() {}
        override fun restorePersonalQueue() {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun invalidateStreamCache(songId: String) {}
    }

    private class StubTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(extraBufferCapacity = 64)
        override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = true

        override fun connect(token: String?) { isConnected = true }
        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> = Result.success(Unit)
        override fun disconnect() { isConnected = false }
        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> =
            Result.success(JSONObject().apply { put("ok", true) })
    }

    private class StubPreferences : JamPreferencesBridge {
        override fun saveLastJam(code: String, avatarUrl: String?) {}
        override fun getLastAvatarUrl(): String? = null
        override fun clearLastJam() {}
        override fun rejoinCandidate(): String? = null
    }

    private fun makeSong(id: String): Song {
        return Song(
            id = id,
            title = "Title $id",
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
    }

    private fun makeSnapshot(queue: List<JamQueueItem>, version: Long = 1L): RoomSnapshot {
        return RoomSnapshot(
            code = "TEST",
            version = version,
            serverNow = 1000L,
            hostUserId = "host_1",
            createdAt = 1000L,
            members = listOf(JamMember("host_1", "Host", perms = JamPermissions(true, true, true))),
            defaults = JamPermissions(true, true, true),
            queue = queue,
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem("np_qid", makeSong("now_playing"), "host_1"),
                status = JamPlaybackStatus.PLAYING,
                positionMs = 0L,
                anchorServerTs = 1000L,
                trackSeq = 1L
            )
        )
    }

    @Test
    fun testQueueHeadChange_triggersPrefetch_sameHead_skipsPrefetch() = runTest {
        val stateStore = JamStateStore(myUserId = "host_1")
        val clockSync = ClockSync()
        val transport = StubTransport()
        val client = JamClient(
            transport = transport,
            clockSync = clockSync,
            stateStore = stateStore,
            tokenProvider = { "token" },
            monotonicNowMs = { System.currentTimeMillis() },
            autoClockSync = false,
            ioDispatcher = Dispatchers.Unconfined,
            scope = backgroundScope
        )

        val player = RecordingPlayerBridge()
        val sessionManager = JamSessionManager(
            playerManager = player,
            jamPreferences = StubPreferences(),
            coroutineScope = backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore
        )

        // 1. Snapshot with queue [Q1, Q2]
        val song1 = makeSong("song1")
        val song2 = makeSong("song2")
        val item1 = JamQueueItem("Q1", song1, "host_1")
        val item2 = JamQueueItem("Q2", song2, "host_1")

        stateStore.applySnapshot(makeSnapshot(listOf(item1, item2), version = 1L))
        runCurrent()

        assertEquals(1, player.prefetchedSongs.size)
        assertEquals("song1", player.prefetchedSongs[0].id)

        // 2. Same head Q1, but queue changed: [Q1, Q3]
        val song3 = makeSong("song3")
        val item3 = JamQueueItem("Q3", song3, "host_1")
        stateStore.applySnapshot(makeSnapshot(listOf(item1, item3), version = 2L))
        runCurrent()

        // Still 1 prefetch because Q1 was already prefetched as queue head!
        assertEquals(1, player.prefetchedSongs.size)

        // 3. New head Q2: [Q2, Q3]
        stateStore.applySnapshot(makeSnapshot(listOf(item2, item3), version = 3L))
        runCurrent()

        // Now 2 prefetches, and second is song2!
        assertEquals(2, player.prefetchedSongs.size)
        assertEquals("song2", player.prefetchedSongs[1].id)
    }
}
