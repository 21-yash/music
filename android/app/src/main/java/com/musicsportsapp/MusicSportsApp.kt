package com.musicsportsapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import org.schabi.newpipe.extractor.NewPipe
import com.musicsportsapp.data.remote.api.NewPipeDownloader
import okhttp3.OkHttpClient

/**
 * Application class.
 *
 * @HiltAndroidApp triggers Hilt's code generation for the dependency
 * injection container. All Hilt-injected components will use this
 * as the parent component.
 */
@HiltAndroidApp
class MusicSportsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize NewPipeExtractor
        // We use a clean OkHttpClient without AuthInterceptor so we don't leak app tokens to YouTube
        val cleanClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
            
        NewPipe.init(NewPipeDownloader(cleanClient))
    }
}
