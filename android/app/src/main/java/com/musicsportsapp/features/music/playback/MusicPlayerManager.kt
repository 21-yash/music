package com.musicsportsapp.features.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musicsportsapp.BuildConfig
import com.musicsportsapp.features.music.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentSong: Song? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playSong(song: Song) {
        currentSong = song
        _playbackState.update { it.copy(currentSong = song, error = null) }

        val streamUrl = "${BuildConfig.API_BASE_URL}music/stream?id=${song.id}&quality=high"

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.primaryArtistName)
            .setArtworkUri(android.net.Uri.parse(song.imageUrl ?: ""))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl)
            .setMediaMetadata(mediaMetadata)
            .build()

        controller?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller?.removeListener(playerListener)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _playbackState.update { it.copy(isPlaying = false) }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playbackState.update { 
                it.copy(
                    isPlaying = false, 
                    error = error.message ?: "Unknown playback error"
                ) 
            }
        }

        // Ideally we'd set up a coroutine to poll position when playing
        // but for simplicity we rely on manual updates or basic state changes here.
    }
}
