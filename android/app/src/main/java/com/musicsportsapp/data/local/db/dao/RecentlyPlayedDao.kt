package com.musicsportsapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.musicsportsapp.data.local.db.entity.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentlyPlayedEntity)

    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT 50")
    fun getAll(): Flow<List<RecentlyPlayedEntity>>

    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC LIMIT 50")
    suspend fun getRecentList(): List<RecentlyPlayedEntity>

    @Query("DELETE FROM recently_played WHERE id NOT IN (SELECT id FROM recently_played ORDER BY playedAt DESC LIMIT 50)")
    suspend fun trimOldEntries()

    @Query("DELETE FROM recently_played")
    suspend fun clearAll()

    @Transaction
    suspend fun recordPlay(entity: RecentlyPlayedEntity) {
        insert(entity)
        trimOldEntries()
    }
}
