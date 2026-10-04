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
import com.musicsportsapp.features.music.domain.model.StreamInfo
import com.musicsportsapp.features.music.data.repository.MusicRepository
import com.musicsportsapp.core.domain.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        _playbackState.update { it.copy(currentSong = song, error = null, isPlaying = true) } // Optimistic

        scope.launch {
            try {
                // 1. Ask backend to resolve the stream
                val streamResult = repository.resolveStream(song.id, song.providerId)
                if (streamResult !is AppResult.Success) {
                    _playbackState.update { it.copy(error = "Failed to fetch stream info", isPlaying = false) }
                    return@launch
                }

                // 2. Extract final media URI based on provider type
                val finalUri = when (val info = streamResult.data) {
                    is StreamInfo.JioSaavn -> info.url
                    is StreamInfo.YouTube -> extractYouTubeStream(info.videoId)
                    is StreamInfo.None -> null
                }

                if (finalUri.isNullOrBlank()) {
                    _playbackState.update { it.copy(error = "Stream not available for this song", isPlaying = false) }
                    return@launch
                }

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.primaryArtistName)
                    .setArtworkUri(android.net.Uri.parse(song.imageUrl ?: ""))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(finalUri)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                controller?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }
            } catch (e: Exception) {
                _playbackState.update { it.copy(error = e.message ?: "Unknown playback error", isPlaying = false) }
            }
        }
    }

    private suspend fun extractYouTubeStream(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = NewPipe.getService(0).getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()
            
            // Get the best audio-only stream
            val audioStreams = extractor.audioStreams
            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
            
            return@withContext bestAudio?.content ?: audioStreams.firstOrNull()?.content
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
