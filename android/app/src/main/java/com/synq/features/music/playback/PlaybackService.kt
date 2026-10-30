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
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
                val isNotification = session.isMediaNotificationController(controller)
                val isExternal = isNotification || controller.packageName != packageName

                val jamManager = jamSessionManagerProvider.get()
                val isInJam = jamManager.jamState.value.isInJam

                if (PlaybackCommandRouter.shouldIntercept(isExternal, isInJam, playerCommand)) {
                    val uiState = jamManager.stateStore.uiState.value
                    val canPlayback = uiState.canControlPlayback || uiState.isHost

                    when (playerCommand) {
                        androidx.media3.common.Player.COMMAND_PLAY_PAUSE -> {
                            if (canPlayback) {
                                jamManager.togglePlayPause()
                            }
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT,
                        androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            if (canPlayback) {
                                jamManager.nextTrack()
                            }
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS,
                        androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            if (canPlayback) {
                                jamManager.previousTrack()
                            }
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_BACK -> {
                            if (canPlayback) {
                                val room = jamManager.stateStore.room.value
                                val current = room?.nowPlaying?.let {
                                    jamManager.clockSync.expectedPosition(it, jamManager.clockSync.serverNow())
                                } ?: session.player.currentPosition
                                jamManager.seekTo(maxOf(0L, current - 10_000L))
                            }
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_FORWARD -> {
                            if (canPlayback) {
                                val room = jamManager.stateStore.room.value
                                val current = room?.nowPlaying?.let {
                                    jamManager.clockSync.expectedPosition(it, jamManager.clockSync.serverNow())
                                } ?: session.player.currentPosition
                                val duration = room?.nowPlaying?.item?.song?.durationSeconds?.let { it * 1000L }
                                    ?: session.player.duration.takeIf { it > 0 }
                                    ?: Long.MAX_VALUE
                                jamManager.seekTo(minOf(duration, current + 10_000L))
                            }
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        androidx.media3.common.Player.COMMAND_STOP -> {
                            if (isNotification) {
                                // Notification dismiss in Jam: leave Jam and stop playback
                                jamManager.leaveJam()
                                pauseAllPlayersAndStopSelf()
                            }
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
        try {
            val jamManager = jamSessionManagerProvider.get()
            if (jamManager.jamState.value.isInJam) {
                jamManager.leaveJam()
            }
        } catch (_: Exception) {}
        pauseAllPlayersAndStopSelf()
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
