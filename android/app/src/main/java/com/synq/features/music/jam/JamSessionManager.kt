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
    object LocalStepOut : LocalPlaybackState()
}

@Singleton
class JamSessionManager @Inject constructor(
    private val playerManager: MusicPlayerManager,
    private val authRepository: AuthRepository,
    private val networkMonitorProvider: Provider<NetworkMonitor>,
    private val jamPreferences: JamPreferences,
    @ApplicationContext private val context: Context
) : JamPlaybackInterceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var myUserId: String = ""

    val transport: JamTransport = SocketIoTransport(
        baseUrl = com.synq.BuildConfig.API_BASE_URL,
        apiKey = com.synq.BuildConfig.API_KEY
    )
    val clockSync: ClockSync = ClockSync()
    val stateStore: JamStateStore = JamStateStore(myUserId)
    val jamClient: JamClient = JamClient(
        transport = transport,
        clockSync = clockSync,
        stateStore = stateStore,
        tokenProvider = { authRepository.getFreshAccessToken() },
        scope = scope
    )

    private val _jamState = MutableStateFlow(JamState())
    val jamState: StateFlow<JamState> = _jamState.asStateFlow()

    private var currentTrackSeq: Long = 0L
    private var reconcilerTrackState: ReconcilerTrackState = ReconcilerTrackState.READY
    private var loadingTrackSeq: Long? = null
    private val _localPlaybackState = MutableStateFlow<LocalPlaybackState>(LocalPlaybackState.Synchronized)
    val localPlaybackState: StateFlow<LocalPlaybackState> = _localPlaybackState.asStateFlow()
    val isLocalStepOut: Boolean get() = _localPlaybackState.value is LocalPlaybackState.LocalStepOut
    private var reconcilerJob: Job? = null
    private var isReconciling = false

    companion object {
        const val MAX_JAM_QUEUE_SIZE = 50
    }

    init {
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
                    "member_joined" -> {
                        showToast("${event.event.username} joined the Jam")
                    }
                    "member_left" -> {
                        showToast("${event.event.username} left the Jam")
                    }
                    "host_changed" -> {
                        showToast("${event.event.username} is now the host")
                    }
                    "kicked" -> {
                        showToast("You were removed from the Jam session")
                        teardownJam()
                    }
                    "server_restarting" -> {
                        showToast("Server restarting, reconnecting...")
                    }
                    "perm_changed" -> {
                        showToast("Your Jam permissions were updated")
                    }
                }
            }

            is JamTransport.Event.Closed -> {
                val msg = when (event.reason) {
                    "ended" -> "The host ended the Jam session."
                    "kicked" -> "You were removed from the Jam session."
                    "expired" -> "The Jam session expired."
                    else -> "The Jam session was closed."
                }
                showToast(msg)
                teardownJam()
            }

            else -> {}
        }
    }

    private fun handleClientStateChange(state: JamClientState) {
        when (state) {
            is JamClientState.Active -> {
                jamPreferences.saveLastJam(state.code)
                playerManager.jamInterceptor = this
                updateLegacyState(stateStore.room.value, state)
            }

            is JamClientState.Closed -> {
                jamPreferences.clearLastJam()
                showToast(state.message)
                teardownJam()
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
                    queue = if (isInJam) prev.queue else emptyList()
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
        val queueSongs = snapshot.queue.map { it.song }

        _jamState.value = JamState(
            isInJam = true,
            sessionCode = snapshot.code,
            isHost = isHost,
            hostUserId = snapshot.hostUserId,
            hostName = hostMember?.username ?: "",
            allowAttendeeReorder = snapshot.defaults.queueManage,
            participants = participants,
            queue = queueSongs,
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
                    val recIds = currentRoom.queue.filter { it.recommended }.map { it.song.id }.toSet()
                    playerManager.setJamQueue(
                        jamQueue = currentRoom.queue.map { it.song },
                        targetSong = action.song,
                        recommendedIds = recIds
                    )

                    if (action.song != null) {
                        playerManager.playJamSyncSong(
                            song = action.song,
                            initialPositionMs = 0L,
                            playWhenReady = false
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
                }

                is ReconcilerAction.Nothing -> {
                    val recIds = currentRoom.queue.filter { it.recommended }.map { it.song.id }.toSet()
                    playerManager.setJamQueue(
                        jamQueue = currentRoom.queue.map { it.song },
                        targetSong = playback.currentSong,
                        recommendedIds = recIds
                    )
                }
            }
        } finally {
            isReconciling = false
        }
    }

    private fun reportTrackEnded() {
        scope.launch {
            val payload = JSONObject().apply {
                put("trackSeq", currentTrackSeq)
            }
            jamClient.sendCommand("trackEnded", payload)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Public Session Controls
    // ─────────────────────────────────────────────────────────────

    fun createJam(userId: String, username: String, avatarUrl: String? = null) {
        myUserId = userId
        stateStore.setMyUserId(userId)
        playerManager.jamInterceptor = this
        playerManager.stashPersonalQueue()
        _jamState.update { it.copy(isInJam = true, error = null) }

        scope.launch {
            val res = jamClient.createRoom(avatarUrl)
            res.onFailure { err ->
                showToast("Failed to create Jam: ${err.message}")
                teardownJam()
            }
        }
    }

    fun joinJam(code: String, userId: String, username: String, avatarUrl: String? = null) {
        myUserId = userId
        stateStore.setMyUserId(userId)
        val cleanCode = code.trim().uppercase()
        playerManager.jamInterceptor = this
        playerManager.stashPersonalQueue()
        _jamState.update { it.copy(isInJam = true, sessionCode = cleanCode, error = null) }

        scope.launch {
            val res = jamClient.joinRoom(cleanCode, avatarUrl)
            res.onFailure { err ->
                showToast("Failed to join Jam: ${err.message}")
                teardownJam()
            }
        }
    }

    fun leaveJam() {
        jamPreferences.clearLastJam()
        scope.launch {
            jamClient.leaveRoom()
        }
        teardownJam()
    }

    fun endJam() {
        jamPreferences.clearLastJam()
        scope.launch {
            jamClient.endRoom()
        }
        teardownJam()
    }

    private fun teardownJam() {
        playerManager.jamInterceptor = null
        playerManager.restorePersonalQueue()
        playerManager.setPlaybackSpeed(1.0f)
        _jamState.value = JamState()
        currentTrackSeq = 0L
        reconcilerTrackState = ReconcilerTrackState.READY
        loadingTrackSeq = null
        _localPlaybackState.value = LocalPlaybackState.Synchronized
    }

    fun onNetworkRestored() {
        if (!_jamState.value.isInJam) return
        jamClient.onNetworkRestored()
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
        _jamState.update { it.copy(isLocalStepOut = false) }
        val room = stateStore.room.value
        if (room != null && room.nowPlaying.item != null) {
            val serverNowPlaying = room.nowPlaying
            val expectedPos = clockSync.expectedPosition(serverNowPlaying, clockSync.serverNow())
            playerManager.applyJamSeek(expectedPos)
            if (serverNowPlaying.status == JamPlaybackStatus.PLAYING) {
                playerManager.applyJamPlay()
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
        val item = room.queue.getOrNull(index) ?: return
        scope.launch {
            val payload = JSONObject().apply { put("qid", item.qid) }
            val res = jamClient.sendCommand("playItem", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun addSongToJam(song: Song, playNext: Boolean = false): Boolean {
        val currentSize = stateStore.room.value?.queue?.size ?: _jamState.value.queue.size
        if (currentSize >= MAX_JAM_QUEUE_SIZE) {
            showToast("Jam queue is full (max $MAX_JAM_QUEUE_SIZE songs)")
            return false
        }
        scope.launch {
            val payload = JSONObject().apply {
                put("song", song.toJsonObject())
                if (playNext) put("playNext", true)
            }
            val res = jamClient.sendCommand("queueAdd", payload)
            res.onFailure { err ->
                showToast(err.message ?: "Failed to add song")
            }
        }
        return true
    }

    fun addSongsToJam(songs: List<Song>, limit: Int = 20): Int {
        val currentSize = stateStore.room.value?.queue?.size ?: _jamState.value.queue.size
        val availableSlots = (MAX_JAM_QUEUE_SIZE - currentSize).coerceAtLeast(0)
        val toAdd = songs.take(limit).take(availableSlots)
        if (toAdd.isEmpty()) return 0

        scope.launch {
            val array = JSONArray()
            toAdd.forEach { array.put(it.toJsonObject()) }
            val payload = JSONObject().apply {
                put("songs", array)
            }
            val res = jamClient.sendCommand("queueAddMany", payload)
            res.onFailure { err ->
                showToast(err.message ?: "Failed to add songs")
            }
        }
        return toAdd.size
    }

    fun removeSongFromJam(index: Int) {
        val room = stateStore.room.value ?: return
        val item = room.queue.getOrNull(index) ?: return
        scope.launch {
            val payload = JSONObject().apply { put("qid", item.qid) }
            val res = jamClient.sendCommand("queueRemove", payload)
            res.onFailure { err -> showToast(err.message ?: "Permission denied") }
        }
    }

    fun reorderJamQueue(fromIndex: Int, toIndex: Int) {
        val room = stateStore.room.value ?: return
        val item = room.queue.getOrNull(fromIndex) ?: return
        scope.launch {
            val payload = JSONObject().apply {
                put("qid", item.qid)
                put("toIndex", toIndex)
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

    fun setAttendeeReorderPermission(allow: Boolean) {
        scope.launch {
            val payload = JSONObject().apply {
                put("perms", JSONObject().apply {
                    put("queueManage", allow)
                })
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
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // JamPlaybackInterceptor implementations (Player Mirror Mode)
    // ─────────────────────────────────────────────────────────────

    override fun onPlaySong(song: Song): Boolean {
        if (!_jamState.value.isInJam) return false
        val added = addSongToJam(song, playNext = true)
        if (added) {
            showToast("Added to Jam queue: ${song.title}")
        }
        return true
    }

    override fun onPlayNext(song: Song): Boolean {
        if (!_jamState.value.isInJam) return false
        val added = addSongToJam(song, playNext = true)
        if (added) {
            showToast("Added to Jam queue: ${song.title}")
        }
        return true
    }

    override fun onAddToQueue(song: Song): Boolean {
        if (!_jamState.value.isInJam) return false
        val added = addSongToJam(song, playNext = false)
        if (added) {
            showToast("Added to Jam queue: ${song.title}")
        }
        return true
    }

    override fun onPlayQueue(songs: List<Song>, startIndex: Int): Boolean {
        if (!_jamState.value.isInJam) return false
        val toAdd = songs.drop(startIndex).ifEmpty { songs }
        val count = addSongsToJam(toAdd, limit = 20)
        if (count > 0) {
            showToast("Added top $count songs to Jam queue")
        }
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
        reportTrackEnded()
        return true
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (!_jamState.value.isInJam) return
        if (state == androidx.media3.common.Player.STATE_READY) {
            if (reconcilerTrackState == ReconcilerTrackState.LOADING) {
                reconcilerTrackState = ReconcilerTrackState.READY
                currentTrackSeq = loadingTrackSeq ?: currentTrackSeq
                loadingTrackSeq = null

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
                    android.util.Log.d("JamSessionManager", "New track READY: trackSeq=$currentTrackSeq metadataDur=$metadataDurationMs actualDur=$actualDurationMs")
                }
            }
        }
    }
}
