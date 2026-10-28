package com.synq.features.sports.data.remote

import com.synq.BuildConfig
import com.synq.data.repository.AuthRepository
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportsSocketService @Inject constructor(
    private val authRepository: AuthRepository,
) {
    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeMatches = mutableSetOf<String>()
    private var isReconnecting = false

    private val _matchUpdates = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val matchUpdates: SharedFlow<String> = _matchUpdates

    fun connect() {
        if (socket?.connected() == true) return

        scope.launch {
            val token = authRepository.getFreshAccessToken()
            initSocket(token)
        }
    }

    private fun initSocket(token: String?) {
        try {
            socket?.disconnect()
            socket?.off()

            val url = BuildConfig.API_BASE_URL.replace("/api/v1/", "")
            val authMap = mutableMapOf<String, String>()
            if (BuildConfig.API_KEY.isNotEmpty()) {
                authMap["apiKey"] = BuildConfig.API_KEY
            }
            if (!token.isNullOrBlank()) {
                authMap["token"] = token
            }

            val options = IO.Options.builder()
                .setTransports(arrayOf("websocket"))
                .setAuth(authMap)
                .build()

            socket = IO.socket(url, options)

            socket?.on(Socket.EVENT_CONNECT) {
                isReconnecting = false
                // Re-join matches on connect/reconnect
                for (matchId in activeMatches) {
                    val payload = JSONObject().apply { put("matchId", matchId) }
                    socket?.emit("join_match", payload)
                }
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) {
                if (!isReconnecting) {
                    isReconnecting = true
                    scope.launch {
                        val freshToken = authRepository.refreshToken()
                        if (freshToken != null) {
                            initSocket(freshToken)
                        } else {
                            isReconnecting = false
                        }
                    }
                }
            }

            socket?.on("match_update") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0]
                    if (data is JSONObject) {
                        _matchUpdates.tryEmit(data.toString())
                    } else if (data is String) {
                        _matchUpdates.tryEmit(data)
                    }
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            isReconnecting = false
        }
    }

    fun joinMatch(matchId: String) {
        activeMatches.add(matchId)
        val payload = JSONObject().apply { put("matchId", matchId) }
        socket?.emit("join_match", payload)
    }

    fun leaveMatch(matchId: String) {
        activeMatches.remove(matchId)
        val payload = JSONObject().apply { put("matchId", matchId) }
        socket?.emit("leave_match", payload)
    }

    fun disconnect() {
        activeMatches.clear()
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
