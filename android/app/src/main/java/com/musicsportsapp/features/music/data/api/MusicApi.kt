package com.musicsportsapp.features.music.data.api

import com.musicsportsapp.data.remote.dto.ApiResponse
import com.musicsportsapp.features.music.data.dto.AlbumDto
import com.musicsportsapp.features.music.data.dto.ArtistDto
import com.musicsportsapp.features.music.data.dto.PlaylistDto
import com.musicsportsapp.features.music.data.dto.SearchResultsDto
import com.musicsportsapp.features.music.data.dto.SongDto
import com.musicsportsapp.features.music.data.dto.TrendingResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

import com.musicsportsapp.features.music.data.dto.StreamInfoDto

/**
 * Retrofit interface for the Music API.
 */
interface MusicApi {

    @GET("music/stream")
    suspend fun getStream(
        @Query("ref") ref: String,
        @Query("quality") quality: String = "high",
        @Query("provider") provider: String? = null
    ): ApiResponse<StreamInfoDto>

    @GET("music/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("provider") provider: String? = null
    ): ApiResponse<SearchResultsDto>

    @GET("music/trending")
    suspend fun getTrending(
        @Query("provider") provider: String? = null
    ): ApiResponse<TrendingResponseDto>

    @GET("music/songs/{id}")
    suspend fun getSong(
        @Path("id") id: String,
        @Query("provider") provider: String? = null
    ): ApiResponse<SongDto>

    @GET("music/albums/{id}")
    suspend fun getAlbum(
        @Path("id") id: String,
        @Query("provider") provider: String? = null
    ): ApiResponse<AlbumDto>

    @GET("music/artists/{id}")
    suspend fun getArtist(
        @Path("id") id: String,
        @Query("provider") provider: String? = null
    ): ApiResponse<ArtistDto>

    @GET("music/playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") id: String,
        @Query("provider") provider: String? = null
    ): ApiResponse<PlaylistDto>
}
