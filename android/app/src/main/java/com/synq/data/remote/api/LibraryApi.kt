package com.synq.data.remote.api

import com.synq.data.remote.dto.*
import retrofit2.http.*

/**
 * Retrofit interface for User Library backend endpoints under /api/v1/library/
 */
interface LibraryApi {

    // ─── Liked Songs ─────────────────────────────────────────────────────────

    @GET("library/likes")
    suspend fun getLikedSongs(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): ApiResponse<LikedSongsPageDto>

    @POST("library/likes")
    suspend fun likeSong(
        @Body request: LikeSongRequest,
    ): ApiResponse<LikedSongDto>

    @DELETE("library/likes/{songId}")
    suspend fun unlikeSong(
        @Path("songId") songId: String,
        @Query("providerId") providerId: String? = null,
    ): ApiResponse<UnlikeResponse>

    @GET("library/likes/check")
    suspend fun checkLikedSongs(
        @Query("songIds") songIds: String,
    ): ApiResponse<CheckLikesResponse>

    @POST("library/likes/sync")
    suspend fun syncLikes(
        @Body request: SyncLikesRequest,
    ): ApiResponse<SyncLikesResponse>

    // ─── Playlists ───────────────────────────────────────────────────────────

    @GET("library/playlists")
    suspend fun getPlaylists(): ApiResponse<List<PlaylistDto>>

    @POST("library/playlists")
    suspend fun createPlaylist(
        @Body request: CreatePlaylistRequest,
    ): ApiResponse<PlaylistDto>

    @GET("library/playlists/{id}")
    suspend fun getPlaylistById(
        @Path("id") id: String,
    ): ApiResponse<PlaylistDto>

    @PUT("library/playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: String,
        @Body request: UpdatePlaylistRequest,
    ): ApiResponse<PlaylistDto>

    @DELETE("library/playlists/{id}")
    suspend fun deletePlaylist(
        @Path("id") id: String,
    ): ApiResponse<SimpleSuccessDto>

    @POST("library/playlists/{id}/songs")
    suspend fun addSongToPlaylist(
        @Path("id") id: String,
        @Body request: AddPlaylistSongRequest,
    ): ApiResponse<PlaylistDto>

    @DELETE("library/playlists/{id}/songs/{songId}")
    suspend fun removeSongFromPlaylist(
        @Path("id") id: String,
        @Path("songId") songId: String,
    ): ApiResponse<PlaylistDto>

    @PUT("library/playlists/{id}/songs/reorder")
    suspend fun reorderPlaylistSongs(
        @Path("id") id: String,
        @Body request: ReorderPlaylistSongsRequest,
    ): ApiResponse<PlaylistDto>

    // ─── Followed Teams ──────────────────────────────────────────────────────

    @GET("library/teams")
    suspend fun getFollowedTeams(): ApiResponse<List<FollowedTeamDto>>

    @POST("library/teams")
    suspend fun followTeam(
        @Body request: FollowTeamRequest,
    ): ApiResponse<FollowedTeamDto>

    @DELETE("library/teams/{teamId}")
    suspend fun unfollowTeam(
        @Path("teamId") teamId: String,
    ): ApiResponse<SimpleSuccessDto>

    @GET("library/teams/check")
    suspend fun checkFollowedTeams(
        @Query("teamIds") teamIds: String,
    ): ApiResponse<CheckTeamsResponse>
}
