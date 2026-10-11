package com.musicsportsapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val serverId: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val songCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["serverId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistSongEntity(
    @PrimaryKey val id: String, // "${playlistId}_${songId}"
    val playlistId: String,
    val songId: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val durationSeconds: Int,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
