package com.musicsportsapp.features.sports.di

import com.musicsportsapp.features.sports.data.api.SportsApi
import com.musicsportsapp.features.sports.data.repository.SportsRepositoryImpl
import com.musicsportsapp.features.sports.domain.repository.SportsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SportsApiModule {

    @Provides
    @Singleton
    fun provideSportsApi(retrofit: Retrofit): SportsApi {
        return retrofit.create(SportsApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SportsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSportsRepository(
        sportsRepositoryImpl: SportsRepositoryImpl
    ): SportsRepository
}
