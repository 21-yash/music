package com.synq.data.repository

import com.synq.core.domain.AppResult
import com.synq.data.local.db.dao.PlaylistDao
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.data.local.db.entity.PlaylistSongEntity
import com.synq.data.remote.api.LibraryApi
import com.synq.data.remote.dto.*
import com.synq.data.remote.safeApiCall
import com.synq.features.music.domain.model.Song
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

    fun getPlaylistIdsForSong(songId: String): Flow<List<String>> =
        playlistDao.getPlaylistIdsForSong(songId)

    private fun parseDateToMillis(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                dateStr.toLong()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    suspend fun syncPlaylists(): AppResult<Unit> {
        return when (val result = safeApiCall { libraryApi.getPlaylists() }) {
            is AppResult.Success -> {
                val dtos = result.data
                val entities = dtos.map { dto ->
                    PlaylistEntity(
                        serverId = dto.id,
                        name = dto.name,
                        description = dto.description,
                        imageUrl = dto.imageUrl,
                        songCount = dto.songCount,
                        createdAt = parseDateToMillis(dto.createdAt),
                        updatedAt = parseDateToMillis(dto.updatedAt),
                    )
                }

                if (entities.isNotEmpty()) {
                    playlistDao.insertPlaylists(entities)
                    playlistDao.deletePlaylistsNotInList(entities.map { it.serverId })
                } else {
                    playlistDao.clearPlaylists()
                }

                // Sync lightweight songIds into Room without overwriting rich song metadata
                dtos.forEach { dto ->
                    val ids = dto.songIds.ifEmpty { dto.songs.map { it.songId } }
                    if (ids.isNotEmpty()) {
                        playlistDao.deleteSongsNotInList(dto.id, ids)
                        val stubEntities = ids.mapIndexed { index, songId ->
                            PlaylistSongEntity(
                                id = "${dto.id}_$songId",
                                playlistId = dto.id,
                                songId = songId,
                                providerId = "",
                                title = "",
                                artist = "",
                                imageUrl = null,
                                durationSeconds = 0,
                                position = index,
                            )
                        }
                        playlistDao.insertSongsIgnore(stubEntities)
                    } else {
                        playlistDao.deleteSongsForPlaylist(dto.id)
                    }
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
                    createdAt = parseDateToMillis(dto.createdAt),
                    updatedAt = parseDateToMillis(dto.updatedAt),
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
                    createdAt = parseDateToMillis(dto.createdAt),
                    updatedAt = parseDateToMillis(dto.updatedAt),
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
                        albumId = s.albumId,
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
            albumId = song.album?.id?.takeIf { it.isNotBlank() },
        )
        return when (val result = safeApiCall { libraryApi.addSongToPlaylist(playlistId, request) }) {
            is AppResult.Success -> {
                loadPlaylistDetails(playlistId)
                AppResult.Success(result.data)
            }
            is AppResult.Error -> {
                if (result.error.message?.contains("already in playlist", ignoreCase = true) == true) {
                    loadPlaylistDetails(playlistId)
                }
                AppResult.Error(result.error)
            }
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
