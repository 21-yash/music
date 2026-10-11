package com.musicsportsapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val durationSeconds: Int,
    val likedAt: Long = System.currentTimeMillis(),
    val pendingSyncAction: String? = null // "LIKE", "UNLIKE", or null if synced
)
