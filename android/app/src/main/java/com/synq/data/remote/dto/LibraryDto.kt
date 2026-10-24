package com.synq.data.remote.dto

import kotlinx.serialization.Serializable

// ─── Liked Songs ─────────────────────────────────────────────────────────────

@Serializable
data class LikedSongDto(
    val _id: String? = null,
    val userId: String? = null,
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val duration: Int = 0,
    val likedAt: String? = null,
)

@Serializable
data class LikedSongsPageDto(
    val songs: List<LikedSongDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 50,
    val hasMore: Boolean = false,
)

@Serializable
data class LikeSongRequest(
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val duration: Int? = null,
)

@Serializable
data class CheckLikesResponse(
    val likedMap: Map<String, Boolean> = emptyMap(),
    val likedSongIds: List<String> = emptyList(),
)

@Serializable
data class PendingLikeItemDto(
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val duration: Int? = null,
    val likedAt: Long? = null,
)

@Serializable
data class SyncLikesRequest(
    val pendingLikes: List<PendingLikeItemDto> = emptyList(),
    val pendingUnlikes: List<String> = emptyList(),
    val since: Long? = null,
)

@Serializable
data class SyncLikesResponse(
    val likes: List<LikedSongDto> = emptyList(),
    val serverTime: Long = 0L,
)

@Serializable
data class UnlikeResponse(
    val success: Boolean,
    val deletedCount: Int = 0,
)

// ─── Playlists ───────────────────────────────────────────────────────────────

@Serializable
data class PlaylistSongDto(
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val duration: Int = 0,
    val addedAt: String? = null,
    val position: Int = 0,
)

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val description: String = "",
    val imageUrl: String? = null,
    val songs: List<PlaylistSongDto> = emptyList(),
    val songCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class AddPlaylistSongRequest(
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val duration: Int? = null,
)

@Serializable
data class ReorderPlaylistSongsRequest(
    val songIds: List<String>,
)

// ─── Followed Teams ──────────────────────────────────────────────────────────

@Serializable
data class FollowedTeamDto(
    val teamId: String,
    val teamName: String,
    val shortName: String,
    val imageUrl: String? = null,
    val sport: String = "cricket",
    val followedAt: String? = null,
)

@Serializable
data class FollowTeamRequest(
    val teamId: String,
    val teamName: String,
    val shortName: String,
    val imageUrl: String? = null,
    val sport: String = "cricket",
)

@Serializable
data class CheckTeamsResponse(
    val followedMap: Map<String, Boolean> = emptyMap(),
    val followedTeamIds: List<String> = emptyList(),
)

@Serializable
data class SimpleSuccessDto(
    val success: Boolean = true,
)
