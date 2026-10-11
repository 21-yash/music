package com.musicsportsapp.di

import android.content.Context
import androidx.room.Room
import com.musicsportsapp.data.local.db.AppDatabase
import com.musicsportsapp.data.local.db.dao.FollowedTeamDao
import com.musicsportsapp.data.local.db.dao.LikedSongDao
import com.musicsportsapp.data.local.db.dao.PlaylistDao
import com.musicsportsapp.data.local.db.dao.RecentlyPlayedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room database and its DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "musicsportsapp.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRecentlyPlayedDao(db: AppDatabase): RecentlyPlayedDao = db.recentlyPlayedDao()

    @Provides
    fun provideLikedSongDao(db: AppDatabase): LikedSongDao = db.likedSongDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideFollowedTeamDao(db: AppDatabase): FollowedTeamDao = db.followedTeamDao()
}
