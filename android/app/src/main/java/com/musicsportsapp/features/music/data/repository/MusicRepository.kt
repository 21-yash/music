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
    suspend fun search(query: String, provider: String? = null, page: Int = 1, limit: Int = 20): AppResult<SearchResults> {
        return safeApiCall { api.search(query, page, limit, provider) }
            .map { it.toDomain() }
    }

    suspend fun getTrending(provider: String? = null): AppResult<List<Song>> {
        return safeApiCall { api.getTrending(provider) }
            .map { response -> response.songs.map { it.toDomain() } }
    }

    suspend fun getSong(id: String, provider: String? = null): AppResult<Song> {
        return safeApiCall { api.getSong(id, provider) }
            .map { it.toDomain() }
    }

    suspend fun getAlbum(id: String, provider: String? = null): AppResult<Album> {
        return safeApiCall { api.getAlbum(id, provider) }
            .map { it.toDomain() }
    }

    suspend fun getArtist(id: String, provider: String? = null): AppResult<Artist> {
        return safeApiCall { api.getArtist(id, provider) }
            .map { it.toDomain() }
    }

    suspend fun getPlaylist(id: String, provider: String? = null): AppResult<Playlist> {
        return safeApiCall { api.getPlaylist(id, provider) }
            .map { it.toDomain() }
    }

    suspend fun resolveStream(ref: String, provider: String? = null, quality: String = "high"): AppResult<StreamInfo> {
        return safeApiCall { api.getStream(ref, quality, provider) }
            .map { it.toDomain() }
    }

    suspend fun getHomeData(provider: String? = null): AppResult<HomeData> {
        return safeApiCall { api.getHomeData(provider) }
            .map { it.toDomain() }
    }
}
