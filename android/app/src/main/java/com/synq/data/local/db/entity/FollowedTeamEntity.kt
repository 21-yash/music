package com.synq.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "followed_teams")
data class FollowedTeamEntity(
    @PrimaryKey val teamId: String,
    val teamName: String,
    val shortName: String,
    val imageUrl: String?,
    val sport: String = "cricket",
    val followedAt: Long = System.currentTimeMillis()
)
