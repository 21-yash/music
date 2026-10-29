package com.synq.features.music.jam

import com.synq.features.music.data.dto.AlbumRefDto
import com.synq.features.music.data.dto.ArtistCreditDto
import com.synq.features.music.data.dto.SongDto
import com.synq.features.music.domain.model.Song
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

private val jamJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

data class JamPermissions(
    val queueAdd: Boolean = true,
    val playback: Boolean = false,
    val queueManage: Boolean = false
) {
    companion object {
        fun fromJson(obj: JSONObject?): JamPermissions {
            if (obj == null) return JamPermissions()
            return JamPermissions(
                queueAdd = obj.optBoolean("queueAdd", true),
                playback = obj.optBoolean("playback", false),
                queueManage = obj.optBoolean("queueManage", false)
            )
        }
    }
}

data class JamMember(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val joinedAt: Long = 0L,
    val online: Boolean = true,
    val lastSeenAt: Long = 0L,
    val perms: JamPermissions = JamPermissions()
) {
    companion object {
        fun fromJson(obj: JSONObject): JamMember {
            return JamMember(
                userId = obj.optString("userId"),
                username = obj.optString("username"),
                avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotEmpty() },
                joinedAt = obj.optLong("joinedAt"),
                online = obj.optBoolean("online", true),
                lastSeenAt = obj.optLong("lastSeenAt"),
                perms = JamPermissions.fromJson(obj.optJSONObject("perms"))
            )
        }
    }
}

data class JamQueueItem(
    val qid: String,
    val song: Song,
    val addedByUserId: String? = null,
    val addedAt: Long = 0L,
    val recommended: Boolean = false
) {
    companion object {
        fun fromJson(obj: JSONObject): JamQueueItem? {
            val qid = obj.optString("qid")
            val songObj = obj.optJSONObject("song") ?: return null
            val song = try {
                val dto = jamJson.decodeFromString<SongDto>(songObj.toString())
                dto.toDomain()
            } catch (_: Exception) {
                return null
            }

            return JamQueueItem(
                qid = qid,
                song = song,
                addedByUserId = obj.optString("addedByUserId").takeIf { it.isNotEmpty() },
                addedAt = obj.optLong("addedAt"),
                recommended = obj.optBoolean("recommended", false)
            )
        }
    }
}

enum class JamPlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED;

    companion object {
        fun fromString(str: String?): JamPlaybackStatus {
            return when (str?.lowercase()) {
                "playing" -> PLAYING
                "paused" -> PAUSED
                else -> IDLE
            }
        }
    }
}

data class JamNowPlaying(
    val item: JamQueueItem? = null,
    val status: JamPlaybackStatus = JamPlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val anchorServerTs: Long = 0L,
    val trackSeq: Long = 0L
) {
    companion object {
        fun fromJson(obj: JSONObject?): JamNowPlaying {
            if (obj == null) return JamNowPlaying()
            val itemObj = obj.optJSONObject("item")
            val item = if (itemObj != null) JamQueueItem.fromJson(itemObj) else null
            return JamNowPlaying(
                item = item,
                status = JamPlaybackStatus.fromString(obj.optString("status")),
                positionMs = obj.optLong("positionMs", 0L),
                anchorServerTs = obj.optLong("anchorServerTs", 0L),
                trackSeq = obj.optLong("trackSeq", 0L)
            )
        }
    }
}

data class RoomSnapshot(
    val code: String,
    val version: Long,
    val serverNow: Long,
    val hostUserId: String,
    val createdAt: Long,
    val members: List<JamMember>,
    val defaults: JamPermissions,
    val queue: List<JamQueueItem>,
    val history: List<JamQueueItem>,
    val nowPlaying: JamNowPlaying
) {
    companion object {
        fun fromJson(obj: JSONObject): RoomSnapshot {
            val membersArr = obj.optJSONArray("members")
            val members = mutableListOf<JamMember>()
            if (membersArr != null) {
                for (i in 0 until membersArr.length()) {
                    membersArr.optJSONObject(i)?.let { members.add(JamMember.fromJson(it)) }
                }
            }

            val queueArr = obj.optJSONArray("queue")
            val queue = mutableListOf<JamQueueItem>()
            if (queueArr != null) {
                for (i in 0 until queueArr.length()) {
                    queueArr.optJSONObject(i)?.let { JamQueueItem.fromJson(it)?.let { item -> queue.add(item) } }
                }
            }

            val historyArr = obj.optJSONArray("history")
            val history = mutableListOf<JamQueueItem>()
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    historyArr.optJSONObject(i)?.let { JamQueueItem.fromJson(it)?.let { item -> history.add(item) } }
                }
            }

            return RoomSnapshot(
                code = obj.optString("code"),
                version = obj.optLong("version", 0L),
                serverNow = obj.optLong("serverNow", 0L),
                hostUserId = obj.optString("hostUserId"),
                createdAt = obj.optLong("createdAt", 0L),
                members = members,
                defaults = JamPermissions.fromJson(obj.optJSONObject("defaults")),
                queue = queue,
                history = history,
                nowPlaying = JamNowPlaying.fromJson(obj.optJSONObject("nowPlaying"))
            )
        }
    }
}

data class JamTickNowPlaying(
    val positionMs: Long,
    val anchorServerTs: Long,
    val status: JamPlaybackStatus,
    val trackSeq: Long
)

data class Jam2Tick(
    val code: String,
    val serverTime: Long,
    val nowPlaying: JamTickNowPlaying
) {
    companion object {
        fun fromJson(obj: JSONObject): Jam2Tick {
            val npObj = obj.optJSONObject("nowPlaying")
            val nowPlaying = JamTickNowPlaying(
                positionMs = npObj?.optLong("positionMs", 0L) ?: 0L,
                anchorServerTs = npObj?.optLong("anchorServerTs", 0L) ?: 0L,
                status = JamPlaybackStatus.fromString(npObj?.optString("status")),
                trackSeq = npObj?.optLong("trackSeq", 0L) ?: 0L
            )
            return Jam2Tick(
                code = obj.optString("code"),
                serverTime = obj.optLong("serverTime", 0L),
                nowPlaying = nowPlaying
            )
        }
    }
}

data class Jam2Event(
    val kind: String, // member_joined, member_left, kicked, host_changed, server_restarting, perm_changed
    val userId: String,
    val username: String
) {
    companion object {
        fun fromJson(obj: JSONObject): Jam2Event {
            return Jam2Event(
                kind = obj.optString("kind"),
                userId = obj.optString("userId"),
                username = obj.optString("username")
            )
        }
    }
}

enum class JamRole {
    HOST,
    GUEST
}

sealed class JamClientState {
    object Disconnected : JamClientState()
    object Connecting : JamClientState()
    data class Joining(val code: String) : JamClientState()
    data class Active(val code: String, val role: JamRole) : JamClientState()
    data class Reconnecting(val code: String, val attempt: Int) : JamClientState()
    data class Closed(val reason: String, val message: String) : JamClientState()
}

sealed class JamUiEvent {
    data class Toast(val message: String) : JamUiEvent()
    data class MemberJoined(val username: String) : JamUiEvent()
    data class MemberLeft(val username: String) : JamUiEvent()
    data class HostChanged(val newHostUsername: String) : JamUiEvent()
    data class Kicked(val reason: String) : JamUiEvent()
    data class PermChanged(val permissions: JamPermissions) : JamUiEvent()
}

fun Song.toJsonObject(): JSONObject {
    val dto = SongDto(
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
    return JSONObject(jamJson.encodeToString(SongDto.serializer(), dto))
}
