package com.synq.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.synq.data.local.db.entity.FollowedTeamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedTeamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(team: FollowedTeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(teams: List<FollowedTeamEntity>)

    @Query("SELECT * FROM followed_teams ORDER BY followedAt DESC")
    fun getAllFollowedTeams(): Flow<List<FollowedTeamEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_teams WHERE teamId = :teamId)")
    fun isTeamFollowed(teamId: String): Flow<Boolean>

    @Query("DELETE FROM followed_teams WHERE teamId = :teamId")
    suspend fun deleteByTeamId(teamId: String)

    @Query("DELETE FROM followed_teams")
    suspend fun clearAll()
}
