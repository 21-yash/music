package com.musicsportsapp.features.music.data.repository

import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.remote.safeApiCall
import com.musicsportsapp.features.music.data.api.MusicApi
import com.musicsportsapp.features.music.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: MusicApi
) {
    suspend fun search(query: String, page: Int = 1, limit: Int = 20): AppResult<SearchResults> {
        return safeApiCall { api.search(query, page, limit) }
            .map { it.toDomain() }
    }

    suspend fun getTrending(): AppResult<List<Song>> {
        return safeApiCall { api.getTrending() }
            .map { response -> response.songs.map { it.toDomain() } }
    }

    suspend fun getSong(id: String): AppResult<Song> {
        return safeApiCall { api.getSong(id) }
            .map { it.toDomain() }
    }

    suspend fun getAlbum(id: String): AppResult<Album> {
        return safeApiCall { api.getAlbum(id) }
            .map { it.toDomain() }
    }

    suspend fun getArtist(id: String): AppResult<Artist> {
        return safeApiCall { api.getArtist(id) }
            .map { it.toDomain() }
    }

    suspend fun getPlaylist(id: String): AppResult<Playlist> {
        return safeApiCall { api.getPlaylist(id) }
            .map { it.toDomain() }
    }
}
