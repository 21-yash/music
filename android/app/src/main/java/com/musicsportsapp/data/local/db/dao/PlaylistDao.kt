package com.musicsportsapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.local.db.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE serverId = :id")
    fun getPlaylistById(id: String): Flow<PlaylistEntity?>

    @Query("DELETE FROM playlists WHERE serverId = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()

    // Playlist Songs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongsForPlaylist(playlistId: String): Flow<List<PlaylistSongEntity>>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongsForPlaylist(playlistId: String)

    @Transaction
    suspend fun syncPlaylistWithSongs(
        playlist: PlaylistEntity,
        songs: List<PlaylistSongEntity>
    ) {
        insertPlaylist(playlist)
        deleteSongsForPlaylist(playlist.serverId)
        if (songs.isNotEmpty()) {
            insertSongs(songs)
        }
    }
}
