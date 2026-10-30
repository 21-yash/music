package com.synq.features.music.jam

import androidx.media3.common.Player
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.PlaybackState
import com.synq.features.music.sleeptimer.SleepTimerManager
import com.synq.features.music.sleeptimer.SleepTimerPlayerBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JamEngineFixesTest {

    // ─────────────────────────────────────────────────────────────
    // Fakes
    // ─────────────────────────────────────────────────────────────

    private class FakeJamTransport : JamTransport {
        private val _events = MutableSharedFlow<JamTransport.Event>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()
        override var isConnected: Boolean = true

        val sentCommands = mutableListOf<Pair<String, Any?>>()
        var nextEmitResult: Result<JSONObject> = Result.success(JSONObject().apply { put("ok", true) })

        fun emitEvent(event: JamTransport.Event) {
            _events.tryEmit(event)
        }

        override fun connect(token: String?) {
            isConnected = true
            emitEvent(JamTransport.Event.Connected)
        }

        override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> {
            return if (isConnected) Result.success(Unit) else Result.failure(IllegalStateException("Socket is not connected"))
        }

        override fun disconnect() {
            isConnected = false
        }

        override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
            sentCommands.add(event to data)
            return nextEmitResult
        }
    }

    private class FakeJamPlayerBridge : JamPlayerBridge, SleepTimerPlayerBridge {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override var jamInterceptor: JamPlaybackInterceptor? = null
        override var onTrackEndedListener: (() -> Unit)? = null

        var currentPosition: Long = 0L
        var playWhenReady: Boolean = false
        var playbackStateInt: Int = Player.STATE_IDLE
        var speed: Float = 1.0f
        var duration: Long = 180_000L

        val playJamSyncSongCalls = mutableListOf<PlayJamSyncCall>()
        var userPauseCallCount = 0
        var applyJamPauseCallCount = 0
        var stopCallCount = 0
        var playCallCount = 0
        var seekCallCount = 0
        var lastSeekPosition: Long? = null
        var lastSetSpeed: Float? = null
        var stashedQueueCalled = false
        var restoredQueueCalled = false

        var currentJamQueue: List<Song> = emptyList()
        var currentJamQueueQids: List<String> = emptyList()
        var currentJamRecommendedQids: Set<String> = emptySet()
        var currentIndex: Int = 0

        data class PlayJamSyncCall(
            val song: Song,
            val initialPositionMs: Long,
            val playWhenReady: Boolean,
            val targetQid: String?
        )

        override fun getPlayerCurrentPosition(): Long = currentPosition
        override fun getPlayerPlayWhenReady(): Boolean = playWhenReady
        override fun getPlayerPlaybackState(): Int = playbackStateInt
        override fun getPlaybackSpeed(): Float = speed
        override fun getPlayerDuration(): Long = duration

        override fun playJamSyncSong(
            song: Song,
            initialPositionMs: Long,
            playWhenReady: Boolean,
            targetQid: String?
        ) {
            playJamSyncSongCalls.add(PlayJamSyncCall(song, initialPositionMs, playWhenReady, targetQid))
            this.playWhenReady = playWhenReady
            this.currentPosition = initialPositionMs
            if (targetQid != null && currentJamQueueQids.isNotEmpty()) {
                val idx = currentJamQueueQids.indexOf(targetQid)
                if (idx >= 0) currentIndex = idx
            }
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = playWhenReady,
                    playbackPositionMs = initialPositionMs
                )
            }
        }

        override fun prefetchTrack(song: Song) {}

        override fun stop() {
            stopCallCount++
            playWhenReady = false
        }

        override fun pause() {
            userPauseCallCount++
            playWhenReady = false
        }

        override fun applyJamPlay() {
            playCallCount++
            playWhenReady = true
            _playbackState.update { it.copy(isPlaying = true) }
        }

        override fun applyJamPause() {
            applyJamPauseCallCount++
            playWhenReady = false
            _playbackState.update { it.copy(isPlaying = false) }
        }

        override fun applyJamSeek(positionMs: Long) {
            seekCallCount++
            lastSeekPosition = positionMs
            currentPosition = positionMs
            _playbackState.update { it.copy(playbackPositionMs = positionMs) }
        }

        override fun applyJamSpeed(factor: Float) {
            lastSetSpeed = factor
            speed = factor
        }

        override fun setJamQueue(
            jamQueue: List<Song>,
            targetSong: Song?,
            targetQid: String?,
            queueQids: List<String>,
            recommendedQids: Set<String>
        ) {
            currentJamQueue = jamQueue
            currentJamQueueQids = queueQids
            currentJamRecommendedQids = recommendedQids
            if (targetQid != null && queueQids.isNotEmpty()) {
                val idx = queueQids.indexOf(targetQid)
                if (idx >= 0) currentIndex = idx
            } else if (targetSong != null) {
                val idx = jamQueue.indexOfFirst { it.id == targetSong.id }
                if (idx >= 0) currentIndex = idx
            }
        }

        override fun stashPersonalQueue() {
            stashedQueueCalled = true
        }

        override fun restorePersonalQueue() {
            restoredQueueCalled = true
        }

        override fun setPlaybackSpeed(speed: Float) {
            this.speed = speed
        }

        override fun invalidateStreamCache(songId: String) {}
    }

    private class FakeJamPreferences : JamPreferencesBridge {
        var savedCode: String? = null
        override fun saveLastJam(code: String) { savedCode = code }
        override fun clearLastJam() { savedCode = null }
        override fun rejoinCandidate(): String? = savedCode
    }

    private fun sampleSong(id: String, durationSec: Int = 180): Song {
        return Song(
            id = id,
            title = "Song $id",
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

    private fun makeSnapshot(
        code: String = "ABCD",
        trackSeq: Long = 1L,
        song: Song = sampleSong("s1"),
        status: JamPlaybackStatus = JamPlaybackStatus.PLAYING,
        hostUserId: String = "host_1"
    ): RoomSnapshot {
        return RoomSnapshot(
            code = code,
            version = 1L,
            serverNow = 10_000L,
            hostUserId = hostUserId,
            createdAt = 1000L,
            members = listOf(
                JamMember(hostUserId, "Host", null, 1000L, true, 1000L, JamPermissions(playback = true, queueAdd = true, queueManage = true))
            ),
            defaults = JamPermissions(playback = true, queueAdd = true, queueManage = true),
            queue = emptyList(),
            history = emptyList(),
            nowPlaying = JamNowPlaying(
                item = JamQueueItem("q_$trackSeq", song),
                status = status,
                positionMs = 5000L,
                anchorServerTs = 10_000L,
                trackSeq = trackSeq
            )
        )
    }

    private class TestFixture(
        val sessionManager: JamSessionManager,
        val fakePlayer: FakeJamPlayerBridge,
        val transport: FakeJamTransport,
        val stateStore: JamStateStore,
        val clockSync: ClockSync,
        val toasts: MutableList<String>,
        var currentTime: Long,
        val testScope: TestScope
    ) {
        fun joinTestJam(code: String = "ABCD", initialSnapshot: RoomSnapshot? = null) {
            val snapshot = initialSnapshot ?: RoomSnapshot(
                code = code,
                version = 1L,
                serverNow = 1000L,
                hostUserId = "host_1",
                createdAt = 1000L,
                members = emptyList(),
                defaults = JamPermissions(playback = true, queueAdd = true, queueManage = true),
                queue = emptyList(),
                history = emptyList(),
                nowPlaying = JamNowPlaying()
            )

            val roomJson = JSONObject().apply {
                put("ok", true)
                put("room", JSONObject().apply {
                    put("code", code)
                    put("version", snapshot.version)
                    put("serverNow", snapshot.serverNow)
                    put("hostUserId", snapshot.hostUserId)
                    put("createdAt", snapshot.createdAt)
                    put("members", JSONArray())
                    put("defaults", JSONObject().apply {
                        put("playback", true)
                        put("queueAdd", true)
                        put("queueManage", true)
                    })
                    put("queue", JSONArray())
                    put("history", JSONArray())
                    put("nowPlaying", JSONObject().apply {
                        put("status", snapshot.nowPlaying.status.name.lowercase())
                        put("positionMs", snapshot.nowPlaying.positionMs)
                        put("anchorServerTs", snapshot.nowPlaying.anchorServerTs)
                        put("trackSeq", snapshot.nowPlaying.trackSeq)
                    })
                })
            }
            transport.nextEmitResult = Result.success(roomJson)

            sessionManager.joinJam(code, "user_me", "Me")
            testScope.runCurrent()

            fakePlayer.jamInterceptor = sessionManager
            stateStore.applySnapshot(snapshot, isEpoch = true)
            testScope.runCurrent()
        }
    }

    private fun createFixture(testScope: TestScope): TestFixture {
        val fakePlayer = FakeJamPlayerBridge()
        val transport = FakeJamTransport()
        val stateStore = JamStateStore(myUserId = "user_me")
        var currentTime = 1000L
        val clockSync = ClockSync(
            monotonicNowMs = { currentTime },
            wallClockNowMs = { currentTime }
        )
        val toasts = mutableListOf<String>()

        val client = JamClient(
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            scope = testScope.backgroundScope,
            ioDispatcher = StandardTestDispatcher(scheduler = testScope.testScheduler),
            tokenProvider = { "valid_token" },
            autoClockSync = false,
            monotonicNowMs = { currentTime },
            wallClockNowMs = { currentTime }
        )

        val sessionManager = JamSessionManager(
            playerManager = fakePlayer,
            jamPreferences = FakeJamPreferences(),
            coroutineScope = testScope.backgroundScope,
            transportOverride = transport,
            jamClientOverride = client,
            clockSyncOverride = clockSync,
            stateStoreOverride = stateStore,
            currentTimeProvider = { currentTime },
            toastEmitter = { msg -> toasts.add(msg) }
        )

        return TestFixture(
            sessionManager = sessionManager,
            fakePlayer = fakePlayer,
            transport = transport,
            stateStore = stateStore,
            clockSync = clockSync,
            toasts = toasts,
            currentTime = currentTime,
            testScope = testScope
        )
    }

    // ─────────────────────────────────────────────────────────────
    // F1.a: stepOutLocally(), snapshot arrives -> isLocalStepOut stays true; resume -> false
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testA_stepOutLocally_snapshotArrives_staysTrue_resumeBecomesFalse`() = runTest {
        val fixture = createFixture(this)
        fixture.joinTestJam("ABCD")

        assertFalse(fixture.sessionManager.isLocalStepOut)
        assertFalse(fixture.sessionManager.jamState.value.isLocalStepOut)

        // Step out locally
        fixture.sessionManager.stepOutLocally()
        runCurrent()
        assertTrue(fixture.sessionManager.isLocalStepOut)
        assertTrue(fixture.sessionManager.jamState.value.isLocalStepOut)

        // A room snapshot arrives
        val newSnapshot = makeSnapshot("ABCD", trackSeq = 5L)
        fixture.stateStore.applySnapshot(newSnapshot, isEpoch = true)
        runCurrent()

        // isLocalStepOut must STAY true!
        assertTrue(fixture.sessionManager.isLocalStepOut)
        assertTrue(fixture.sessionManager.jamState.value.isLocalStepOut)

        // Resume from step-out
        fixture.sessionManager.resumeFromLocalStepOut()
        runCurrent()

        assertFalse(fixture.sessionManager.isLocalStepOut)
        assertFalse(fixture.sessionManager.jamState.value.isLocalStepOut)
    }

    // ─────────────────────────────────────────────────────────────
    // F1.b: Focus loss and Noisy step out with 0 commands; USER_REQUEST must NOT step out
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testB_audioFocusLoss_andNoisy_stepsOutLocally_withoutJamCommands_andUserRequestDoesNot`() = runTest {
        val fixture = createFixture(this)
        fixture.joinTestJam("ABCD")

        val transportCommandCountBefore = fixture.transport.sentCommands.size

        // 1. Focus loss occurs
        fixture.fakePlayer.jamInterceptor?.onPlayWhenReadyChanged(
            playWhenReady = false,
            reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
        )
        runCurrent()

        // Zero jam commands sent
        assertEquals(transportCommandCountBefore, fixture.transport.sentCommands.size)
        // localPlaybackState == LocalStepOut
        assertTrue(fixture.sessionManager.localPlaybackState.value is LocalPlaybackState.LocalStepOut)
        assertTrue(fixture.sessionManager.isLocalStepOut)
        // Focus loss toast emitted
        assertTrue(fixture.toasts.contains("Paused for you only — tap Resume to rejoin"))

        // Reconciler makes no player calls while in step-out
        val seekCalls = fixture.fakePlayer.seekCallCount
        val playCalls = fixture.fakePlayer.playCallCount
        fixture.stateStore.applySnapshot(makeSnapshot("ABCD", trackSeq = 10L), isEpoch = true)
        runCurrent()
        assertEquals(seekCalls, fixture.fakePlayer.seekCallCount)
        assertEquals(playCalls, fixture.fakePlayer.playCallCount)

        // Resume
        fixture.sessionManager.resumeFromLocalStepOut()
        runCurrent()
        assertFalse(fixture.sessionManager.isLocalStepOut)

        // 2. Noisy event occurs
        fixture.fakePlayer.jamInterceptor?.onPlayWhenReadyChanged(
            playWhenReady = false,
            reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
        )
        runCurrent()
        assertEquals(transportCommandCountBefore, fixture.transport.sentCommands.size)
        assertTrue(fixture.sessionManager.localPlaybackState.value is LocalPlaybackState.LocalStepOut)

        // Resume again
        fixture.sessionManager.resumeFromLocalStepOut()
        runCurrent()
        assertFalse(fixture.sessionManager.isLocalStepOut)

        // 3. USER_REQUEST (e.g. from internal applyJamPause) must NOT step out!
        fixture.fakePlayer.jamInterceptor?.onPlayWhenReadyChanged(
            playWhenReady = false,
            reason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        )
        runCurrent()
        assertFalse("USER_REQUEST must not step out", fixture.sessionManager.isLocalStepOut)
        assertEquals(LocalPlaybackState.Synchronized, fixture.sessionManager.localPlaybackState.value)
    }

    // ─────────────────────────────────────────────────────────────
    // F1.c: SleepTimerManager in Jam steps out locally; not in Jam pauses player
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testC_sleepTimerManager_inJam_stepsOutLocally_notInJam_pausesPlayer`() = runTest {
        val fixture = createFixture(this)
        fixture.joinTestJam("ABCD")

        val sleepTimer = SleepTimerManager(
            playerManager = fixture.fakePlayer,
            jamSessionManagerProvider = { fixture.sessionManager },
            coroutineScope = backgroundScope
        )

        // 1. In jam: "End of track" finish calls stepOutLocally, never playerManager.pause()
        sleepTimer.setTimer("End of track")
        fixture.fakePlayer.onTrackEndedListener?.invoke()
        runCurrent()

        assertTrue(fixture.sessionManager.isLocalStepOut)
        assertEquals(0, fixture.fakePlayer.userPauseCallCount)
        assertTrue(fixture.fakePlayer.applyJamPauseCallCount > 0)

        // Resume from step-out
        fixture.sessionManager.resumeFromLocalStepOut()
        runCurrent()
        assertFalse(fixture.sessionManager.isLocalStepOut)

        // 2. In jam: countdown finish calls stepOutLocally, never pause()
        sleepTimer.setTimer("15 mins")
        advanceTimeBy(15 * 60 * 1000L + 1000L)
        runCurrent()

        assertTrue(fixture.sessionManager.isLocalStepOut)
        assertEquals(0, fixture.fakePlayer.userPauseCallCount)

        // 3. Not in a jam: leave jam -> timer finish calls playerManager.pause()
        fixture.sessionManager.leaveJam()
        runCurrent()
        assertFalse(fixture.sessionManager.jamState.value.isInJam)

        sleepTimer.setTimer("End of track")
        fixture.fakePlayer.onTrackEndedListener?.invoke()
        runCurrent()

        assertEquals(1, fixture.fakePlayer.userPauseCallCount)
    }

    // ─────────────────────────────────────────────────────────────
    // F1.d: resumeFromLocalStepOut with same vs newer trackSeq
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testD_resumeFromLocalStepOut_sameTrackSeq_seeksAndPlays_newerTrackSeq_resetsAndChangesTrack`() = runTest {
        val fixture = createFixture(this)
        val song1 = sampleSong("s1")
        val song2 = sampleSong("s2")

        fixture.joinTestJam("ABCD", initialSnapshot = makeSnapshot("ABCD", trackSeq = 10L, song = song1))
        fixture.sessionManager.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        // Step out locally on track 10
        fixture.sessionManager.stepOutLocally()
        runCurrent()
        assertTrue(fixture.sessionManager.isLocalStepOut)

        val seekCountBefore = fixture.fakePlayer.seekCallCount
        val playCountBefore = fixture.fakePlayer.playCallCount

        // Resume with SAME trackSeq (10L)
        fixture.sessionManager.resumeFromLocalStepOut()
        runCurrent()

        // Seek and play should be called for current track
        assertTrue(fixture.fakePlayer.seekCallCount > seekCountBefore)
        assertTrue(fixture.fakePlayer.playCallCount > playCountBefore)

        // Now test with NEWER trackSeq:
        fixture.sessionManager.stepOutLocally()
        runCurrent()
        assertTrue(fixture.sessionManager.isLocalStepOut)

        val seekCountBeforeNewer = fixture.fakePlayer.seekCallCount
        val playCountBeforeNewer = fixture.fakePlayer.playCallCount
        val playSyncCallsBefore = fixture.fakePlayer.playJamSyncSongCalls.size

        // Server moves to track 11 while stepped out
        val newerSnapshot = makeSnapshot("ABCD", trackSeq = 11L, song = song2)
        fixture.stateStore.applySnapshot(newerSnapshot, isEpoch = true)
        runCurrent()

        // Resume from step-out
        fixture.sessionManager.resumeFromLocalStepOut()
        runCurrent()

        // Must NOT seek or play on old track (track 10)
        assertEquals(seekCountBeforeNewer, fixture.fakePlayer.seekCallCount)
        assertEquals(playCountBeforeNewer, fixture.fakePlayer.playCallCount)

        // Instead, reconciler issues ChangeTrack to the new song (track 11)
        assertTrue(fixture.fakePlayer.playJamSyncSongCalls.size > playSyncCallsBefore)
        val lastSyncCall = fixture.fakePlayer.playJamSyncSongCalls.last()
        assertEquals("s2", lastSyncCall.song.id)
    }

    // ─────────────────────────────────────────────────────────────
    // F1.f & F4: Loading timeout retries at 15s and 30s, aborts at 45s with single toast
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testF_checkLoadingTimeout_retriesAt15sAnd30s_thenAbortsAndToastsOnce`() = runTest {
        val fixture = createFixture(this)
        val song = sampleSong("slow_song")
        val snapshot = makeSnapshot("ABCD", trackSeq = 20L, song = song, status = JamPlaybackStatus.PLAYING)
        fixture.joinTestJam("ABCD", initialSnapshot = snapshot)

        // Track is LOADING at clock = 1000L
        fixture.currentTime = 1000L
        fixture.sessionManager.syncJamQueue(snapshot, force = true)
        runCurrent()

        // Clear any initial play sync calls
        fixture.fakePlayer.playJamSyncSongCalls.clear()

        // After 14s (clock = 15000L): no retry yet
        fixture.currentTime = 15_000L
        fixture.sessionManager.checkLoadingTimeout(fixture.currentTime)
        runCurrent()
        assertEquals(0, fixture.fakePlayer.playJamSyncSongCalls.size)

        // After 16s (>15s elapsed, clock = 17000L): Retry #1
        fixture.currentTime = 17_000L
        fixture.sessionManager.checkLoadingTimeout(fixture.currentTime)
        runCurrent()
        assertEquals(1, fixture.fakePlayer.playJamSyncSongCalls.size)

        // After another 16s (clock = 33000L): Retry #2
        fixture.currentTime = 33_000L
        fixture.sessionManager.checkLoadingTimeout(fixture.currentTime)
        runCurrent()
        assertEquals(2, fixture.fakePlayer.playJamSyncSongCalls.size)

        // After another 16s (clock = 49000L): Max retries reached -> Abort!
        val pauseCallsBefore = fixture.fakePlayer.applyJamPauseCallCount
        fixture.currentTime = 49_000L
        fixture.sessionManager.checkLoadingTimeout(fixture.currentTime)
        runCurrent()

        // Player paused
        assertTrue(fixture.fakePlayer.applyJamPauseCallCount > pauseCallsBefore)
        // Toast "Couldn't load this song" emitted once
        assertEquals(1, fixture.toasts.count { it == "Couldn't load this song" })

        // Subsequent checkLoadingTimeout calls do not spam toasts
        fixture.currentTime = 65_000L
        fixture.sessionManager.checkLoadingTimeout(fixture.currentTime)
        runCurrent()
        assertEquals(1, fixture.toasts.count { it == "Couldn't load this song" })
    }

    // ─────────────────────────────────────────────────────────────
    // F2: Duplicate songs in jam queue with different QIDs: recommended & index by QID
    // ─────────────────────────────────────────────────────────────
    @Test
    fun `testF2_duplicateSongInJamQueue_differentiatesRecommendedAndIndexByQid`() {
        val fakePlayer = FakeJamPlayerBridge()
        val dupSong = sampleSong("same_song_id")

        val item1 = JamQueueItem(qid = "qid_user", song = dupSong, recommended = false)
        val item2 = JamQueueItem(qid = "qid_rec", song = dupSong, recommended = true)

        val fullQueue = listOf(item1.song, item2.song)
        val queueQids = listOf(item1.qid, item2.qid)
        val recQids = setOf(item2.qid)

        // In jam mode, set jam queue with duplicate song id
        fakePlayer.setJamQueue(
            jamQueue = fullQueue,
            targetSong = dupSong,
            targetQid = "qid_rec",
            queueQids = queueQids,
            recommendedQids = recQids
        )

        // The current index MUST be 1 (selected by targetQid = qid_rec), NOT 0
        assertEquals(1, fakePlayer.currentIndex)

        // Recommended check MUST be by QID, not song.id
        assertFalse(fakePlayer.currentJamRecommendedQids.contains("qid_user"))
        assertTrue(fakePlayer.currentJamRecommendedQids.contains("qid_rec"))

        // Now select the user item (index 0)
        fakePlayer.playJamSyncSong(
            song = dupSong,
            initialPositionMs = 0L,
            playWhenReady = true,
            targetQid = "qid_user"
        )
        assertEquals(0, fakePlayer.currentIndex)
    }
}
