package com.musicsportsapp.features.sports.data.remote

import com.musicsportsapp.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportsSocketService @Inject constructor() {
    private var socket: Socket? = null

    private val _matchUpdates = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val matchUpdates: SharedFlow<String> = _matchUpdates

    fun connect() {
        if (socket?.connected() == true) return
        
        try {
            // Usually socket binds to root domain, not the /api/v1/ prefix
            val url = BuildConfig.API_BASE_URL.replace("/api/v1/", "") 
            val options = IO.Options.builder()
                .setTransports(arrayOf("websocket"))
                .build()
            
            socket = IO.socket(url, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                // Connected
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
        }
    }

    fun joinMatch(matchId: String) {
        val payload = JSONObject().apply { put("matchId", matchId) }
        socket?.emit("join_match", payload)
    }

    fun leaveMatch(matchId: String) {
        val payload = JSONObject().apply { put("matchId", matchId) }
        socket?.emit("leave_match", payload)
    }

    fun disconnect() {
        socket?.disconnect()
    }
}
