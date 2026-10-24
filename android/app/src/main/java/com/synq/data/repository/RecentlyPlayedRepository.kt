package com.synq.data.repository

import com.synq.data.local.db.dao.RecentlyPlayedDao
import com.synq.data.local.db.entity.RecentlyPlayedEntity
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.Song
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyPlayedRepository @Inject constructor(
    private val recentlyPlayedDao: RecentlyPlayedDao,
) {
    val recentlyPlayed: Flow<List<RecentlyPlayedEntity>> = recentlyPlayedDao.getAll()

    suspend fun getRecentSongsList(): List<Song> {
        return recentlyPlayedDao.getRecentList().map { entity ->
            Song(
                id = entity.songId,
                title = entity.title,
                artists = listOf(ArtistCredit(id = entity.artist, name = entity.artist, imageUrl = null)),
                album = null,
                durationSeconds = entity.durationSeconds,
                imageUrl = entity.imageUrl,
                year = "",
                language = "",
                hasLyrics = false,
                playCount = 0,
                label = "",
                streamRef = entity.songId,
                providerId = entity.providerId,
            )
        }
    }

    suspend fun recordPlay(song: Song) {
        val entity = RecentlyPlayedEntity(
            id = "${song.id}_${song.providerId}",
            songId = song.id,
            providerId = song.providerId,
            title = song.title,
            artist = song.primaryArtistName,
            imageUrl = song.imageUrl,
            durationSeconds = song.durationSeconds,
            playedAt = System.currentTimeMillis(),
        )
        recentlyPlayedDao.recordPlay(entity)
    }

    suspend fun clearHistory() {
        recentlyPlayedDao.clearAll()
    }
}
