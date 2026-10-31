package com.synq.features.music.jam

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.synq.core.network.NetworkMonitor
import com.synq.data.repository.AuthRepository
import com.synq.features.music.domain.model.Artist
import com.synq.features.music.domain.model.Song
import com.synq.features.music.playback.JamPlaybackInterceptor
import com.synq.features.music.playback.MusicPlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

sealed class LocalPlaybackState {
    object Synchronized : LocalPlaybackState()
    object CatchingUp : LocalPlaybackState()
    object LocalStepOut : LocalPlaybackState()
}

@Singleton
class JamSessionManager(
    private val playerManager: JamPlayerBridge,
    private val authRepository: AuthRepository? = null,
    private val networkMonitorProvider: Provider<NetworkMonitor>? = null,
    private val jamPreferences: JamPreferencesBridge,
    private val context: Context? = null,
    coroutineScope: CoroutineScope? = null,
    transportOverride: JamTransport? = null,
    jamClientOverride: JamClient? = null,
    clockSyncOverride: ClockSync? = null,
    stateStoreOverride: JamStateStore? = null,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val toastEmitter: (String) -> Unit = { msg ->
        try {
            Handler(Looper.getMainLooper()).post {
                context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
            }
        } catch (_: Throwable) {}
    }
) : JamPlaybackInterceptor {

    @Inject
    constructor(
        playerManager: MusicPlayerManager,
        authRepository: AuthRepository,
        networkMonitorProvider: Provider<NetworkMonitor>,
        jamPreferences: JamPreferences,
        @ApplicationContext context: Context
    ) : this(
        playerManager as JamPlayerBridge,
        authRepository,
        networkMonitorProvider,
        jamPreferences as JamPreferencesBridge,
        context
    )

    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var myUserId: String = ""
    private var myAvatarUrl: String? = null

    val clockSync: ClockSync = clockSyncOverride ?: ClockSync()
    val stateStore: JamStateStore = stateStoreOverride ?: JamStateStore(myUserId)
    val transport: JamTransport = transportOverride ?: SocketIoTransport(
        baseUrl = com.synq.BuildConfig.API_BASE_URL,
        apiKey = com.synq.BuildConfig.API_KEY
    )
    val jamClient: JamClient = jamClientOverride ?: JamClient(
        transport = transport,
        clockSync = clockSync,
        stateStore = stateStore,
        tokenProvider = { authRepository?.getFreshAccessToken() },
        scope = scope
    )

    private val _jamState = MutableStateFlow(JamState())
    val jamState: StateFlow<JamState> = _jamState.asStateFlow()

    private var currentTrackSeq: Long = 0L
    private var reconcilerTrackState: ReconcilerTrackState = ReconcilerTrackState.READY
    private var loadingTrackSeq: Long? = null
    private var loadingStartedAt: Long = 0L
    private var trackRetryCount: Int = 0
    private var retryTrackSeq: Long = 0L
    internal var unplayableTrackSeq: Long? = null
    private var lastReportedEndedTrackSeq: Long = 0L
    private var lastReportedEndedAttemptTs: Long = 0L
    private var isReportingEndedTrackSeq: Long? = null
    private var hasShownAudioFocusToastInThisJam: Boolean = false
    private var lastLoadingAbortedToastTrackSeq: Long? = null
    private val _localPlaybackState = MutableStateFlow<LocalPlaybackState>(LocalPlaybackState.Synchronized)
    val localPlaybackState: StateFlow<LocalPlaybackState> = _localPlaybackState.asStateFlow()
    val isLocalStepOut: Boolean get() = _localPlaybackState.value is LocalPlaybackState.LocalStepOut
    private var reconcilerJob: Job? = null
    private var isReconciling = false
    private var lastAppliedSnapshotVersion: Long? = null
    private var lastAppliedNowPlayingQid: String? = null
    private var isTornDown: Boolean = true
    private var isSessionOpInFlight: Boolean = false
    private var isAddPlaylistInFlight: Boolean = false

    companion object {
        const val MAX_JAM_QUEUE_SIZE = 50
    }

    init {
        // Observe local playback state and keep JamState.isLocalStepOut in sync
        scope.launch {
            _localPlaybackState.collect { state ->
                val steppedOut = state is LocalPlaybackState.LocalStepOut
                _jamState.update { it.copy(isLocalStepOut = steppedOut) }
            }
        }

        // Observe transport events (server notifications)
        scope.launch {
            transport.events.collect { event ->
                handleTransportEvent(event)
            }
        }

        // Observe JamClient state transitions
        scope.launch {
            jamClient.clientState.collect { state ->
                handleClientStateChange(state)
            }
        }

        // Observe authoritative room snapshot and sync legacy JamState
        scope.launch {
            stateStore.room.collect { room ->
                updateLegacyState(room, jamClient.clientState.value)
            }
        }

        // Start reconciliation loop
        startReconcilerLoop()
    }

    private fun handleTransportEvent(event: JamTransport.Event) {
        when (event) {
            is JamTransport.Event.JamEvent -> {
                when (event.event.kind) {
                    "kicked" -> {
                        if (event.event.userId == myUserId) {
                            teardownJam()
                        }
                    }
                    else -> {}
                }
            }

            is JamTransport.Event.Closed -> {
                teardownJam()
            }

            else -> {}
        }
    }

    private fun handleClientStateChange(state: JamClientState) {
        when (state) {
            is JamClientState.Active -> {
                jamPreferences.saveLastJam(state.code, myAvatarUrl)
                playerManager.jamInterceptor = this
                updateLegacyState(stateStore.room.value, state)
            }

            is JamClientState.Closed -> {
                jamPreferences.clearLastJam()
                showToast(state.message)
                teardownJam()
                jamClient.resetToDisconnected()
            }

            is JamClientState.Reconnecting -> {
                updateLegacyState(stateStore.room.value, state)
            }

            is JamClientState.Disconnected -> {
                if (_jamState.value.isInJam && jamClient.clientState.value !is JamClientState.Reconnecting) {
                    teardownJam()
                }
            }

            else -> {
                updateLegacyState(stateStore.room.value, state)
            }
        }
    }

    private fun updateLegacyState(snapshot: RoomSnapshot?, clientState: JamClientState) {
        val isInJam = clientState is JamClientState.Active ||
                clientState is JamClientState.Reconnecting ||
                clientState is JamClientState.Joining

        if (!isInJam || snapshot == null) {
            val code = (clientState as? JamClientState.Joining)?.code
                ?: (clientState as? JamClientState.Active)?.code
                ?: (clientState as? JamClientState.Reconnecting)?.code

            _jamState.update { prev ->
                prev.copy(
                    isInJam = isInJam,
                    sessionCode = code ?: prev.sessionCode,
                    participants = if (isInJam) prev.participants else emptyList(),
                    queue = if (isInJam) prev.queue else emptyList(),
                    isLocalStepOut = isLocalStepOut
                )
            }
            return
        }

        val isHost = snapshot.hostUserId == myUserId
        val hostMember = snapshot.members.firstOrNull { it.userId == snapshot.hostUserId }
        val participants = snapshot.members.map { m ->
            JamParticipantUi(
                socketId = m.userId,
                userId = m.userId,
                username = m.username,
                avatarUrl = m.avatarUrl,
                isHost = m.userId == snapshot.hostUserId
            )
        }
        val fullQueue = listOfNotNull(snapshot.nowPlaying.item) + snapshot.queue
        val queueSongs = fullQueue.map { it.song }

        val isUnplayable = unplayableTrackSeq != null && unplayableTrackSeq == snapshot.nowPlaying.trackSeq
        if (unplayableTrackSeq != null && !isUnplayable) {
            unplayableTrackSeq = null
            trackRetryCount = 0
        }

        _jamState.value = JamState(
            isInJam = true,
            sessionCode = snapshot.code,
            isHost = isHost,
            hostUserId = snapshot.hostUserId,
            hostName = hostMember?.username ?: "",
            allowAttendeeReorder = snapshot.defaults.queueManage,
            participants = participants,
            queue = queueSongs,
            isLocalStepOut = isLocalStepOut,
            isLocalPlaybackUnplayable = isUnplayable,
            error = null
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Reconciler Loop (Pure Decision Engine -> Player Mirror)
    // ─────────────────────────────────────────────────────────────

    private fun startReconcilerLoop() {
        reconcilerJob?.cancel()
        reconcilerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                reconcilePlayback()
                checkNearEndTick()
                checkLoadingTimeout()
            }
        }

        // Also trigger reconciliation whenever authoritative room updates
        scope.launch {
            stateStore.room.collect {
                reconcilePlayback()
            }
        }
    }

    private fun reconcilePlayback() {
        if (!_jamState.value.isInJam) return
        if (isLocalStepOut) return // Reconciler is suspended while guest is locally stepped out
        val currentRoom = stateStore.room.value ?: return
        if (isReconciling) return

        // Task F9 & G6: Suppress playback loop if this track sequence was marked unplayable
        val unplayable = unplayableTrackSeq
        if (unplayable != null) {
            if (currentRoom.nowPlaying.trackSeq == unplayable) {
                if (!_jamState.value.isLocalPlaybackUnplayable) {
                    _jamState.update { it.copy(isLocalPlaybackUnplayable = true) }
                }
                return
            } else {
                unplayableTrackSeq = null
                trackRetryCount = 0
                _jamState.update { it.copy(isLocalPlaybackUnplayable = false) }
            }
        } else if (_jamState.value.isLocalPlaybackUnplayable) {
            _jamState.update { it.copy(isLocalPlaybackUnplayable = false) }
        }

        try {
            isReconciling = true
            val playback = playerManager.playbackState.value
            val currentPos = playerManager.getPlayerCurrentPosition()
            val playWhenReady = playerManager.getPlayerPlayWhenReady()
            val playbackState = playerManager.getPlayerPlaybackState()
            val localSpeed = playerManager.getPlaybackSpeed()

            val localState = LocalPlayerState(
                currentSongId = playback.currentSong?.id,
                currentTrackSeq = currentTrackSeq,
                currentPositionMs = currentPos,
                playWhenReady = playWhenReady,
                playbackState = playbackState,
                playbackSpeed = localSpeed,
                trackState = reconcilerTrackState,
                loadingTrackSeq = loadingTrackSeq
            )

            val action = JamReconciler.decide(localState, currentRoom, clockSync)
            when (action) {
                is ReconcilerAction.ChangeTrack -> {
                    reconcilerTrackState = ReconcilerTrackState.LOADING
                    loadingTrackSeq = action.trackSeq
                    loadingStartedAt = currentTimeProvider()
                    syncJamQueue(currentRoom)

                    if (action.song != null) {
                        val targetQid = currentRoom.nowPlaying.item?.qid
                        playerManager.playJamSyncSong(
                            song = action.song,
                            initialPositionMs = 0L,
                            playWhenReady = false,
                            targetQid = targetQid
                        )
                        // Pre-cache upcoming track
                        val nextSong = currentRoom.queue.firstOrNull()?.song
                        if (nextSong != null) {
                            playerManager.prefetchTrack(nextSong)
                        }
                    } else {
                        reconcilerTrackState = ReconcilerTrackState.READY
                        loadingTrackSeq = null
                        currentTrackSeq = 0L
                        playerManager.stop()
                    }
                }

                is ReconcilerAction.SetPlayWhenReady -> {
                    if (action.playWhenReady) {
                        playerManager.applyJamPlay()
                    } else {
                        playerManager.applyJamPause()
                    }
                }

                is ReconcilerAction.Seek -> {
                    playerManager.applyJamSeek(action.positionMs)
                }

                is ReconcilerAction.SpeedRamp -> {
                    playerManager.applyJamSpeed(action.factor)
                    if (_localPlaybackState.value != LocalPlaybackState.LocalStepOut) {
                        _localPlaybackState.value = LocalPlaybackState.CatchingUp
                    }
                }

                is ReconcilerAction.Nothing -> {
                    syncJamQueue(currentRoom)
                    if (_localPlaybackState.value == LocalPlaybackState.CatchingUp) {
                        _localPlaybackState.value = LocalPlaybackState.Synchronized
                    }
                }
            }
        } finally {
            isReconciling = false
        }
    }

    fun getFullQueue(): List<JamQueueItem> {
        val room = stateStore.room.value ?: return emptyList()
        return listOfNotNull(room.nowPlaying.item) + room.queue
    }

    internal fun syncJamQueue(room: RoomSnapshot, force: Boolean = false) {
        val currentNowPlayingQid = room.nowPlaying.item?.qid
        val versionChanged = room.version != lastAppliedSnapshotVersion
        val nowPlayingChanged = currentNowPlayingQid != lastAppliedNowPlayingQid

        if (force || versionChanged || nowPlayingChanged) {
            lastAppliedSnapshotVersion = room.version
            lastAppliedNowPlayingQid = currentNowPlayingQid

            val fullQueue = listOfNotNull(room.nowPlaying.item) + room.queue
            val fullQueueSongs = fullQueue.map { it.song }
            val fullQueueQids = fullQueue.map { it.qid }
            val recQids = room.queue.filter { it.recommended }.map { it.qid }.toSet()
            val targetSong = room.nowPlaying.item?.song
            val targetQid = room.nowPlaying.item?.qid

            playerManager.setJamQueue(
                jamQueue = fullQueueSongs,
                targetSong = targetSong,
                targetQid = targetQid,
                queueQids = fullQueueQids,
                recommendedQids = recQids
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Public Session Controls
    // ─────────────────────────────────────────────────────────────

    fun createJam(
        userId: String,
        username: String,
        avatarUrl: String? = null,
        onComplete: ((Result<RoomSnapshot>) -> Unit)? = null
    ) {
        if (_jamState.value.isInJam || isSessionOpInFlight) {
            val err = IllegalStateException("Already in a Jam session or an operation is in progress")
            showToast("Already in a Jam session")
            onComplete?.invoke(Result.failure(err))
            return
        }
        isSessionOpInFlight = true
        isTornDown = false

        val playback = playerManager.playbackState.value
        val isCurrentlyPlaying = playback.isPlaying
        val currentLocalSong = playback.currentSong
        myUserId = userId
        myAvatarUrl = avatarUrl
        stateStore.setMyUserId(userId)
        playerManager.stashPersonalQueue()
        playerManager.jamInterceptor = this
        _jamState.update { it.copy(isInJam = true, error = null) }

        scope.launch {
            val res = jamClient.createRoom(avatarUrl)
            isSessionOpInFlight = false
            res.onSuccess { snapshot ->
                isTornDown = false
                if (isCurrentlyPlaying && currentLocalSong != null) {
                    addSongToJam(currentLocalSong)
                }
                onComplete?.invoke(Result.success(snapshot))
            }
            res.onFailure { err ->
                showToast("Failed to create Jam: ${err.message}")
                teardownJam()
                jamClient.resetToDisconnected()
                onComplete?.invoke(Result.failure(err))
            }
        }
    }

    fun joinJam(
        code: String,
        userId: String,
        username: String,
        avatarUrl: String? = null,
        onComplete: ((Result<RoomSnapshot>) -> Unit)? = null
    ) {
        if (_jamState.value.isInJam || isSessionOpInFlight) {
            val err = IllegalStateException("Already in a Jam session or an operation is in progress")
            showToast("Already in a Jam session")
            onComplete?.invoke(Result.failure(err))
            return
        }
        isSessionOpInFlight = true
        isTornDown = false

        myUserId = userId
        stateStore.setMyUserId(userId)
        val cleanCode = code.trim().uppercase()
        playerManager.stashPersonalQueue()
        playerManager.jamInterceptor = this
        _jamState.update { it.copy(isInJam = true, sessionCode = cleanCode, error = null) }

        scope.launch {
            val res = jamClient.joinRoom(cleanCode, avatarUrl)
            isSessionOpInFlight = false
            res.onSuccess { snapshot ->
                isTornDown = false
                onComplete?.invoke(Result.success(snapshot))
            }
            res.onFailure { err ->
                showToast("Failed to join Jam: ${err.message}")
                teardownJam()
                jamClient.resetToDisconnected()
                onComplete?.invoke(Result.failure(err))
            }
        }
    }

    fun leaveJam() {
        isTornDown = false
        teardownJam()
        jamClient.resetToDisconnected()
        scope.launch {
            jamClient.leaveRoom()
        }
    }

    fun endJam() {
        scope.launch {
            val res = jamClient.endRoom()
            res.onSuccess {
                teardownJam()
            }
            res.onFailure { err ->
                showToast(err.message ?: "Failed to end Jam")
            }
        }
    }

    private fun teardownJam() {
        if (isTornDown) return
        isTornDown = true
        isSessionOpInFlight = false
        jamPreferences.clearLastJam()
        playerManager.jamInterceptor = null
        playerManager.stop()
        playerManager.setPlaybackSpeed(1.0f)
        playerManager.restorePersonalQueue()
        _jamState.value = JamState()
        currentTrackSeq = 0L
        reconcilerTrackState = ReconcilerTrackState.READY
        loadingTrackSeq = null
        loadingStartedAt = 0L
        trackRetryCount = 0
        retryTrackSeq = 0L
        unplayableTrackSeq = null
        lastReportedEndedTrackSeq = 0L
        lastReportedEndedAttemptTs = 0L
        isReportingEndedTrackSeq = null
        _localPlaybackState.value = LocalPlaybackState.Synchronized
        lastAppliedSnapshotVersion = null
        lastAppliedNowPlayingQid = null
        hasShownAudioFocusToastInThisJam = false
        lastLoadingAbortedToastTrackSeq = null
    }

    fun onNetworkRestored() {
        if (!_jamState.value.isInJam) return
        jamClient.onNetworkRestored()
    }

    fun setError(message: String) {
        _jamState.update { it.copy(error = message) }
        showToast(message)
    }

    fun clearError() {
        _jamState.update { it.copy(error = null) }
    }

    // ─────────────────────────────────────────────────────────────
    // Transport & Playback API
    // ─────────────────────────────────────────────────────────────

    fun canControlPlayback(): Boolean {
        val room = stateStore.room.value ?: return false
        val isHost = room.hostUserId == myUserId
        val member = room.members.find { it.userId == myUserId }
        val playbackPerm = member?.perms?.playback ?: room.defaults.playback
        return isHost || playbackPerm
    }

    fun stepOutLocally() {
        _localPlaybackState.value = LocalPlaybackState.LocalStepOut
        _jamState.update { it.copy(isLocalStepOut = true) }
        playerManager.applyJamPause()
    }

    fun resumeFromLocalStepOut() {
        _localPlaybackState.value = LocalPlaybackState.Synchronized
        lastAppliedSnapshotVersion = null
        lastAppliedNowPlayingQid = null
        unplayableTrackSeq = null
        trackRetryCount = 0
        _jamState.update { it.copy(isLocalStepOut = false, isLocalPlaybackUnplayable = false) }
        val room = stateStore.room.value
        if (room != null && room.nowPlaying.item != null) {
            val serverNowPlaying = room.nowPlaying
            if (currentTrackSeq == serverNowPlaying.trackSeq) {
                val expectedPos = clockSync.expectedPosition(serverNowPlaying, clockSync.serverNow())
                playerManager.applyJamSeek(expectedPos)
                if (serverNowPlaying.status == JamPlaybackStatus.PLAYING) {
                    playerManager.applyJamPlay()
                }
            } else {
                reconcilerTrackState = ReconcilerTrackState.READY
                loadingTrackSeq = null
            }
        }
        reconcilePlayback()
    }

    fun togglePlayPause() {
        if (isLocalStepOut) {
            resumeFromLocalStepOut()
            return
        }
        val room = stateStore.room.value ?: return
        if (!canControlPlayback()) {
            if (room.nowPlaying.status == JamPlaybackStatus.PLAYING) {
                stepOutLocally()
            } else {
                showToast("You don't have permission to control playback")
            }
            return
        }
        val isPlaying = room.nowPlaying.status == JamPlaybackStatus.PLAYING
        scope.launch {
            val cmd = if (isPlaying) "pause" else "play"
            val res = jamClient.sendCommand(cmd)
            res.onFailure { err ->
                showToast(err.message ?: "Failed to change playback state")
            }
        }
    }

    fun play() {
        if (isLocalStepOut) {
            resumeFromLocalStepOut()
            return
        }
        if (!canControlPlayback()) {
            showToast("You don't have permission to control playback")
            return
        }
        scope.launch {
            val res = jamClient.sendCommand("play")
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun pause() {
        if (!canControlPlayback()) {
            stepOutLocally()
        } else {
            scope.launch {
                val res = jamClient.sendCommand("pause")
                res.onFailure { err -> showToast(err.message ?: "Permission denied") }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val payload = JSONObject().apply { put("positionMs", positionMs) }
            val res = jamClient.sendCommand("seek", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun nextTrack() {
        scope.launch {
            val res = jamClient.sendCommand("next")
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun previousTrack() {
        scope.launch {
            val res = jamClient.sendCommand("prev")
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun playQueueIndex(index: Int) {
        val room = stateStore.room.value ?: return
        val fullQueue = getFullQueue()
        val item = fullQueue.getOrNull(index) ?: return
        if (item.qid == room.nowPlaying.item?.qid) {
            seekTo(0)
            return
        }
        playItemByQid(item.qid)
    }

    fun playItemByQid(qid: String) {
        scope.launch {
            val payload = JSONObject().apply { put("qid", qid) }
            val res = jamClient.sendCommand("playItem", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun addSongToJam(song: Song, playNext: Boolean = false): Boolean {
        val userQueueSize = stateStore.room.value?.queue?.filter { !it.recommended }?.size ?: 0
        if (userQueueSize >= MAX_JAM_QUEUE_SIZE) {
            showToast("Queue is full (50 songs)")
            return false
        }
        scope.launch {
            val payload = JSONObject().apply {
                put("song", song.toJsonObject())
                if (playNext) put("playNext", true)
            }
            val res = jamClient.sendCommandRaw("queueAdd", payload)
            res.onSuccess { json ->
                val added = json.optInt("added", 1)
                val skippedDuplicate = json.optInt("skippedDuplicate", 0)
                val skippedFull = json.optInt("skippedFull", 0)
                if (skippedDuplicate > 0 && added == 0) {
                    showToast("Song already in queue")
                } else if (skippedFull > 0 && added == 0) {
                    showToast("Queue is full (50 songs)")
                } else {
                    showToast("Added to Jam: ${song.title}")
                }
            }
            res.onFailure { err ->
                showToast(err.message ?: "Failed to add song")
            }
        }
        return true
    }

    fun addSongsToJam(songs: List<Song>, limit: Int = 20): Int {
        if (isAddPlaylistInFlight) return 0
        isAddPlaylistInFlight = true

        val userQueueSize = stateStore.room.value?.queue?.filter { !it.recommended }?.size ?: 0
        val availableSlots = (MAX_JAM_QUEUE_SIZE - userQueueSize).coerceAtLeast(0)
        val toAdd = songs.take(limit).take(availableSlots)
        if (toAdd.isEmpty()) {
            isAddPlaylistInFlight = false
            showToast("Queue is full (50 songs)")
            return 0
        }

        scope.launch {
            try {
                val array = JSONArray()
                toAdd.forEach { array.put(it.toJsonObject()) }
                val payload = JSONObject().apply {
                    put("songs", array)
                }
                val res = jamClient.sendCommandRaw("queueAddMany", payload)
                res.onSuccess { json ->
                    val added = json.optInt("added", toAdd.size)
                    val skippedDuplicate = json.optInt("skippedDuplicate", 0)
                    val skippedFull = json.optInt("skippedFull", 0)
                    if (added > 0 && skippedDuplicate > 0) {
                        showToast("Added $added songs, $skippedDuplicate already in queue")
                    } else if (added > 0) {
                        showToast("Added $added songs to Jam")
                    } else if (skippedDuplicate > 0) {
                        showToast("All songs already in queue")
                    } else if (skippedFull > 0) {
                        showToast("Queue is full (50 songs)")
                    }
                }
                res.onFailure { err ->
                    showToast(err.message ?: "Failed to add songs")
                }
            } finally {
                delay(300L) // debounce
                isAddPlaylistInFlight = false
            }
        }
        return toAdd.size
    }

    fun removeSongFromJam(index: Int) {
        val room = stateStore.room.value ?: return
        val fullQueue = getFullQueue()
        val item = fullQueue.getOrNull(index) ?: return
        if (item.qid == room.nowPlaying.item?.qid) {
            return
        }
        removeByQid(item.qid)
    }

    fun removeByQid(qid: String) {
        scope.launch {
            val payload = JSONObject().apply { put("qid", qid) }
            val res = jamClient.sendCommand("queueRemove", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun reorderJamQueue(fromIndex: Int, toIndex: Int) {
        val room = stateStore.room.value ?: return
        val fullQueue = getFullQueue()
        val item = fullQueue.getOrNull(fromIndex) ?: return
        if (item.qid == room.nowPlaying.item?.qid) {
            return
        }
        val hasNowPlaying = room.nowPlaying.item != null
        val offset = if (hasNowPlaying) 1 else 0
        val targetQueueIndex = (toIndex - offset).coerceAtLeast(0)
        moveByQid(item.qid, targetQueueIndex)
    }

    fun moveByQid(qid: String, toQueueIndex: Int) {
        scope.launch {
            val payload = JSONObject().apply {
                put("qid", qid)
                put("toIndex", toQueueIndex)
            }
            val res = jamClient.sendCommand("queueMove", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun promoteRecommended(qid: String) {
        scope.launch {
            val payload = JSONObject().apply { put("qid", qid) }
            val res = jamClient.sendCommand("promote", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun setDefaults(perms: JamPermissions, applyToExisting: Boolean = false) {
        scope.launch {
            val payload = buildSetDefaultsPayload(perms, applyToExisting)
            val res = jamClient.sendCommand("setDefaults", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun setAttendeeReorderPermission(allow: Boolean) {
        scope.launch {
            val payload = JSONObject().apply {
                put("perms", JSONObject().apply {
                    put("queueManage", allow)
                })
                put("applyToExisting", true)
            }
            val res = jamClient.sendCommand("setDefaults", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun setMemberPerms(targetUserId: String, perms: JamPermissions) {
        scope.launch {
            val payload = JSONObject().apply {
                put("userId", targetUserId)
                put("perms", JSONObject().apply {
                    put("playback", perms.playback)
                    put("queueAdd", perms.queueAdd)
                    put("queueManage", perms.queueManage)
                })
            }
            val res = jamClient.sendCommand("setMemberPerms", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun kickMember(targetUserId: String) {
        scope.launch {
            val payload = JSONObject().apply { put("userId", targetUserId) }
            val res = jamClient.sendCommand("kick", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun transferHost(targetUserId: String) {
        scope.launch {
            val payload = JSONObject().apply { put("userId", targetUserId) }
            val res = jamClient.sendCommand("transferHost", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    private fun showToast(message: String) {
        toastEmitter(message)
    }

    // ─────────────────────────────────────────────────────────────
    // JamPlaybackInterceptor implementations (Player Mirror Mode)
    // ─────────────────────────────────────────────────────────────

    override fun onPlaySong(song: Song): Boolean {
        if (!_jamState.value.isInJam) return false
        addSongToJam(song, playNext = true)
        return true
    }

    override fun onPlayNext(song: Song): Boolean {
        if (!_jamState.value.isInJam) return false
        addSongToJam(song, playNext = true)
        return true
    }

    override fun onAddToQueue(song: Song): Boolean {
        if (!_jamState.value.isInJam) return false
        addSongToJam(song, playNext = false)
        return true
    }

    override fun onPlayQueue(songs: List<Song>, startIndex: Int): Boolean {
        if (!_jamState.value.isInJam) return false
        val toAdd = songs.drop(startIndex).ifEmpty { songs }
        addSongsToJam(toAdd, limit = 20)
        return true
    }

    override fun onStartArtistRadio(artist: Artist): Boolean {
        if (!_jamState.value.isInJam) return false
        showToast("Artist Radio is disabled during a Jam session")
        return true
    }

    override fun onTogglePlayPause(): Boolean {
        if (!_jamState.value.isInJam) return false
        togglePlayPause()
        return true
    }

    override fun onPlay(): Boolean {
        if (!_jamState.value.isInJam) return false
        play()
        return true
    }

    override fun onPause(): Boolean {
        if (!_jamState.value.isInJam) return false
        pause()
        return true
    }

    override fun onSeekTo(positionMs: Long): Boolean {
        if (!_jamState.value.isInJam) return false
        seekTo(positionMs)
        return true
    }

    override fun onSkipToNext(): Boolean {
        if (!_jamState.value.isInJam) return false
        nextTrack()
        return true
    }

    override fun onSkipToPrevious(): Boolean {
        if (!_jamState.value.isInJam) return false
        previousTrack()
        return true
    }

    override fun onPlayQueueIndex(index: Int): Boolean {
        if (!_jamState.value.isInJam) return false
        playQueueIndex(index)
        return true
    }

    override fun onRemoveFromQueue(index: Int): Boolean {
        if (!_jamState.value.isInJam) return false
        removeSongFromJam(index)
        return true
    }

    override fun onReorderQueue(fromIndex: Int, toIndex: Int): Boolean {
        if (!_jamState.value.isInJam) return false
        reorderJamQueue(fromIndex, toIndex)
        return true
    }

    override fun onToggleShuffle(): Boolean {
        if (!_jamState.value.isInJam) return false
        showToast("Shuffle is managed by the Jam session")
        return true
    }

    override fun onToggleRepeat(): Boolean {
        if (!_jamState.value.isInJam) return false
        showToast("Repeat is disabled during a Jam session")
        return true
    }

    override fun onTrackEnded(): Boolean {
        if (!_jamState.value.isInJam) return false
        handleTrackEndedOrError()
        return true
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException): Boolean {
        if (!_jamState.value.isInJam) return false
        handleTrackEndedOrError()
        return true
    }

    private fun handleTrackEndedOrError() {
        val room = stateStore.room.value ?: return
        val nowPlaying = room.nowPlaying
        if (nowPlaying.item == null) return

        val durationMs = (nowPlaying.item.song.durationSeconds ?: 0) * 1000L
        val expectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
        val remainingMs = durationMs - expectedPos

        if (durationMs > 0 && remainingMs > 5000L) {
            // Premature track ended (>5s remaining on server)
            if (retryTrackSeq != nowPlaying.trackSeq) {
                retryTrackSeq = nowPlaying.trackSeq
                trackRetryCount = 0
            }

            if (trackRetryCount < 2) {
                trackRetryCount++
                logD("JamSessionManager", "Premature track end (remaining: ${remainingMs}ms). Retry $trackRetryCount/2 for trackSeq ${nowPlaying.trackSeq}")
                val targetQid = nowPlaying.item.qid
                playerManager.playJamSyncSong(
                    song = nowPlaying.item.song,
                    initialPositionMs = expectedPos,
                    playWhenReady = (nowPlaying.status == JamPlaybackStatus.PLAYING),
                    targetQid = targetQid,
                    forceRefresh = true
                )
            } else {
                logW("JamSessionManager", "Max retries reached for trackSeq ${nowPlaying.trackSeq}. Staying silent until near-end.")
                unplayableTrackSeq = nowPlaying.trackSeq
                _jamState.update { it.copy(isLocalPlaybackUnplayable = true) }
                playerManager.applyJamPause()
                if (lastLoadingAbortedToastTrackSeq != nowPlaying.trackSeq) {
                    lastLoadingAbortedToastTrackSeq = nowPlaying.trackSeq
                    showToast("Couldn't play ${nowPlaying.item.song.title}")
                }
            }
        } else {
            // Legitimately reached near-end
            reportTrackEnded(nowPlaying.trackSeq)
        }
    }

    private fun checkNearEndTick() {
        val room = stateStore.room.value ?: return
        val nowPlaying = room.nowPlaying
        if (nowPlaying.item == null || nowPlaying.status != JamPlaybackStatus.PLAYING) return

        val durationMs = (nowPlaying.item.song.durationSeconds ?: 0) * 1000L
        val expectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
        val isNearEnd = if (durationMs > 0L) {
            expectedPos >= durationMs - 5000L
        } else {
            (clockSync.serverNow() - nowPlaying.anchorServerTs + nowPlaying.positionMs) >= 15000L
        }

        val isSilentOrEnded = trackRetryCount >= 2 || playerManager.getPlayerPlaybackState() == androidx.media3.common.Player.STATE_ENDED
        if (isNearEnd && isSilentOrEnded) {
            reportTrackEnded(nowPlaying.trackSeq)
        }
    }

    private fun reportTrackEnded(trackSeq: Long? = null) {
        val room = stateStore.room.value ?: return
        val targetSeq = trackSeq ?: room.nowPlaying.trackSeq
        if (targetSeq <= 0L) return
        if (lastReportedEndedTrackSeq == targetSeq) return
        if (isReportingEndedTrackSeq == targetSeq) return

        val now = currentTimeProvider()
        if (now - lastReportedEndedAttemptTs < 2000L) return
        lastReportedEndedAttemptTs = now

        isReportingEndedTrackSeq = targetSeq
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("trackSeq", targetSeq)
                }
                val res = jamClient.sendCommandRaw("trackEnded", payload)
                val isApplied = res.getOrNull()?.optBoolean("applied", false) ?: false
                val currentSeq = stateStore.room.value?.nowPlaying?.trackSeq ?: 0L
                if (isApplied || currentSeq > targetSeq) {
                    lastReportedEndedTrackSeq = targetSeq
                }
            } finally {
                if (isReportingEndedTrackSeq == targetSeq) {
                    isReportingEndedTrackSeq = null
                }
            }
        }
    }

    internal fun checkLoadingTimeout(currentTimeMs: Long = currentTimeProvider()) {
        if (reconcilerTrackState != ReconcilerTrackState.LOADING) return
        val startedAt = loadingStartedAt
        if (startedAt <= 0L) return
        if (currentTimeMs - startedAt < 15_000L) return

        val room = stateStore.room.value ?: return
        val nowPlaying = room.nowPlaying
        if (nowPlaying.item == null) return
        val targetSeq = loadingTrackSeq ?: nowPlaying.trackSeq
        if (targetSeq != nowPlaying.trackSeq) return

        if (retryTrackSeq != targetSeq) {
            retryTrackSeq = targetSeq
            trackRetryCount = 0
        }

        if (trackRetryCount < 2) {
            trackRetryCount++
            loadingStartedAt = currentTimeMs
            logD("JamSessionManager", "LOADING timeout (~15s) reached. Retry $trackRetryCount/2 for trackSeq $targetSeq")
            val expectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
            val targetQid = nowPlaying.item.qid
            playerManager.playJamSyncSong(
                song = nowPlaying.item.song,
                initialPositionMs = expectedPos,
                playWhenReady = (nowPlaying.status == JamPlaybackStatus.PLAYING),
                targetQid = targetQid,
                forceRefresh = true
            )
        } else {
            logW("JamSessionManager", "Max retries reached on LOADING timeout for trackSeq $targetSeq. Aborting loading.")
            unplayableTrackSeq = targetSeq
            _jamState.update { it.copy(isLocalPlaybackUnplayable = true) }
            reconcilerTrackState = ReconcilerTrackState.READY
            loadingTrackSeq = null
            loadingStartedAt = 0L
            playerManager.applyJamPause()

            if (lastLoadingAbortedToastTrackSeq != targetSeq) {
                lastLoadingAbortedToastTrackSeq = targetSeq
                showToast("Couldn't play ${nowPlaying.item.song.title}")
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (!_jamState.value.isInJam) return
        if (state == androidx.media3.common.Player.STATE_READY) {
            if (reconcilerTrackState == ReconcilerTrackState.LOADING) {
                reconcilerTrackState = ReconcilerTrackState.READY
                currentTrackSeq = loadingTrackSeq ?: currentTrackSeq
                loadingTrackSeq = null
                loadingStartedAt = 0L
                trackRetryCount = 0

                val room = stateStore.room.value
                if (room != null && room.nowPlaying.trackSeq == currentTrackSeq) {
                    val serverNowPlaying = room.nowPlaying
                    val expectedPos = clockSync.expectedPosition(serverNowPlaying, clockSync.serverNow())
                    playerManager.applyJamSeek(expectedPos)
                    if (serverNowPlaying.status == JamPlaybackStatus.PLAYING) {
                        playerManager.applyJamPlay()
                    }

                    val actualDurationMs = playerManager.getPlayerDuration()
                    val metadataDurationMs = (serverNowPlaying.item?.song?.durationSeconds ?: 0) * 1000L
                    logD("JamSessionManager", "New track READY: trackSeq=$currentTrackSeq metadataDur=$metadataDurationMs actualDur=$actualDurationMs")
                }
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!_jamState.value.isInJam) return
        if (!playWhenReady) {
            if (reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                stepOutLocally()
                if (!hasShownAudioFocusToastInThisJam) {
                    hasShownAudioFocusToastInThisJam = true
                    showToast("Paused for you only — tap Resume to rejoin")
                }
            } else if (reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                stepOutLocally()
            }
        }
    }

    fun retryPlayback() {
        unplayableTrackSeq = null
        trackRetryCount = 0
        reconcilerTrackState = ReconcilerTrackState.READY
        loadingTrackSeq = null
        loadingStartedAt = 0L
        _jamState.update { it.copy(isLocalPlaybackUnplayable = false) }
        reconcilePlayback()
    }

    private fun logD(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logW(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }
}

internal fun buildSetDefaultsPayload(perms: JamPermissions, applyToExisting: Boolean = false): JSONObject {
    return JSONObject().apply {
        put("perms", JSONObject().apply {
            put("playback", perms.playback)
            put("queueAdd", perms.queueAdd)
            put("queueManage", perms.queueManage)
        })
        if (applyToExisting) {
            put("applyToExisting", true)
        }
    }
}
