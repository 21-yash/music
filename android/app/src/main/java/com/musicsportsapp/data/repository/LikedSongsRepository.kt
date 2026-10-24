package com.musicsportsapp.data.repository

import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.local.db.dao.LikedSongDao
import com.musicsportsapp.data.local.db.entity.LikedSongEntity
import com.musicsportsapp.data.remote.api.LibraryApi
import com.musicsportsapp.data.remote.dto.LikeSongRequest
import com.musicsportsapp.data.remote.dto.PendingLikeItemDto
import com.musicsportsapp.data.remote.dto.SyncLikesRequest
import com.musicsportsapp.data.remote.safeApiCall
import com.musicsportsapp.features.music.domain.model.Song
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LikedSongsRepository @Inject constructor(
    private val likedSongDao: LikedSongDao,
    private val libraryApi: LibraryApi,
) {
    val likedSongs: Flow<List<LikedSongEntity>> = likedSongDao.getActiveLikedSongs()
    val likedSongsCount: Flow<Int> = likedSongDao.getLikedSongsCount()

    fun isSongLiked(songId: String): Flow<Boolean> = likedSongDao.isSongLiked(songId)

    suspend fun toggleLike(song: Song): Boolean {
        val existing = likedSongDao.findBySongId(song.id)
        val isCurrentlyLiked = existing != null && existing.pendingSyncAction != "UNLIKE"

        return if (isCurrentlyLiked) {
            unlikeSong(song.id, song.providerId)
            false
        } else {
            likeSong(song)
            true
        }
    }

    suspend fun likeSong(song: Song) {
        val entity = LikedSongEntity(
            id = "${song.id}_${song.providerId}",
            songId = song.id,
            providerId = song.providerId,
            title = song.title,
            artist = song.primaryArtistName,
            imageUrl = song.imageUrl,
            durationSeconds = song.durationSeconds,
            likedAt = System.currentTimeMillis(),
            pendingSyncAction = "LIKE",
        )
        // Optimistically write to Room cache
        likedSongDao.insert(entity)

        // Attempt remote push
        val request = LikeSongRequest(
            songId = song.id,
            providerId = song.providerId,
            title = song.title,
            artist = song.primaryArtistName,
            imageUrl = song.imageUrl,
            duration = song.durationSeconds,
        )
        when (safeApiCall { libraryApi.likeSong(request) }) {
            is AppResult.Success -> {
                // Mark as synced
                likedSongDao.insert(entity.copy(pendingSyncAction = null))
            }
            is AppResult.Error -> {
                // Stays in Room with pendingSyncAction = "LIKE" to be merged on sync
            }
        }
    }

    suspend fun unlikeSong(songId: String, providerId: String) {
        val existing = likedSongDao.findBySongId(songId)
        if (existing == null) return

        if (existing.pendingSyncAction == "LIKE") {
            // Never reached server, just remove locally
            likedSongDao.deleteBySongId(songId)
            return
        }

        // Mark as pending UNLIKE optimistically so UI reflects it immediately
        likedSongDao.insert(existing.copy(pendingSyncAction = "UNLIKE"))

        when (safeApiCall { libraryApi.unlikeSong(songId, providerId) }) {
            is AppResult.Success -> {
                likedSongDao.deleteBySongId(songId)
            }
            is AppResult.Error -> {
                // Kept with pendingSyncAction = "UNLIKE" for next sync merge
            }
        }
    }

    /**
     * Bidirectional offline-merge sync:
     * 1. Collect all local pending LIKE/UNLIKE actions
     * 2. Send to backend in sync payload
     * 3. Server applies pending changes and responds with updated source of truth
     * 4. Replace Room synced cache with server result
     */
    suspend fun syncWithServer(): AppResult<Unit> {
        val pendingItems = likedSongDao.getPendingSyncSongs()
        val pendingLikes = pendingItems.filter { it.pendingSyncAction == "LIKE" }.map {
            PendingLikeItemDto(
                songId = it.songId,
                providerId = it.providerId,
                title = it.title,
                artist = it.artist,
                imageUrl = it.imageUrl,
                duration = it.durationSeconds,
                likedAt = it.likedAt,
            )
        }
        val pendingUnlikes = pendingItems.filter { it.pendingSyncAction == "UNLIKE" }.map { it.songId }

        val request = SyncLikesRequest(
            pendingLikes = pendingLikes,
            pendingUnlikes = pendingUnlikes,
        )

        return when (val result = safeApiCall { libraryApi.syncLikes(request) }) {
            is AppResult.Success -> {
                val serverLikes = result.data.likes.map { dto ->
                    LikedSongEntity(
                        id = "${dto.songId}_${dto.providerId}",
                        songId = dto.songId,
                        providerId = dto.providerId,
                        title = dto.title,
                        artist = dto.artist,
                        imageUrl = dto.imageUrl,
                        durationSeconds = dto.duration,
                        likedAt = System.currentTimeMillis(),
                        pendingSyncAction = null,
                    )
                }
                likedSongDao.clearAll()
                if (serverLikes.isNotEmpty()) {
                    likedSongDao.insertAll(serverLikes)
                }
                AppResult.Success(Unit)
            }
            is AppResult.Error -> {
                AppResult.Error(result.error)
            }
        }
    }

    suspend fun syncLikedSongs(): AppResult<Unit> = syncWithServer()

    suspend fun clearLocalCache() {
        likedSongDao.clearAll()
    }
}
