package com.synq.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_played")
data class RecentlyPlayedEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val durationSeconds: Int,
    val playedAt: Long = System.currentTimeMillis(),
    val albumId: String? = null
)
