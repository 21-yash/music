package com.musicsportsapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.musicsportsapp.core.network.NetworkMonitor
import com.musicsportsapp.data.remote.api.NewPipeDownloader
import com.musicsportsapp.workers.SyncManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject

/**
 * Application class.
 *
 * @HiltAndroidApp triggers Hilt's code generation for the dependency
 * injection container. All Hilt-injected components will use this
 * as the parent component.
 */
@HiltAndroidApp
class MusicSportsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize NewPipeExtractor
        // We use a clean OkHttpClient without AuthInterceptor so we don't leak app tokens to YouTube
        val cleanClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
            
        NewPipe.init(NewPipeDownloader(cleanClient))

        // Start network monitoring & schedule periodic background library sync
        networkMonitor.startMonitoring()
        SyncManager.schedulePeriodicSync(this)
    }
}

