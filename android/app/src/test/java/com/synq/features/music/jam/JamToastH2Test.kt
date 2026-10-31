package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamToastH2Test {

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = true

        fun emitEvent(event: JamTransport.Event) {
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            isConnected = true
            emitEvent(JamTransport.Event.Connected)
        }

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> = Result.success(Unit)
        override fun disconnect() { isConnected = false }
        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> =
            Result.success(JSONObject().apply {
                put("ok", true)
                put("room", JSONObject().apply {
                    put("code", "ABCD")
                    put("version", 1L)
                    put("serverNow", 1000L)
                    put("hostUserId", "host_1")
                    put("createdAt", 1000L)
                    put("members", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("userId", "guest_1")
                            put("username", "Guest 1")
                            put("joinedAt", 1000L)
                            put("perms", JSONObject().apply {
                                put("playback", false)
                                put("queueAdd", true)
                                put("queueManage", false)
                            })
                        })
                    })
                    put("defaults", JSONObject())
                    put("queue", org.json.JSONArray())
                    put("history", org.json.JSONArray())
                    put("nowPlaying", JSONObject())
                })
            })
    }

    private class FakeJamPlayerBridge : JamPlayerBridge {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override var jamInterceptor: JamPlaybackInterceptor? = null

        override fun getPlayerCurrentPosition(): Long = 0L
        override fun getPlayerPlayWhenReady(): Boolean = false
        override fun getPlayerPlaybackState(): Int = Player.STATE_IDLE
        override fun getPlaybackSpeed(): Float = 1.0f
        override fun getPlayerDuration(): Long = 180_000L
        override fun prefetchTrack(song: Song) {}

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

    private class FakeJamPreferences : JamPreferencesBridge {
        var savedCode: String? = null
        override fun saveLastJam(code: String, avatarUrl: String?) { savedCode = code }
        override fun getLastAvatarUrl(): String? = null
        override fun clearLastJam() { savedCode = null }
        override fun rejoinCandidate(): String? = savedCode
    }

    private fun makeSnapshot(
        code: String = "ABCD",
        version: Long = 1L,
        members: List<JamMember>
    ): RoomSnapshot {
        return RoomSnapshot(
            code = code,
            version = version,
            serverNow = 1000L,
            hostUserId = "host_1",
            createdAt = 1000L,
            members = members,
            defaults = JamPermissions(playback = false, queueAdd = true, queueManage = false),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying()
        )
    }

    @Test
    fun testClosedToastFor_policy() {
        assertNull("Kick is handled by JamUiEvent.Kicked, so closedToastFor returns null", closedToastFor("kicked", false))
        assertNull("Kick returns null even if alreadyToasted is true", closedToastFor("kicked", true))

        assertEquals("The host ended this Jam", closedToastFor("ended", false))
        assertNull("Ended returns null if already toasted", closedToastFor("ended", true))

        assertEquals("This Jam has expired", closedToastFor("expired", false))
        assertNull("Expired returns null if already toasted", closedToastFor("expired", true))

        assertNull("Left returns null (voluntary leave has no extra toast)", closedToastFor("left", false))
        assertNull("Unknown returns null", closedToastFor("unknown_xyz", false))
    }

    @Test
    fun testSnapshotWithoutMeAfterSnapshotWithMe_emitsZeroToastEvents() = runTest {
        val myUserId = "guest_1"
        val store = JamStateStore(myUserId = myUserId)

        val member = JamMember(userId = myUserId, username = "Guest 1", perms = JamPermissions(playback = false, queueAdd = true))
        val snapshot1 = makeSnapshot(version = 1L, members = listOf(JamMember("host_1", "Host"), member))

        val applied1 = store.applySnapshot(snapshot1, isEpoch = true)
        assertTrue(applied1)

        val uiEvents = mutableListOf<JamUiEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) {
            store.uiEvents.collect { uiEvents.add(it) }
        }

        // Snapshot 2: guest has been removed from members (e.g. kicked)
        val snapshot2 = makeSnapshot(version = 2L, members = listOf(JamMember("host_1", "Host")))
        val applied2 = store.applySnapshot(snapshot2, isEpoch = false)
        assertTrue(applied2)

        // Must emit ZERO permission-diff toast events
        assertEquals(0, uiEvents.size)
    }

    @Test
    fun testNormalPermChangeForMe_toastsOnce() = runTest {
        val myUserId = "guest_1"
        val store = JamStateStore(myUserId = myUserId)

        val memberInitial = JamMember(userId = myUserId, username = "Guest 1", perms = JamPermissions(playback = false, queueAdd = true))
        store.applySnapshot(makeSnapshot(version = 1L, members = listOf(JamMember("host_1", "Host"), memberInitial)), isEpoch = true)

        val uiEvents = mutableListOf<JamUiEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) {
            store.uiEvents.collect { uiEvents.add(it) }
        }

        val memberUpdated = JamMember(userId = myUserId, username = "Guest 1", perms = JamPermissions(playback = true, queueAdd = true))
        store.applySnapshot(makeSnapshot(version = 2L, members = listOf(JamMember("host_1", "Host"), memberUpdated)), isEpoch = false)

        assertEquals(1, uiEvents.size)
        val event = uiEvents[0] as JamUiEvent.Toast
        assertEquals("You can now control playback", event.message)
    }

    @Test
    fun testKickSequence_emitsExactlyOneToast() = runTest {
        val myUserId = "guest_1"
        val stateStore = JamStateStore(myUserId = myUserId)
        val clockSync = ClockSync()
        val transport = FakeJamTransport()
        val client = JamClient(
            transport = transport,
            clockSync = clockSync,
            stateStore = stateStore,
            tokenProvider = { "fake-token" },
            monotonicNowMs = { System.currentTimeMillis() },
            autoClockSync = false,
            ioDispatcher = Dispatchers.Unconfined,
            scope = backgroundScope
        )

        val toasts = mutableListOf<String>()
        val sessionManager = JamSessionManager(
            playerManager = FakeJamPlayerBridge(),
            jamPreferences = FakeJamPreferences(),
            coroutineScope = backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            toastEmitter = { toasts.add(it) }
        )

        // Mock join
        sessionManager.joinJam("ABCD", myUserId, "Guest 1")
        runCurrent()

        val uiEvents = mutableListOf<JamUiEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) {
            stateStore.uiEvents.collect { uiEvents.add(it) }
        }

        // 1. Server emits kicked event to room
        transport.emitEvent(JamTransport.Event.JamEvent(Jam2Event("kicked", myUserId, "Guest 1")))
        runCurrent()

        // 2. Server closes client with kicked reason
        transport.emitEvent(JamTransport.Event.Closed("kicked"))
        runCurrent()

        // 3. Snapshot without guest arrives
        stateStore.applySnapshot(makeSnapshot(version = 2L, members = listOf(JamMember("host_1", "Host"))))
        runCurrent()

        // uiEvents has exactly 1 event: JamUiEvent.Kicked
        val kickEvents = uiEvents.filterIsInstance<JamUiEvent.Kicked>()
        assertEquals(1, kickEvents.size)
        assertEquals("You were removed from the Jam by the host", kickEvents[0].reason)

        // No permission toasts in uiEvents
        val permToasts = uiEvents.filterIsInstance<JamUiEvent.Toast>()
        assertEquals(0, permToasts.size)

        // Closed handler did NOT toast
        assertEquals(0, toasts.size)

        // Total user-visible toasts across the kick sequence is exactly 1
        assertEquals(1, kickEvents.size + toasts.size)
    }

    @Test
    fun testHostEndedSequence_emitsExactlyOneToast() = runTest {
        val myUserId = "guest_1"
        val stateStore = JamStateStore(myUserId = myUserId)
        val clockSync = ClockSync()
        val transport = FakeJamTransport()
        val client = JamClient(
            transport = transport,
            clockSync = clockSync,
            stateStore = stateStore,
            tokenProvider = { "fake-token" },
            monotonicNowMs = { System.currentTimeMillis() },
            autoClockSync = false,
            ioDispatcher = Dispatchers.Unconfined,
            scope = backgroundScope
        )

        val toasts = mutableListOf<String>()
        val sessionManager = JamSessionManager(
            playerManager = FakeJamPlayerBridge(),
            jamPreferences = FakeJamPreferences(),
            coroutineScope = backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            toastEmitter = { toasts.add(it) }
        )

        sessionManager.joinJam("ABCD", myUserId, "Guest 1")
        runCurrent()

        val uiEvents = mutableListOf<JamUiEvent>()
        backgroundScope.launch(Dispatchers.Unconfined) {
            stateStore.uiEvents.collect { uiEvents.add(it) }
        }

        // Server sends Closed event with reason "ended"
        transport.emitEvent(JamTransport.Event.Closed("ended"))
        runCurrent()

        // Closed handler emits exactly 1 toast: "The host ended this Jam"
        assertEquals(1, toasts.size)
        assertEquals("The host ended this Jam", toasts[0])

        // No uiEvents toast
        assertEquals(0, uiEvents.size)
    }
}
