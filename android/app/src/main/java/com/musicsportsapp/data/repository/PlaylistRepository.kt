package com.musicsportsapp.data.repository

import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.local.db.dao.PlaylistDao
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.local.db.entity.PlaylistSongEntity
import com.musicsportsapp.data.remote.api.LibraryApi
import com.musicsportsapp.data.remote.dto.*
import com.musicsportsapp.data.remote.safeApiCall
import com.musicsportsapp.features.music.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val libraryApi: LibraryApi,
) {
    val playlists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun getPlaylistSongs(playlistId: String): Flow<List<PlaylistSongEntity>> =
        playlistDao.getSongsForPlaylist(playlistId)

    suspend fun syncPlaylists(): AppResult<Unit> {
        return when (val result = safeApiCall { libraryApi.getPlaylists() }) {
            is AppResult.Success -> {
                val entities = result.data.map { dto ->
                    PlaylistEntity(
                        serverId = dto.id,
                        name = dto.name,
                        description = dto.description,
                        imageUrl = dto.imageUrl,
                        songCount = dto.songCount,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                playlistDao.clearPlaylists()
                if (entities.isNotEmpty()) {
                    playlistDao.insertPlaylists(entities)
                }
                AppResult.Success(Unit)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun createPlaylist(name: String, description: String? = null): AppResult<PlaylistDto> {
        val request = CreatePlaylistRequest(name = name, description = description)
        return when (val result = safeApiCall { libraryApi.createPlaylist(request) }) {
            is AppResult.Success -> {
                val dto = result.data
                val entity = PlaylistEntity(
                    serverId = dto.id,
                    name = dto.name,
                    description = dto.description,
                    imageUrl = dto.imageUrl,
                    songCount = dto.songCount,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                playlistDao.insertPlaylist(entity)
                AppResult.Success(dto)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun loadPlaylistDetails(playlistId: String): AppResult<PlaylistDto> {
        return when (val result = safeApiCall { libraryApi.getPlaylistById(playlistId) }) {
            is AppResult.Success -> {
                val dto = result.data
                val playlistEntity = PlaylistEntity(
                    serverId = dto.id,
                    name = dto.name,
                    description = dto.description,
                    imageUrl = dto.imageUrl,
                    songCount = dto.songs.size,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                val songEntities = dto.songs.map { s ->
                    PlaylistSongEntity(
                        id = "${dto.id}_${s.songId}",
                        playlistId = dto.id,
                        songId = s.songId,
                        providerId = s.providerId,
                        title = s.title,
                        artist = s.artist,
                        imageUrl = s.imageUrl,
                        durationSeconds = s.duration,
                        position = s.position,
                    )
                }
                playlistDao.syncPlaylistWithSongs(playlistEntity, songEntities)
                AppResult.Success(dto)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun deletePlaylist(playlistId: String): AppResult<Unit> {
        return when (val result = safeApiCall { libraryApi.deletePlaylist(playlistId) }) {
            is AppResult.Success -> {
                playlistDao.deletePlaylist(playlistId)
                AppResult.Success(Unit)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song): AppResult<PlaylistDto> {
        val request = AddPlaylistSongRequest(
            songId = song.id,
            providerId = song.providerId,
            title = song.title,
            artist = song.primaryArtistName,
            imageUrl = song.imageUrl,
            duration = song.durationSeconds,
        )
        return when (val result = safeApiCall { libraryApi.addSongToPlaylist(playlistId, request) }) {
            is AppResult.Success -> {
                loadPlaylistDetails(playlistId)
                AppResult.Success(result.data)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): AppResult<PlaylistDto> {
        return when (val result = safeApiCall { libraryApi.removeSongFromPlaylist(playlistId, songId) }) {
            is AppResult.Success -> {
                loadPlaylistDetails(playlistId)
                AppResult.Success(result.data)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        description: String? = null,
        imageUrl: String? = null
    ): AppResult<PlaylistDto> {
        val request = UpdatePlaylistRequest(
            name = name,
            description = description,
            imageUrl = imageUrl
        )
        return when (val result = safeApiCall { libraryApi.updatePlaylist(playlistId, request) }) {
            is AppResult.Success -> {
                loadPlaylistDetails(playlistId)
                AppResult.Success(result.data)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun reorderPlaylistSongs(
        playlistId: String,
        songIds: List<String>
    ): AppResult<PlaylistDto> {
        val request = ReorderPlaylistSongsRequest(songIds = songIds)
        return when (val result = safeApiCall { libraryApi.reorderPlaylistSongs(playlistId, request) }) {
            is AppResult.Success -> {
                loadPlaylistDetails(playlistId)
                AppResult.Success(result.data)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun getLocalPlaylist(playlistId: String): PlaylistEntity? =
        playlistDao.getPlaylistById(playlistId).firstOrNull()

    suspend fun getLocalPlaylistSongs(playlistId: String): List<PlaylistSongEntity> =
        playlistDao.getSongsForPlaylist(playlistId).firstOrNull() ?: emptyList()

    suspend fun clearLocalCache() {
        playlistDao.clearPlaylists()
    }
}
