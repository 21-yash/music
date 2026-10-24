package com.synq.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.synq.data.local.db.dao.FollowedTeamDao
import com.synq.data.local.db.dao.LikedSongDao
import com.synq.data.local.db.dao.PlaylistDao
import com.synq.data.local.db.dao.RecentlyPlayedDao
import com.synq.data.local.db.entity.FollowedTeamEntity
import com.synq.data.local.db.entity.LikedSongEntity
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.data.local.db.entity.PlaylistSongEntity
import com.synq.data.local.db.entity.RecentlyPlayedEntity

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

