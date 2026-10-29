package com.synq.features.music.jam

import com.synq.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class JamClient(
    private val transport: JamTransport,
    val stateStore: JamStateStore,
    val clockSync: ClockSync,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val authRepository: AuthRepository? = null,
    private val tokenProvider: (suspend () -> String?)? = null,
    private val autoClockSync: Boolean = true,
    private val monotonicNowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val wallClockNowMs: () -> Long = { System.currentTimeMillis() }
) {
    private suspend fun fetchFreshToken(): String? {
        return tokenProvider?.invoke() ?: authRepository?.getFreshAccessToken()
    }
    private val _clientState = MutableStateFlow<JamClientState>(JamClientState.Disconnected)
    val clientState: StateFlow<JamClientState> = _clientState.asStateFlow()

    private var activeRoomCode: String? = null
    private var reconnectJob: Job? = null
    private var clockSyncJob: Job? = null
    private var eventListenerJob: Job? = null
    private var notFoundRetryJob: Job? = null
    private var notFoundRetryCount = 0

    init {
        startEventListener()
    }

    private fun startEventListener() {
        eventListenerJob?.cancel()
        eventListenerJob = scope.launch(ioDispatcher) {
            transport.events.collect { event ->
                handleTransportEvent(event)
            }
        }
    }

    private fun handleTransportEvent(event: JamTransport.Event) {
        when (event) {
            is JamTransport.Event.Connected -> {
                reconnectJob?.cancel()
                reconnectJob = null

                if (autoClockSync) {
                    // Trigger clock sync immediately
                    scope.launch(ioDispatcher) {
                        performClockSync()
                        startClockSyncLoop()
                    }
                }

                // If reconnecting, re-join the active room code
                val code = activeRoomCode
                if (code != null && _clientState.value is JamClientState.Reconnecting) {
                    scope.launch(ioDispatcher) {
                        rejoinAfterReconnect(code)
                    }
                }
            }

            is JamTransport.Event.State -> {
                stateStore.applySnapshot(event.snapshot, isEpoch = false)
            }

            is JamTransport.Event.Tick -> {
                val currentRoom = stateStore.room.value
                val applied = stateStore.applyTick(event.tick)
                if (!applied || (currentRoom != null && event.tick.version > 0L && event.tick.version != currentRoom.version)) {
                    scope.launch(ioDispatcher) {
                        sync()
                    }
                }
            }

            is JamTransport.Event.JamEvent -> {
                stateStore.applyEvent(event.event)
            }

            is JamTransport.Event.Closed -> {
                handleServerClosed(event.reason)
            }

            is JamTransport.Event.ConnectError -> {
                handleConnectionLoss()
            }

            is JamTransport.Event.Disconnected -> {
                if (_clientState.value !is JamClientState.Closed && _clientState.value !is JamClientState.Disconnected) {
                    handleConnectionLoss()
                }
            }
        }
    }

    /**
     * Creates a new Jam room.
     */
    suspend fun createRoom(avatarUrl: String? = null): Result<RoomSnapshot> {
        _clientState.value = JamClientState.Connecting
        val token = fetchFreshToken()
        if (token.isNullOrBlank()) {
            val err = JamProtocolException("AUTH", "Authentication required")
            _clientState.value = JamClientState.Closed("auth_failed", "Please log in to start a Jam.")
            return Result.failure(err)
        }

        transport.connect(token)
        val connectRes = transport.awaitConnected(10000L)
        if (connectRes.isFailure) {
            _clientState.value = JamClientState.Disconnected
            val msg = connectRes.exceptionOrNull()?.message ?: "Failed to connect to Jam server"
            return Result.failure(IllegalStateException(msg))
        }

        val payload = JSONObject().apply {
            put("protocol", 2)
            if (!avatarUrl.isNullOrEmpty()) put("avatarUrl", avatarUrl)
        }

        val res = transport.emit("jam2:create", payload)
        return res.mapCatching { json ->
            val roomObj = json.getJSONObject("room")
            val snapshot = RoomSnapshot.fromJson(roomObj)
            activeRoomCode = snapshot.code
            stateStore.applySnapshot(snapshot, isEpoch = true)
            _clientState.value = JamClientState.Active(snapshot.code, JamRole.HOST)
            snapshot
        }.onFailure {
            _clientState.value = JamClientState.Disconnected
        }
    }

    /**
     * Joins an existing Jam room.
     */
    suspend fun joinRoom(code: String, avatarUrl: String? = null): Result<RoomSnapshot> {
        val cleanCode = code.trim().uppercase()
        _clientState.value = JamClientState.Joining(cleanCode)

        val token = fetchFreshToken()
        if (token.isNullOrBlank()) {
            val err = JamProtocolException("AUTH", "Authentication required")
            _clientState.value = JamClientState.Closed("auth_failed", "Please log in to join a Jam.")
            return Result.failure(err)
        }

        transport.connect(token)
        val connectRes = transport.awaitConnected(10000L)
        if (connectRes.isFailure) {
            _clientState.value = JamClientState.Disconnected
            val msg = connectRes.exceptionOrNull()?.message ?: "Failed to connect to Jam server"
            return Result.failure(IllegalStateException(msg))
        }

        val payload = JSONObject().apply {
            put("protocol", 2)
            put("code", cleanCode)
            if (!avatarUrl.isNullOrEmpty()) put("avatarUrl", avatarUrl)
        }

        val res = transport.emit("jam2:join", payload)
        return res.mapCatching { json ->
            val roomObj = json.getJSONObject("room")
            val snapshot = RoomSnapshot.fromJson(roomObj)
            activeRoomCode = snapshot.code
            stateStore.applySnapshot(snapshot, isEpoch = true)
            val role = if (snapshot.hostUserId == stateStore.uiState.value.myUserId) JamRole.HOST else JamRole.GUEST
            _clientState.value = JamClientState.Active(snapshot.code, role)
            snapshot
        }.onFailure {
            _clientState.value = JamClientState.Disconnected
        }
    }

    /**
     * Pulls the latest room snapshot without state mutation.
     */
    suspend fun sync(): Result<RoomSnapshot> {
        val code = activeRoomCode ?: return Result.failure(IllegalStateException("Not in a room"))
        val payload = JSONObject().apply { put("code", code) }

        val res = transport.emit("jam2:sync", payload)
        return res.mapCatching { json ->
            val roomObj = json.getJSONObject("room")
            val snapshot = RoomSnapshot.fromJson(roomObj)
            stateStore.applySnapshot(snapshot, isEpoch = true)
            snapshot
        }
    }

    /**
     * Sends a command to the room. If currently reconnecting, buffers the command with a 15s TTL.
     */
    suspend fun sendCommand(type: String, params: JSONObject = JSONObject()): Result<Long> {
        val currentState = _clientState.value
        if (currentState !is JamClientState.Active) {
            return Result.failure(IllegalStateException("Reconnecting… try again"))
        }

        val code = activeRoomCode ?: return Result.failure(IllegalStateException("Not in a room"))
        val cmdPayload = JSONObject(params.toString()).apply {
            put("code", code)
            put("type", type)
        }

        val res = transport.emit("jam2:cmd", cmdPayload)
        return res.mapCatching { json ->
            json.optLong("version", 0L)
        }
    }

    suspend fun leaveRoom(): Result<Unit> {
        val code = activeRoomCode
        activeRoomCode = null
        stopAll()

        if (code != null) {
            val payload = JSONObject().apply { put("code", code) }
            transport.emit("jam2:leave", payload)
        }

        transport.disconnect()
        stateStore.clear()
        _clientState.value = JamClientState.Disconnected
        return Result.success(Unit)
    }

    suspend fun endRoom(): Result<Unit> {
        val code = activeRoomCode
        activeRoomCode = null
        stopAll()

        if (code != null) {
            val payload = JSONObject().apply { put("code", code) }
            transport.emit("jam2:end", payload)
        }

        transport.disconnect()
        stateStore.clear()
        _clientState.value = JamClientState.Closed("ended", "The host ended this Jam")
        return Result.success(Unit)
    }

    private fun handleServerClosed(reason: String) {
        activeRoomCode = null
        stopAll()
        transport.disconnect()
        stateStore.clear()

        val userMessage = when (reason) {
            "kicked" -> "You were removed from this Jam"
            "ended" -> "The host ended this Jam"
            "expired" -> "This Jam expired"
            else -> "The Jam session was closed ($reason)."
        }

        _clientState.value = JamClientState.Closed(reason, userMessage)
    }

    fun onNetworkRestored() {
        val code = activeRoomCode ?: return
        if (_clientState.value !is JamClientState.Active && _clientState.value !is JamClientState.Reconnecting) return
        scheduleReconnect(code, attempt = 1)
    }

    private fun handleConnectionLoss() {
        val code = activeRoomCode ?: return
        if (_clientState.value is JamClientState.Closed) return
        if (_clientState.value is JamClientState.Reconnecting) return

        _clientState.value = JamClientState.Reconnecting(code, attempt = 1)
        scheduleReconnect(code, attempt = 1)
    }

    private fun scheduleReconnect(code: String, attempt: Int) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(ioDispatcher) {
            // Reconnect backoff: 1–5 s with jitter
            val baseDelay = minOf(5000L, 1000L * (1L shl minOf(attempt - 1, 2)))
            val jitter = Random.nextLong(0, 500)
            delay(baseDelay + jitter)

            if (!isActive) return@launch

            _clientState.value = JamClientState.Reconnecting(code, attempt)

            // Step 1: Refresh token before reconnecting
            val token = fetchFreshToken()
            if (token == null) {
                // Refresh failed with 401 or invalid token
                handleServerClosed("auth_failed")
                return@launch
            }

            // Step 2: Connect socket with fresh token
            transport.connect(token)
        }
    }

    private suspend fun rejoinAfterReconnect(code: String) {
        val payload = JSONObject().apply {
            put("protocol", 2)
            put("code", code)
        }

        val res = transport.emit("jam2:join", payload)
        res.onSuccess { json ->
            val roomObj = json.getJSONObject("room")
            val snapshot = RoomSnapshot.fromJson(roomObj)
            stateStore.applySnapshot(snapshot, isEpoch = true)
            val role = if (snapshot.hostUserId == stateStore.uiState.value.myUserId) JamRole.HOST else JamRole.GUEST
            _clientState.value = JamClientState.Active(code, role)
            notFoundRetryCount = 0
            notFoundRetryJob?.cancel()
            notFoundRetryJob = null
        }.onFailure { err ->
            if (err is JamProtocolException && err.code == "NOT_FOUND") {
                if (notFoundRetryCount < 4) {
                    notFoundRetryCount++
                    notFoundRetryJob?.cancel()
                    notFoundRetryJob = scope.launch(ioDispatcher) {
                        delay(2000L)
                        if (isActive && _clientState.value is JamClientState.Reconnecting) {
                            rejoinAfterReconnect(code)
                        }
                    }
                } else {
                    notFoundRetryCount = 0
                    handleServerClosed("ended")
                }
            } else if (err is JamProtocolException && err.code == "FORBIDDEN") {
                notFoundRetryCount = 0
                handleServerClosed("kicked")
            } else {
                val currentAttempt = (_clientState.value as? JamClientState.Reconnecting)?.attempt ?: 1
                scheduleReconnect(code, currentAttempt + 1)
            }
        }
    }

    private suspend fun performClockSync() {
        val samples = mutableListOf<PingSample>()
        for (i in 0 until 3) {
            val sendMonotonic = monotonicNowMs()
            val ackRes = transport.emit("jam2:time", null, ackTimeoutMs = 2000L)
            val receiveMonotonic = monotonicNowMs()
            ackRes.onSuccess { json ->
                val serverNow = json.optLong("serverNow", 0L)
                if (serverNow > 0L) {
                    samples.add(PingSample(sendMonotonic, serverNow, receiveMonotonic))
                }
            }
            if (i < 2) delay(100)
        }

        if (samples.isNotEmpty()) {
            clockSync.updateWithSamples(samples)
        }
    }

    private fun startClockSyncLoop() {
        clockSyncJob?.cancel()
        clockSyncJob = scope.launch(ioDispatcher) {
            while (isActive) {
                delay(30_000L)
                performClockSync()
            }
        }
    }

    private fun stopAll() {
        reconnectJob?.cancel()
        reconnectJob = null
        notFoundRetryJob?.cancel()
        notFoundRetryJob = null
        notFoundRetryCount = 0
        clockSyncJob?.cancel()
        clockSyncJob = null
        clockSync.reset()
    }

    fun destroy() {
        stopAll()
        eventListenerJob?.cancel()
        transport.disconnect()
    }
}
