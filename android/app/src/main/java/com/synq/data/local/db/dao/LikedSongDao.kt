package com.synq.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.synq.data.local.db.entity.LikedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LikedSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LikedSongEntity>)

    @Query("SELECT * FROM liked_songs WHERE pendingSyncAction != 'UNLIKE' OR pendingSyncAction IS NULL ORDER BY likedAt DESC")
    fun getActiveLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT COUNT(*) FROM liked_songs WHERE pendingSyncAction != 'UNLIKE' OR pendingSyncAction IS NULL")
    fun getLikedSongsCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId AND (pendingSyncAction != 'UNLIKE' OR pendingSyncAction IS NULL))")
    fun isSongLiked(songId: String): Flow<Boolean>

    @Query("SELECT * FROM liked_songs WHERE songId = :songId LIMIT 1")
    suspend fun findBySongId(songId: String): LikedSongEntity?

    @Query("SELECT * FROM liked_songs WHERE pendingSyncAction IS NOT NULL")
    suspend fun getPendingSyncSongs(): List<LikedSongEntity>

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM liked_songs WHERE pendingSyncAction IS NULL")
    suspend fun clearSyncedLikes()

    @Query("DELETE FROM liked_songs")
    suspend fun clearAll()
}
