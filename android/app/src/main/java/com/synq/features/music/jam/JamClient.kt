package com.synq.features.music.jam

import com.synq.data.remote.TokenRefreshResult
import com.synq.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

data class JamClosedEvent(val reason: String, val message: String, val initiatedByMe: Boolean = false)

class JamClient(
    private val transport: JamTransport,
    val stateStore: JamStateStore,
    val clockSync: ClockSync,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val authRepository: AuthRepository? = null,
    private val tokenProvider: (suspend () -> String?)? = null,
    private val tokenResultProvider: (suspend () -> TokenRefreshResult)? = null,
    private val autoClockSync: Boolean = true,
    private val monotonicNowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val wallClockNowMs: () -> Long = { System.currentTimeMillis() }
) {
    private suspend fun fetchFreshTokenResult(): TokenRefreshResult {
        if (tokenResultProvider != null) {
            return tokenResultProvider.invoke()
        }
        if (authRepository != null) {
            return authRepository.getFreshAccessTokenResult()
        }
        val legacy = tokenProvider?.invoke()
        return if (!legacy.isNullOrBlank()) {
            TokenRefreshResult.Success(legacy)
        } else {
            TokenRefreshResult.Unauthorized
        }
    }

    private val _clientState = MutableStateFlow<JamClientState>(JamClientState.Disconnected)
    val clientState: StateFlow<JamClientState> = _clientState.asStateFlow()

    private val _closedEvents = MutableSharedFlow<JamClosedEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val closedEvents: SharedFlow<JamClosedEvent> = _closedEvents.asSharedFlow()

    private var activeRoomCode: String? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 1
    private var reconnectStartedAt = 0L
    private var clockSyncJob: Job? = null
    private var eventListenerJob: Job? = null
    private var notFoundRetryCount = 0
    private var closedEmittedForSession = false

    private fun emitClosedOnce(reason: String, message: String, initiatedByMe: Boolean) {
        if (activeRoomCode == null && closedEmittedForSession) {
            return
        }
        closedEmittedForSession = true
        _closedEvents.tryEmit(JamClosedEvent(reason, message, initiatedByMe))
    }

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
                // Connection handled directly inside awaitConnected or join
                if (autoClockSync) {
                    scope.launch(ioDispatcher) {
                        performClockSync()
                        startClockSyncLoop()
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

            is JamTransport.Event.ParseError -> {
                safeLogW("JamClient", "Parse error for ${event.eventName}; keeping current state and requesting sync()")
                scope.launch(ioDispatcher) {
                    sync()
                }
            }
        }
    }

    /**
     * Creates a new Jam room.
     */
    suspend fun createRoom(avatarUrl: String? = null): Result<RoomSnapshot> {
        closedEmittedForSession = false
        _clientState.value = JamClientState.Connecting
        val tokenRes = fetchFreshTokenResult()
        val token = when (tokenRes) {
            is TokenRefreshResult.Success -> tokenRes.token
            is TokenRefreshResult.Transient -> tokenRes.fallbackToken
            is TokenRefreshResult.Unauthorized -> null
        }

        if (token.isNullOrBlank()) {
            if (tokenRes is TokenRefreshResult.Transient) {
                val err = JamProtocolException("NETWORK", "Check your internet connection and try again")
                _clientState.value = JamClientState.Disconnected
                return Result.failure(err)
            } else {
                val err = JamProtocolException("AUTH", "Authentication required")
                _clientState.value = JamClientState.Closed("auth_failed", "Please log in to start a Jam.")
                return Result.failure(err)
            }
        }

        transport.connect(token)
        val connectRes = transport.awaitConnected(10000L)
        if (connectRes.isFailure) {
            transport.disconnect()
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
            if (res.isSuccess) {
                val code = res.getOrNull()?.optJSONObject("room")?.optString("code")
                if (!code.isNullOrBlank()) {
                    try {
                        transport.emit("jam2:end", JSONObject().apply { put("code", code) }, ackTimeoutMs = 1500L)
                    } catch (_: Exception) {}
                }
            }
            transport.disconnect()
            _clientState.value = JamClientState.Disconnected
        }
    }

    /**
     * Joins an existing Jam room.
     */
    suspend fun joinRoom(code: String, avatarUrl: String? = null): Result<RoomSnapshot> {
        closedEmittedForSession = false
        val cleanCode = code.trim().uppercase()
        _clientState.value = JamClientState.Joining(cleanCode)

        val tokenRes = fetchFreshTokenResult()
        val token = when (tokenRes) {
            is TokenRefreshResult.Success -> tokenRes.token
            is TokenRefreshResult.Transient -> tokenRes.fallbackToken
            is TokenRefreshResult.Unauthorized -> null
        }

        if (token.isNullOrBlank()) {
            if (tokenRes is TokenRefreshResult.Transient) {
                val err = JamProtocolException("NETWORK", "Check your internet connection and try again")
                _clientState.value = JamClientState.Disconnected
                return Result.failure(err)
            } else {
                val err = JamProtocolException("AUTH", "Authentication required")
                _clientState.value = JamClientState.Closed("auth_failed", "Please log in to join a Jam.")
                return Result.failure(err)
            }
        }

        transport.connect(token)
        val connectRes = transport.awaitConnected(10000L)
        if (connectRes.isFailure) {
            transport.disconnect()
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
            if (res.isSuccess) {
                try {
                    transport.emit("jam2:leave", JSONObject().apply { put("code", cleanCode) }, ackTimeoutMs = 1500L)
                } catch (_: Exception) {}
            }
            transport.disconnect()
            _clientState.value = JamClientState.Disconnected
        }
    }

    /**
     * Pulls the latest room snapshot without state mutation.
     * Uses isEpoch = false so stale responses cannot roll back newer state (Task A8).
     */
    suspend fun sync(): Result<RoomSnapshot> {
        val code = activeRoomCode ?: return Result.failure(IllegalStateException("Not in a room"))
        val payload = JSONObject().apply { put("code", code) }

        val res = transport.emit("jam2:sync", payload)
        return res.mapCatching { json ->
            val roomObj = json.getJSONObject("room")
            val snapshot = RoomSnapshot.fromJson(roomObj)
            stateStore.applySnapshot(snapshot, isEpoch = false)
            snapshot
        }
    }

    /**
     * Sends a command to the room. Fails fast if not in Active state.
     */
    suspend fun sendCommandRaw(type: String, params: JSONObject = JSONObject()): Result<JSONObject> {
        val currentState = _clientState.value
        if (currentState !is JamClientState.Active) {
            return Result.failure(IllegalStateException("Reconnecting… try again"))
        }

        val code = activeRoomCode ?: return Result.failure(IllegalStateException("Not in a room"))
        val cmdPayload = JSONObject(params.toString()).apply {
            put("code", code)
            put("type", type)
        }

        return transport.emit("jam2:cmd", cmdPayload)
    }

    suspend fun sendCommand(type: String, params: JSONObject = JSONObject()): Result<Long> {
        return sendCommandRaw(type, params).mapCatching { json ->
            json.optLong("version", 0L)
        }
    }

    suspend fun leaveRoom(): Result<Unit> {
        val code = activeRoomCode
        activeRoomCode = null
        stopAll()

        if (code != null) {
            val payload = JSONObject().apply { put("code", code) }
            try {
                transport.emit("jam2:leave", payload, ackTimeoutMs = 2000L)
            } catch (_: Exception) {}
        }

        transport.disconnect()
        stateStore.clear()
        emitClosedOnce("left", "You left the Jam", initiatedByMe = true)
        _clientState.value = JamClientState.Disconnected
        return Result.success(Unit)
    }

    suspend fun endRoom(): Result<Unit> {
        val code = activeRoomCode ?: return Result.failure(IllegalStateException("Not in a room"))
        val payload = JSONObject().apply { put("code", code) }
        val res = transport.emit("jam2:end", payload, ackTimeoutMs = 5000L)
        if (res.isFailure) {
            return Result.failure(res.exceptionOrNull() ?: Exception("Failed to end Jam"))
        }

        activeRoomCode = null
        stopAll()
        transport.disconnect()
        stateStore.clear()
        emitClosedOnce("ended", "The host ended this Jam", initiatedByMe = true)
        _clientState.value = JamClientState.Disconnected
        return Result.success(Unit)
    }

    private fun handleServerClosed(reason: String) {
        if (activeRoomCode == null && closedEmittedForSession) {
            return
        }
        activeRoomCode = null
        stopAll()
        transport.disconnect()
        stateStore.clear()

        val userMessage = when (reason) {
            "kicked" -> "You were removed from this Jam"
            "ended" -> "The host ended this Jam"
            "expired" -> "This Jam expired"
            "connection_lost" -> "Connection lost. Please rejoin the Jam."
            else -> "The Jam session was closed ($reason)."
        }

        emitClosedOnce(reason, userMessage, initiatedByMe = false)
        _clientState.value = JamClientState.Closed(reason, userMessage)
    }

    fun resetToDisconnected() {
        _clientState.value = JamClientState.Disconnected
    }

    internal fun setActiveForTesting(code: String, role: JamRole = JamRole.GUEST) {
        activeRoomCode = code
        _clientState.value = JamClientState.Active(code, role)
    }

    fun onNetworkRestored() {
        val code = activeRoomCode ?: return
        val current = _clientState.value
        if (current !is JamClientState.Active && current !is JamClientState.Reconnecting) return

        if (current is JamClientState.Active && transport.isConnected) {
            scope.launch(ioDispatcher) {
                val ping1 = transport.emit("jam2:time", null, ackTimeoutMs = HealthCheckPolicy.PING_TIMEOUT_MS)
                val action1 = HealthCheckPolicy.onFirstPingResult(ping1.isSuccess)
                when (action1) {
                    is HealthCheckAction.DoNothing -> return@launch
                    is HealthCheckAction.Retry -> {
                        delay(action1.delayMs)
                        val ping2 = transport.emit("jam2:time", null, ackTimeoutMs = action1.timeoutMs)
                        val action2 = HealthCheckPolicy.onRetryPingResult(ping2.isSuccess)
                        if (action2 is HealthCheckAction.DisconnectAndReconnect) {
                            transport.disconnect()
                            startReconnectLoop(code, resetBackoff = true, trigger = "health-check failed")
                        }
                    }
                    is HealthCheckAction.DisconnectAndReconnect -> {
                        transport.disconnect()
                        startReconnectLoop(code, resetBackoff = true, trigger = "health-check failed")
                    }
                }
            }
            return
        }

        startReconnectLoop(code, resetBackoff = true, trigger = "network restored")
    }

    private fun handleConnectionLoss() {
        val code = activeRoomCode ?: return
        if (_clientState.value is JamClientState.Closed) return
        startReconnectLoop(code, resetBackoff = false, trigger = "transport connection lost")
    }

    private fun startReconnectLoop(code: String, resetBackoff: Boolean, trigger: String = "unknown") {
        android.util.Log.i("JamClient", "Starting reconnect loop for room $code. Trigger: $trigger")
        if (resetBackoff) {
            reconnectAttempt = 1
            reconnectStartedAt = monotonicNowMs()
        } else if (reconnectStartedAt == 0L) {
            reconnectStartedAt = monotonicNowMs()
        }

        _clientState.value = JamClientState.Reconnecting(code, reconnectAttempt)

        if (reconnectJob?.isActive == true && !resetBackoff) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch(ioDispatcher) {
            runReconnectLoop(code, immediateFirst = resetBackoff)
        }
    }

    private suspend fun runReconnectLoop(code: String, immediateFirst: Boolean) {
        val maxDurationMs = 10 * 60 * 1000L // 10 minutes
        var isImmediate = immediateFirst

        while (currentCoroutineContext().isActive) {
            val elapsed = monotonicNowMs() - reconnectStartedAt
            if (elapsed >= maxDurationMs) {
                handleServerClosed("connection_lost")
                return
            }

            _clientState.value = JamClientState.Reconnecting(code, reconnectAttempt)

            if (!isImmediate) {
                // Exponential backoff with jitter: 1s, 2s, 4s, 8s... cap ~15s
                val baseDelay = minOf(15000L, 1000L * (1L shl minOf(reconnectAttempt - 1, 4)))
                val jitter = Random.nextLong(0, 500)
                delay(baseDelay + jitter)
            }
            isImmediate = false

            if (!currentCoroutineContext().isActive) return

            // Step 1: Token resolution
            val tokenRes = fetchFreshTokenResult()
            val tokenToUse = when (tokenRes) {
                is TokenRefreshResult.Unauthorized -> {
                    handleServerClosed("auth_failed")
                    return
                }
                is TokenRefreshResult.Transient -> {
                    tokenRes.fallbackToken
                }
                is TokenRefreshResult.Success -> {
                    tokenRes.token
                }
            }

            // Step 2: Connect socket if not connected
            if (!transport.isConnected) {
                transport.connect(tokenToUse)
                val connectRes = transport.awaitConnected(10000L)
                if (connectRes.isFailure) {
                    reconnectAttempt++
                    continue
                }
            }

            // Step 3: Clock sync if enabled
            if (autoClockSync) {
                performClockSync()
                startClockSyncLoop()
            }

            // Step 4: Rejoin the room
            val rejoinRes = performRejoin(code)
            if (rejoinRes.isSuccess) {
                reconnectAttempt = 1
                reconnectStartedAt = 0L
                reconnectJob = null
                return
            } else {
                val err = rejoinRes.exceptionOrNull()
                if (err is JamProtocolException && err.code == "FORBIDDEN") {
                    handleServerClosed("kicked")
                    return
                } else if (err is JamProtocolException && err.code == "NOT_FOUND") {
                    if (notFoundRetryCount < 4) {
                        notFoundRetryCount++
                        delay(2000L)
                        isImmediate = true
                        continue
                    } else {
                        notFoundRetryCount = 0
                        handleServerClosed("ended")
                        return
                    }
                } else {
                    reconnectAttempt++
                    continue
                }
            }
        }
    }

    private suspend fun performRejoin(code: String): Result<RoomSnapshot> {
        val payload = JSONObject().apply {
            put("protocol", 2)
            put("code", code)
        }

        val res = transport.emit("jam2:join", payload)
        return res.mapCatching { json ->
            val roomObj = json.getJSONObject("room")
            val snapshot = RoomSnapshot.fromJson(roomObj)
            if (autoClockSync) {
                performClockSync()
            }
            stateStore.applySnapshot(snapshot, isEpoch = true)
            val role = if (snapshot.hostUserId == stateStore.uiState.value.myUserId) JamRole.HOST else JamRole.GUEST
            _clientState.value = JamClientState.Active(code, role)
            notFoundRetryCount = 0
            snapshot
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
                if (_clientState.value is JamClientState.Active) {
                    performClockSync()
                }
            }
        }
    }

    private fun stopAll() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 1
        reconnectStartedAt = 0L
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
