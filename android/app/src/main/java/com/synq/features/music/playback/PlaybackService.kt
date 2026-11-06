package com.synq.features.music.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    private var jamAwarePlayer: JamAwarePlayer? = null

    class JamAwarePlayer(
        player: Player,
        private val jamSessionManagerProvider: javax.inject.Provider<com.synq.features.music.jam.JamSessionManager>
    ) : ForwardingPlayer(player) {

        @Volatile
        var isCurrentSeekExternal: Boolean = false

        override fun seekTo(positionMs: Long) {
            seekTo(currentMediaItemIndex, positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            val isExternal = isCurrentSeekExternal
            isCurrentSeekExternal = false

            val jamManager = jamSessionManagerProvider.get()
            val isInJam = jamManager.jamState.value.isInJam
            val uiState = jamManager.stateStore.uiState.value
            val canPlayback = uiState.canControlPlayback || uiState.isHost

            val route = JamSeekRouter.route(
                isInJam = isInJam,
                isExternalController = isExternal,
                canPlayback = canPlayback
            )

            when (route) {
                JamSeekRouter.Route.PASS_THROUGH -> {
                    super.seekTo(mediaItemIndex, positionMs)
                }
                JamSeekRouter.Route.ROUTE_TO_JAM -> {
                    jamManager.seekTo(positionMs)
                }
                JamSeekRouter.Route.DROP -> {
                    // Drop seek from unpermitted external controller
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val cacheDataSourceFactory = audioCacheProvider.buildCacheDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                PlaybackBufferConfig.MIN_BUFFER_MS,
                PlaybackBufferConfig.MAX_BUFFER_MS,
                PlaybackBufferConfig.BUFFER_FOR_PLAYBACK_MS,
                PlaybackBufferConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
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
                    val action = PlaybackCommandRouter.mapJamCommand(playerCommand, canPlayback)

                    when (action) {
                        PlaybackCommandRouter.Action.TOGGLE -> {
                            jamManager.togglePlayPause()
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        PlaybackCommandRouter.Action.NEXT -> {
                            jamManager.nextTrack()
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        PlaybackCommandRouter.Action.PREV -> {
                            jamManager.previousTrack()
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        PlaybackCommandRouter.Action.SEEK_BACK -> {
                            val room = jamManager.stateStore.room.value
                            val current = room?.nowPlaying?.let {
                                jamManager.clockSync.expectedPosition(it, jamManager.clockSync.serverNow())
                            } ?: session.player.currentPosition
                            jamManager.seekTo(maxOf(0L, current - 10_000L))
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        PlaybackCommandRouter.Action.SEEK_FWD -> {
                            val room = jamManager.stateStore.room.value
                            val current = room?.nowPlaying?.let {
                                jamManager.clockSync.expectedPosition(it, jamManager.clockSync.serverNow())
                            } ?: session.player.currentPosition
                            val duration = room?.nowPlaying?.item?.song?.durationSeconds?.let { it * 1000L }
                                ?: session.player.duration.takeIf { it > 0 }
                                ?: Long.MAX_VALUE
                            jamManager.seekTo(minOf(duration, current + 10_000L))
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        PlaybackCommandRouter.Action.SEEK_IN_CURRENT -> {
                            jamAwarePlayer?.isCurrentSeekExternal = true
                            return androidx.media3.session.SessionResult.RESULT_SUCCESS
                        }
                        PlaybackCommandRouter.Action.SKIP_ONLY -> {
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        PlaybackCommandRouter.Action.STOP -> {
                            if (isNotification) {
                                // Notification dismiss in Jam: leave Jam and stop playback
                                jamManager.leaveJam()
                                pauseAllPlayersAndStopSelf()
                            }
                            return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                        }
                        null -> {}
                    }
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }

        val wrappedPlayer = JamAwarePlayer(player!!, jamSessionManagerProvider)
        jamAwarePlayer = wrappedPlayer

        mediaSession = MediaSession.Builder(this, wrappedPlayer)
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
        jamAwarePlayer = null
        player = null
        super.onDestroy()
    }
}
