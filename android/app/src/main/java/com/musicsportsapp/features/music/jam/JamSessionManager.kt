package com.musicsportsapp.features.music.jam

import com.musicsportsapp.BuildConfig
import com.musicsportsapp.features.music.data.dto.AlbumRefDto
import com.musicsportsapp.features.music.data.dto.ArtistCreditDto
import com.musicsportsapp.features.music.data.dto.SongDto
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.playback.MusicPlayerManager
import io.socket.client.IO
import io.socket.client.Socket
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JamSessionManager @Inject constructor(
    private val playerManager: MusicPlayerManager
) {
    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _jamState = MutableStateFlow(JamState())
    val jamState: StateFlow<JamState> = _jamState.asStateFlow()

    private var myUserId: String = ""

    private fun connectSocket() {
        if (socket?.connected() == true) return

        try {
            val url = BuildConfig.API_BASE_URL.replace("/api/v1/", "")
            val options = IO.Options.builder()
                .setTransports(arrayOf("websocket"))
                .build()

            socket = IO.socket(url, options)

            socket?.on(Socket.EVENT_CONNECT) {
                // Connected
            }

            socket?.on("jam:created") { args ->
                scope.launch {
                    handleSessionPayload(args, isHostCreated = true)
                }
            }

            socket?.on("jam:joined") { args ->
                scope.launch {
                    handleSessionPayload(args, isHostCreated = false)
                }
            }

            socket?.on("jam:participants_updated") { args ->
                scope.launch {
                    if (args.isNotEmpty()) {
                        val obj = args[0] as? JSONObject
                        val array = obj?.optJSONArray("participants")
                        if (array != null) {
                            val participants = parseParticipants(array)
                            _jamState.update { it.copy(participants = participants) }
                        }
                    }
                }
            }

            socket?.on("jam:playback_updated") { args ->
                scope.launch {
                    if (!_jamState.value.isHost && args.isNotEmpty()) {
                        handlePlaybackUpdateFromHost(args[0] as? JSONObject)
                    }
                }
            }

            socket?.on("jam:queue_updated") { args ->
                scope.launch {
                    if (args.isNotEmpty()) {
                        val obj = args[0] as? JSONObject
                        val array = obj?.optJSONArray("queue")
                        if (array != null) {
                            val newQueue = parseSongs(array)
                            _jamState.update { it.copy(queue = newQueue) }
                            if (_jamState.value.isInJam) {
                                playerManager.setJamQueue(newQueue, playerManager.playbackState.value.currentSong)
                            }
                        }
                    }
                }
            }

            socket?.on("jam:permissions_updated") { args ->
                scope.launch {
                    if (args.isNotEmpty()) {
                        val obj = args[0] as? JSONObject
                        val allow = obj?.optBoolean("allowAttendeeReorder", false) ?: false
                        _jamState.update { it.copy(allowAttendeeReorder = allow) }
                    }
                }
            }

            socket?.on("jam:host_changed") { args ->
                scope.launch {
                    if (args.isNotEmpty()) {
                        val obj = args[0] as? JSONObject
                        val hostId = obj?.optString("hostUserId") ?: ""
                        val hostName = obj?.optString("hostName") ?: ""
                        val amIHost = hostId == myUserId
                        _jamState.update { it.copy(isHost = amIHost, hostUserId = hostId, hostName = hostName) }
                    }
                }
            }

            socket?.on("jam:error") { args ->
                scope.launch {
                    val msg = (args.firstOrNull() as? JSONObject)?.optString("message") ?: "Jam session error"
                    _jamState.update { it.copy(error = msg) }
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            _jamState.update { it.copy(error = e.message) }
        }
    }

    fun createJam(userId: String, username: String, avatarUrl: String? = null) {
        myUserId = userId
        connectSocket()

        val payload = JSONObject().apply {
            put("userId", userId)
            put("username", username)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
        }
        socket?.emit("jam:create", payload)
    }

    fun joinJam(code: String, userId: String, username: String, avatarUrl: String? = null) {
        myUserId = userId
        connectSocket()

        val payload = JSONObject().apply {
            put("code", code.trim().uppercase())
            put("userId", userId)
            put("username", username)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
        }
        socket?.emit("jam:join", payload)
    }

    fun leaveJam() {
        val code = _jamState.value.sessionCode
        if (!code.isNullOrBlank()) {
            val payload = JSONObject().apply { put("code", code) }
            socket?.emit("jam:leave", payload)
        }

        stopHostSync()
        playerManager.restorePersonalQueue()
        _jamState.value = JamState()
    }

    fun addSongToJam(song: Song) {
        val code = _jamState.value.sessionCode ?: return
        try {
            val dto = song.toDto()
            val songJson = JSONObject(json.encodeToString(dto))
            val payload = JSONObject().apply {
                put("code", code)
                put("song", songJson)
            }
            socket?.emit("jam:add_to_queue", payload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeSongFromJam(index: Int) {
        val code = _jamState.value.sessionCode ?: return
        val payload = JSONObject().apply {
            put("code", code)
            put("index", index)
        }
        socket?.emit("jam:remove_from_queue", payload)
    }

    fun reorderJamQueue(fromIndex: Int, toIndex: Int) {
        val code = _jamState.value.sessionCode ?: return
        val payload = JSONObject().apply {
            put("code", code)
            put("fromIndex", fromIndex)
            put("toIndex", toIndex)
        }
        socket?.emit("jam:reorder_queue", payload)
    }

    fun setAttendeeReorderPermission(allow: Boolean) {
        val code = _jamState.value.sessionCode ?: return
        if (!_jamState.value.isHost) return
        val payload = JSONObject().apply {
            put("code", code)
            put("allowAttendeeReorder", allow)
        }
        socket?.emit("jam:set_permissions", payload)
    }

    private fun handleSessionPayload(args: Array<Any>, isHostCreated: Boolean) {
        if (args.isEmpty()) return
        val root = args[0] as? JSONObject ?: return
        val session = root.optJSONObject("session") ?: return

        val code = session.optString("code")
        val hostUserId = session.optString("hostUserId")
        val hostName = session.optString("hostName")
        val allowAttendeeReorder = session.optBoolean("allowAttendeeReorder", false)
        val participants = parseParticipants(session.optJSONArray("participants"))
        val queue = parseSongs(session.optJSONArray("queue"))

        // Stash personal queue when entering jam
        playerManager.stashPersonalQueue()

        val isHost = isHostCreated || (hostUserId == myUserId)
        _jamState.value = JamState(
            isInJam = true,
            sessionCode = code,
            isHost = isHost,
            hostUserId = hostUserId,
            hostName = hostName,
            allowAttendeeReorder = allowAttendeeReorder,
            participants = participants,
            queue = queue,
            error = null
        )

        playerManager.setJamQueue(queue)

        if (isHost) {
            startHostSync(code)
        }
    }

    private fun handlePlaybackUpdateFromHost(obj: JSONObject?) {
        if (obj == null) return
        val songObj = obj.optJSONObject("currentSong")
        val isPlaying = obj.optBoolean("isPlaying", false)
        val positionMs = obj.optLong("positionMs", 0L)

        val hostSong = songObj?.let {
            try {
                json.decodeFromString<SongDto>(it.toString()).toDomain()
            } catch (_: Exception) { null }
        }

        val myPlayback = playerManager.playbackState.value

        if (hostSong != null && hostSong.id != myPlayback.currentSong?.id) {
            playerManager.playSong(hostSong)
        }

        val drift = Math.abs(positionMs - myPlayback.playbackPositionMs)
        if (drift > 1500) {
            playerManager.seekTo(positionMs)
        }

        if (isPlaying != myPlayback.isPlaying) {
            if (isPlaying) {
                playerManager.togglePlayPause()
            } else {
                playerManager.pause()
            }
        }
    }

    private fun startHostSync(code: String) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(1500)
                if (_jamState.value.isInJam && _jamState.value.isHost) {
                    val state = playerManager.playbackState.value
                    val payload = JSONObject().apply {
                        put("code", code)
                        if (state.currentSong != null) {
                            put("currentSong", JSONObject(json.encodeToString(state.currentSong.toDto())))
                        }
                        put("isPlaying", state.isPlaying)
                        put("positionMs", state.playbackPositionMs)
                    }
                    socket?.emit("jam:sync_playback", payload)
                }
            }
        }
    }

    private fun stopHostSync() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun parseParticipants(array: JSONArray?): List<JamParticipantUi> {
        if (array == null) return emptyList()
        val list = mutableListOf<JamParticipantUi>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            list.add(
                JamParticipantUi(
                    socketId = item.optString("socketId"),
                    userId = item.optString("userId"),
                    username = item.optString("username"),
                    avatarUrl = item.optString("avatarUrl").takeIf { it.isNotEmpty() },
                    isHost = item.optBoolean("isHost", false)
                )
            )
        }
        return list
    }

    private fun parseSongs(array: JSONArray?): List<Song> {
        if (array == null) return emptyList()
        val list = mutableListOf<Song>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            try {
                val dto = json.decodeFromString<SongDto>(item.toString())
                list.add(dto.toDomain())
            } catch (_: Exception) {}
        }
        return list
    }

    private fun Song.toDto(): SongDto = SongDto(
        id = id,
        title = title,
        artists = artists.map { ArtistCreditDto(it.id, it.name, it.imageUrl) },
        album = album?.let { AlbumRefDto(it.id, it.title, it.imageUrl) },
        duration = durationSeconds,
        imageUrl = imageUrl,
        year = year,
        language = language,
        hasLyrics = hasLyrics,
        playCount = playCount,
        label = label,
        streamRef = streamRef,
        providerId = providerId
    )
}
