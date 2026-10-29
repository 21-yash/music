package com.synq.features.music.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.synq.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var audioCacheProvider: AudioCacheProvider

    @Inject
    lateinit var jamSessionManagerProvider: javax.inject.Provider<com.synq.features.music.jam.JamSessionManager>

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val cacheDataSourceFactory = audioCacheProvider.buildCacheDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val callback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
                val updatedItems = mediaItems.map { item ->
                    val customKey = item.localConfiguration?.customCacheKey ?: item.mediaId
                    item.buildUpon()
                        .setCustomCacheKey(customKey)
                        .build()
                }.toMutableList()
                return com.google.common.util.concurrent.Futures.immediateFuture(updatedItems)
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                val jamManager = jamSessionManagerProvider.get()
                if (jamManager.jamState.value.isInJam) {
                    when (playerCommand) {
                        androidx.media3.common.Player.COMMAND_PLAY_PAUSE -> {
                            jamManager.togglePlayPause()
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT,
                        androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            jamManager.nextTrack()
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS,
                        androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            jamManager.previousTrack()
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                    }
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(callback)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_stat_synq)
            }
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val isInJam = try {
            jamSessionManagerProvider.get().jamState.value.isInJam
        } catch (_: Exception) {
            false
        }
        if (!isInJam && (player == null || !player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
