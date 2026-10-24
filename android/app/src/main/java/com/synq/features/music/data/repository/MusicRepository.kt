package com.synq.features.music.data.repository

import com.synq.core.domain.AppResult
import com.synq.data.remote.safeApiCall
import com.synq.features.music.data.api.MusicApi
import com.synq.features.music.domain.model.*
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

    private var cachedTrendingSongs: List<Song>? = null
    private var cachedHomeData: HomeData? = null

    fun getCachedTrending(): List<Song>? = cachedTrendingSongs
    fun getCachedHomeData(): HomeData? = cachedHomeData

    suspend fun getTrending(provider: String? = null, forceRefresh: Boolean = false): AppResult<List<Song>> {
        if (!forceRefresh && cachedTrendingSongs != null) {
            return AppResult.Success(cachedTrendingSongs!!)
        }
        val result = safeApiCall { api.getTrending(provider) }
            .map { response -> response.songs.map { it.toDomain() } }
        if (result is AppResult.Success) {
            cachedTrendingSongs = result.data
        }
        return result
    }

    suspend fun getSong(id: String, provider: String? = null): AppResult<Song> {
        return safeApiCall { api.getSong(id, provider) }
            .map { it.toDomain() }
    }

    suspend fun getAlbum(id: String, provider: String? = null): AppResult<Album> {
        return safeApiCall { api.getAlbum(id, provider) }
            .map { it.toDomain() }
    }

    private val artistCache = java.util.concurrent.ConcurrentHashMap<String, Artist>()

    suspend fun getArtist(id: String, provider: String? = null): AppResult<Artist> {
        val trimmed = id.trim()
        val lower = trimmed.lowercase()
        artistCache[lower]?.let { return AppResult.Success(it) }
        artistCache[trimmed]?.let { return AppResult.Success(it) }

        val result = safeApiCall { api.getArtist(id, provider) }
            .map { it.toDomain() }

        if (result is AppResult.Success) {
            val artist = result.data
            if (artistCache.size > 200) {
                artistCache.clear()
            }
            if (artist.id.isNotBlank()) {
                artistCache[artist.id.trim()] = artist
                artistCache[artist.id.trim().lowercase()] = artist
            }
            if (artist.name.isNotBlank()) {
                artistCache[artist.name.trim().lowercase()] = artist
            }
            artistCache[trimmed] = artist
            artistCache[lower] = artist
        }
        return result
    }

    suspend fun getPlaylist(id: String, provider: String? = null): AppResult<Playlist> {
        return safeApiCall { api.getPlaylist(id, provider) }
            .map { it.toDomain() }
    }

    suspend fun resolveStream(ref: String, provider: String? = null, quality: String = "high"): AppResult<StreamInfo> {
        return safeApiCall { api.getStream(ref, quality, provider) }
            .map { it.toDomain() }
    }

    suspend fun getHomeData(provider: String? = null, forceRefresh: Boolean = false): AppResult<HomeData> {
        if (!forceRefresh && cachedHomeData != null) {
            return AppResult.Success(cachedHomeData!!)
        }
        val result = safeApiCall { api.getHomeData(provider) }
            .map { it.toDomain() }
        if (result is AppResult.Success) {
            cachedHomeData = result.data
        }
        return result
    }

    suspend fun getRelatedSongs(
        songId: String,
        provider: String? = null,
        title: String? = null,
        artist: String? = null,
        limit: Int = 10
    ): AppResult<List<Song>> {
        return safeApiCall { api.getRelated(songId, provider, title, artist, limit) }
            .map { response -> response.songs.map { it.toDomain() } }
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int? = null,
        songId: String? = null,
        provider: String? = null
    ): AppResult<Lyrics> {
        return safeApiCall { api.getLyrics(title, artist, duration, songId, provider) }
            .map { it.toDomain() }
    }
}
