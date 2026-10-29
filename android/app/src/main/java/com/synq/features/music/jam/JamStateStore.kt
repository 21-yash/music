package com.synq.features.music.jam

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class JamUiState(
    val isInJam: Boolean = false,
    val sessionCode: String? = null,
    val isHost: Boolean = false,
    val hostUserId: String = "",
    val hostName: String = "",
    val myUserId: String = "",
    val myPerms: JamPermissions = JamPermissions(),
    val canControlPlayback: Boolean = false,
    val canAddSongs: Boolean = true,
    val canManageQueue: Boolean = false,
    val members: List<JamMember> = emptyList(),
    val queue: List<JamQueueItem> = emptyList(),
    val userQueue: List<JamQueueItem> = emptyList(),
    val recommendedQueue: List<JamQueueItem> = emptyList(),
    val history: List<JamQueueItem> = emptyList(),
    val nowPlaying: JamNowPlaying = JamNowPlaying(),
    val error: String? = null
)

class JamStateStore(
    private var myUserId: String = ""
) {
    private val _room = MutableStateFlow<RoomSnapshot?>(null)
    val room: StateFlow<RoomSnapshot?> = _room.asStateFlow()

    private val _uiState = MutableStateFlow(JamUiState())
    val uiState: StateFlow<JamUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<JamUiEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvents: SharedFlow<JamUiEvent> = _uiEvents.asSharedFlow()

    fun setMyUserId(userId: String) {
        myUserId = userId
        _room.value?.let { recomputeUiState(it) }
    }

    /**
     * Applies a snapshot from the server.
     * @param isEpoch If true (from jam2:join or jam2:sync), accepted unconditionally.
     *                If false (from jam2:state broadcast), accepted only if version > current.
     */
    fun applySnapshot(snapshot: RoomSnapshot, isEpoch: Boolean = false): Boolean {
        val current = _room.value
        if (!isEpoch && current != null && snapshot.version <= current.version) {
            // Drop stale or duplicate state broadcast
            return false
        }

        _room.value = snapshot
        recomputeUiState(snapshot)
        return true
    }

    /**
     * Applies a periodic lightweight heartbeat tick to correct position and anchor drift.
     */
    fun applyTick(tick: Jam2Tick): Boolean {
        val current = _room.value ?: return false
        if (tick.code != current.code) return false
        if (tick.nowPlaying.trackSeq != current.nowPlaying.trackSeq) return false

        val updatedNowPlaying = current.nowPlaying.copy(
            positionMs = tick.nowPlaying.positionMs,
            anchorServerTs = tick.nowPlaying.anchorServerTs,
            status = tick.nowPlaying.status
        )

        val updated = current.copy(nowPlaying = updatedNowPlaying)
        _room.value = updated
        _uiState.update { it.copy(nowPlaying = updatedNowPlaying) }
        return true
    }

    /**
     * Emits toast / ephemeral UI feedback for room events.
     */
    fun applyEvent(event: Jam2Event) {
        when (event.kind) {
            "member_joined" -> {
                _uiEvents.tryEmit(JamUiEvent.MemberJoined(event.username))
            }
            "member_left" -> {
                _uiEvents.tryEmit(JamUiEvent.MemberLeft(event.username))
            }
            "host_changed" -> {
                _uiEvents.tryEmit(JamUiEvent.HostChanged(event.username))
            }
            "kicked" -> {
                _uiEvents.tryEmit(JamUiEvent.Kicked("You were kicked from the Jam."))
            }
            "perm_changed" -> {
                val member = _room.value?.members?.find { it.userId == event.userId }
                if (member != null) {
                    _uiEvents.tryEmit(JamUiEvent.PermChanged(member.perms))
                    if (event.userId == myUserId) {
                        _room.value?.let { recomputeUiState(it) }
                    }
                }
            }
        }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }

    fun clear() {
        _room.value = null
        _uiState.value = JamUiState(myUserId = myUserId)
    }

    private fun recomputeUiState(room: RoomSnapshot) {
        val isHost = room.hostUserId == myUserId
        val hostMember = room.members.find { it.userId == room.hostUserId }
        val hostName = hostMember?.username ?: "Host"

        val me = room.members.find { it.userId == myUserId }
        val myPerms = me?.perms ?: room.defaults

        val canControlPlayback = isHost || myPerms.playback
        val canAddSongs = isHost || myPerms.queueAdd
        val canManageQueue = isHost || myPerms.queueManage

        val userQueue = room.queue.filter { !it.recommended }
        val recommendedQueue = room.queue.filter { it.recommended }

        _uiState.value = JamUiState(
            isInJam = true,
            sessionCode = room.code,
            isHost = isHost,
            hostUserId = room.hostUserId,
            hostName = hostName,
            myUserId = myUserId,
            myPerms = myPerms,
            canControlPlayback = canControlPlayback,
            canAddSongs = canAddSongs,
            canManageQueue = canManageQueue,
            members = room.members,
            queue = room.queue,
            userQueue = userQueue,
            recommendedQueue = recommendedQueue,
            history = room.history,
            nowPlaying = room.nowPlaying,
            error = null
        )
    }
}
