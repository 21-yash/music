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

/**
 * Retrofit interface for the Music API.
 * 
 * Note: Stream URL resolution is done dynamically in the Media3
 * player by hitting `/api/v1/music/stream?id=...`, not through Retrofit,
 * because ExoPlayer handles the actual audio streaming from the CDN URL.
 */
interface MusicApi {

    @GET("music/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<SearchResultsDto>

    @GET("music/trending")
    suspend fun getTrending(): ApiResponse<TrendingResponseDto>

    @GET("music/songs/{id}")
    suspend fun getSong(
        @Path("id") id: String
    ): ApiResponse<SongDto>

    @GET("music/albums/{id}")
    suspend fun getAlbum(
        @Path("id") id: String
    ): ApiResponse<AlbumDto>

    @GET("music/artists/{id}")
    suspend fun getArtist(
        @Path("id") id: String
    ): ApiResponse<ArtistDto>

    @GET("music/playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") id: String
    ): ApiResponse<PlaylistDto>
}
