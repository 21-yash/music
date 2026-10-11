package com.musicsportsapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.musicsportsapp.data.local.db.dao.FollowedTeamDao
import com.musicsportsapp.data.local.db.dao.LikedSongDao
import com.musicsportsapp.data.local.db.dao.PlaylistDao
import com.musicsportsapp.data.local.db.dao.RecentlyPlayedDao
import com.musicsportsapp.data.local.db.entity.FollowedTeamEntity
import com.musicsportsapp.data.local.db.entity.LikedSongEntity
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.local.db.entity.PlaylistSongEntity
import com.musicsportsapp.data.local.db.entity.RecentlyPlayedEntity

/**
 * Room database for local caching and offline user library data.
 */
@Database(
    entities = [
        RecentlyPlayedEntity::class,
        LikedSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        FollowedTeamEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun followedTeamDao(): FollowedTeamDao
}

