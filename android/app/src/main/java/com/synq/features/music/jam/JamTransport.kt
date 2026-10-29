package com.synq.features.music.jam

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

class JamProtocolException(val code: String, message: String) : Exception("Jam error [$code]: $message")

interface JamTransport {
    sealed class Event {
        object Connected : Event()
        data class State(val snapshot: RoomSnapshot) : Event()
        data class Tick(val tick: Jam2Tick) : Event()
        data class JamEvent(val event: Jam2Event) : Event()
        data class Closed(val reason: String) : Event()
        data class ConnectError(val error: Throwable) : Event()
        data class Disconnected(val reason: String) : Event()
    }

    val events: SharedFlow<Event>
    val isConnected: Boolean

    fun connect(token: String?)
    suspend fun awaitConnected(timeoutMs: Long = 10000L): Result<Unit>
    fun disconnect()
    suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long = 5000L): Result<JSONObject>
}

class SocketIoTransport(
    private val baseUrl: String,
    private val apiKey: String
) : JamTransport {

    private var socket: Socket? = null
    @Volatile
    private var connectionDeferred = CompletableDeferred<Unit>()

    private val _events = MutableSharedFlow<JamTransport.Event>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<JamTransport.Event> = _events.asSharedFlow()

    override val isConnected: Boolean
        get() = socket?.connected() == true

    override fun connect(token: String?) {
        disconnect()

        val cleanUrl = baseUrl.trimEnd('/')
        val url = if (cleanUrl.endsWith("/api/v1")) {
            cleanUrl.substringBeforeLast("/api/v1")
        } else {
            cleanUrl
        }

        val authMap = mutableMapOf<String, String>()
        if (apiKey.isNotEmpty()) {
            authMap["apiKey"] = apiKey
        }
        if (!token.isNullOrBlank()) {
            authMap["token"] = token
        }

        val options = IO.Options.builder()
            .setTransports(arrayOf("websocket", "polling"))
            .setAuth(authMap)
            .setReconnection(false) // Reconnection is explicitly orchestrated by JamClient state machine
            .build()

        val deferred = CompletableDeferred<Unit>()
        connectionDeferred = deferred

        val s = IO.socket(url, options)
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            deferred.complete(Unit)
            _events.tryEmit(JamTransport.Event.Connected)
        }

        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val err = args.firstOrNull() as? Throwable ?: Exception(args.firstOrNull()?.toString() ?: "Connect error")
            deferred.completeExceptionally(err)
            _events.tryEmit(JamTransport.Event.ConnectError(err))
        }

        s.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args.firstOrNull()?.toString() ?: "unknown"
            _events.tryEmit(JamTransport.Event.Disconnected(reason))
        }

        s.on("jam2:state") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val snapshot = RoomSnapshot.fromJson(obj)
                _events.tryEmit(JamTransport.Event.State(snapshot))
            } catch (_: Exception) {}
        }

        s.on("jam2:tick") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val tick = Jam2Tick.fromJson(obj)
                _events.tryEmit(JamTransport.Event.Tick(tick))
            } catch (_: Exception) {}
        }

        s.on("jam2:event") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val event = Jam2Event.fromJson(obj)
                _events.tryEmit(JamTransport.Event.JamEvent(event))
            } catch (_: Exception) {}
        }

        s.on("jam2:closed") { args ->
            val obj = args.firstOrNull() as? JSONObject
            val reason = obj?.optString("reason") ?: "ended"
            _events.tryEmit(JamTransport.Event.Closed(reason))
        }

        s.connect()
    }

    override suspend fun awaitConnected(timeoutMs: Long): Result<Unit> {
        if (isConnected) return Result.success(Unit)
        if (socket == null) {
            return Result.failure(IllegalStateException("Socket is not initialized"))
        }
        val currentDeferred = connectionDeferred
        return try {
            withTimeout(timeoutMs) {
                currentDeferred.await()
                Result.success(Unit)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out waiting for Jam server connection (${timeoutMs / 1000}s)"))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override fun disconnect() {
        socket?.apply {
            off()
            disconnect()
        }
        socket = null
        if (connectionDeferred.isActive) {
            connectionDeferred.cancel()
        }
        connectionDeferred = CompletableDeferred()
    }

    override suspend fun emit(event: String, data: Any?, ackTimeoutMs: Long): Result<JSONObject> {
        val currentSocket = socket
        if (currentSocket == null) {
            return Result.failure(IllegalStateException("Socket is not initialized"))
        }

        if (!currentSocket.connected()) {
            val awaitRes = awaitConnected(minOf(ackTimeoutMs, 5000L))
            if (awaitRes.isFailure || !currentSocket.connected()) {
                val reason = awaitRes.exceptionOrNull()?.message ?: "Socket is not connected"
                return Result.failure(IllegalStateException(reason))
            }
        }

        return withTimeoutOrNull(ackTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val ack = Ack { args ->
                    val obj = args.firstOrNull() as? JSONObject
                    if (obj == null) {
                        continuation.resume(Result.failure(Exception("Empty ack received")))
                        return@Ack
                    }

                    if (obj.optBoolean("ok", false)) {
                        continuation.resume(Result.success(obj))
                    } else {
                        val code = obj.optString("code", "UNKNOWN")
                        val msg = obj.optString("message", "Request failed")
                        continuation.resume(Result.failure(JamProtocolException(code, msg)))
                    }
                }

                if (data != null) {
                    currentSocket.emit(event, data, ack)
                } else {
                    currentSocket.emit(event, ack)
                }
            }
        } ?: Result.failure(Exception("Ack timed out after ${ackTimeoutMs}ms"))
    }
}
