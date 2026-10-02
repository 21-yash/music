package com.musicsportsapp.di

import com.musicsportsapp.features.music.data.api.MusicApi
import com.musicsportsapp.features.music.data.repository.MusicRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MusicModule {

    @Provides
    @Singleton
    fun provideMusicApi(retrofit: Retrofit): MusicApi {
        return retrofit.create(MusicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMusicRepository(api: MusicApi): MusicRepository {
        return MusicRepository(api)
    }
}
