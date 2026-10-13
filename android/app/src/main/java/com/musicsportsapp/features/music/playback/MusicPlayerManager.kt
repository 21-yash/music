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
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.data.repository.RecentlyPlayedRepository
import com.musicsportsapp.features.music.data.repository.MusicRepository
import com.musicsportsapp.features.music.domain.model.Artist
import com.musicsportsapp.features.music.domain.model.Lyrics
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.domain.model.StreamInfo
import com.musicsportsapp.features.music.util.SongDeduplicator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Binary repeat mode: false = OFF, true = ON (repeats current song)
    private val _isRepeatOne = MutableStateFlow(false)
    val isRepeatOne: StateFlow<Boolean> = _isRepeatOne.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    // Lyrics state
    private val _currentLyrics = MutableStateFlow<Lyrics?>(null)
    val currentLyrics: StateFlow<Lyrics?> = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    // Sleep timer stop hook
    var onTrackEndedListener: (() -> Unit)? = null

    // Session-based deduplication tracker
    private val sessionPlayedIds = mutableSetOf<String>()

    // Tracks IDs of songs automatically appended as recommendations
    private val recommendedSongIds = mutableSetOf<String>()

    // Personal queue isolation for Jam sessions
    private var stashedPersonalQueue: List<Song>? = null
    private var stashedPersonalIndex: Int? = null
    private var stashedSourcePlaylist: List<Song>? = null
    private var stashedNextSourceIndex: Int = 0

    // Artist Radio state tracking (ensures only this artist's songs play during radio)
    private var activeRadioArtist: String? = null
    private var stashedActiveRadioArtist: String? = null

    // Lazy paging state for large playlists
    private var sourcePlaylist: List<Song>? = null
    private var nextSourceIndex: Int = 0

    companion object {
        private const val MAX_INITIAL_QUEUE_SIZE = 100
        private const val QUEUE_PAGE_CHUNK = 50
    }

    private var positionTrackerJob: Job? = null
    private var isFetchingRecommendations = false

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

    // ─────────────────────────────────────────────────────────────
    // Playback actions
    // ─────────────────────────────────────────────────────────────

    fun playSong(song: Song) {
        activeRadioArtist = null
        sourcePlaylist = null
        nextSourceIndex = 0
        recommendedSongIds.clear()
        sessionPlayedIds.add(song.id)
        _queue.value = listOf(song)
        _currentIndex.value = 0
        loadAndPlay(song)

        // Instant recommendation pre-population (Spotify-style)
        prefetchAutoplayRecommendations(song)
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        activeRadioArtist = null
        recommendedSongIds.clear()
        val validIndex = startIndex.coerceIn(0, songs.size - 1)

        if (songs.size <= MAX_INITIAL_QUEUE_SIZE) {
            sourcePlaylist = null
            nextSourceIndex = 0
            _queue.value = songs
            _currentIndex.value = validIndex
            val target = songs[validIndex]
            sessionPlayedIds.add(target.id)
            loadAndPlay(target)

            if (songs.size - validIndex <= 2) {
                prefetchAutoplayRecommendations(target)
            }
        } else {
            // Safe windowing for large playlists to prevent memory spikes & UI lag
            sourcePlaylist = songs
            val windowBefore = 15
            val fromIndex = (validIndex - windowBefore).coerceAtLeast(0)
            val toIndex = (fromIndex + MAX_INITIAL_QUEUE_SIZE).coerceAtMost(songs.size)
            val windowedList = songs.subList(fromIndex, toIndex)
            val targetIndexInWindow = (validIndex - fromIndex).coerceIn(0, windowedList.size - 1)

            nextSourceIndex = toIndex
            _queue.value = windowedList
            _currentIndex.value = targetIndexInWindow
            val target = windowedList[targetIndexInWindow]
            sessionPlayedIds.add(target.id)
            loadAndPlay(target)
        }
    }

    fun startArtistRadio(artist: Artist) {
        sourcePlaylist = null
        nextSourceIndex = 0
        recommendedSongIds.clear()
        activeRadioArtist = artist.name

        val initialTracks = if (artist.topSongs.isNotEmpty()) {
            artist.topSongs.shuffled()
        } else {
            emptyList()
        }

        if (initialTracks.isNotEmpty()) {
            val seed = initialTracks.first()
            sessionPlayedIds.add(seed.id)
            _queue.value = initialTracks
            _currentIndex.value = 0
            loadAndPlay(seed)
        }

        // Expand radio queue with more songs exclusively by this artist via search
        scope.launch {
            try {
                val searchRes = repository.search(
                    query = artist.name,
                    provider = artist.providerId,
                    limit = 50
                )
                if (searchRes is AppResult.Success && activeRadioArtist == artist.name) {
                    val artistLower = artist.name.trim().lowercase()
                    val artistSongs = searchRes.data.songs.filter { song ->
                        song.artists.any { it.name.trim().lowercase().contains(artistLower) } ||
                        song.primaryArtistName.trim().lowercase().contains(artistLower)
                    }

                    val current = _queue.value
                    val unique = SongDeduplicator.filterDuplicateSongs(
                        incoming = artistSongs,
                        existing = current,
                        sessionHistoryIds = sessionPlayedIds
                    )

                    if (unique.isNotEmpty() && activeRadioArtist == artist.name) {
                        if (current.isEmpty()) {
                            val shuffled = unique.shuffled()
                            _queue.value = shuffled
                            _currentIndex.value = 0
                            loadAndPlay(shuffled.first())
                        } else {
                            _queue.update { it + unique.shuffled() }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadAndPlay(song: Song) {
        _playbackState.update { 
            it.copy(
                currentSong = song, 
                error = null, 
                isPlaying = true,
                playbackPositionMs = 0L,
                bufferedPositionMs = 0L
            ) 
        }

        // Fetch lyrics
        fetchLyrics(song)

        scope.launch {
            // Record recently played history in Room
            try {
                recentlyPlayedRepository.recordPlay(song)
            } catch (_: Exception) {}

            try {
                // 1. Resolve stream via backend
                val streamResult = repository.resolveStream(song.id, song.providerId)
                if (streamResult !is AppResult.Success) {
                    _playbackState.update { it.copy(error = "Failed to fetch stream info", isPlaying = false) }
                    return@launch
                }

                // 2. Extract media URI
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

                startPositionTracking()
            } catch (e: Exception) {
                _playbackState.update { it.copy(error = e.message ?: "Unknown playback error", isPlaying = false) }
            }
        }
    }

    private fun fetchLyrics(song: Song) {
        scope.launch {
            _isLyricsLoading.value = true
            _currentLyrics.value = null
            try {
                val res = repository.getLyrics(
                    title = song.title,
                    artist = song.primaryArtistName,
                    duration = song.durationSeconds,
                    songId = song.id,
                    provider = song.providerId
                )
                if (res is AppResult.Success) {
                    _currentLyrics.value = res.data
                } else {
                    _currentLyrics.value = null
                }
            } catch (_: Exception) {
                _currentLyrics.value = null
            } finally {
                _isLyricsLoading.value = false
            }
        }
    }

    private fun prefetchAutoplayRecommendations(seedSong: Song) {
        if (activeRadioArtist != null) return // Never add third-party songs during Artist Radio
        if (isFetchingRecommendations) return
        isFetchingRecommendations = true
        scope.launch {
            try {
                val res = repository.getRelatedSongs(
                    songId = seedSong.id,
                    provider = seedSong.providerId,
                    title = seedSong.title,
                    artist = seedSong.primaryArtistName,
                    limit = 10
                )
                if (res is AppResult.Success) {
                    val currentList = _queue.value
                    // Eliminate duplicates, covers, and remixes
                    val unique = SongDeduplicator.filterDuplicateSongs(
                        incoming = res.data,
                        existing = currentList,
                        sessionHistoryIds = sessionPlayedIds
                    )
                    if (unique.isNotEmpty()) {
                        recommendedSongIds.addAll(unique.map { it.id })
                        _queue.update { it + unique }
                    }
                }
            } catch (_: Exception) {
            } finally {
                isFetchingRecommendations = false
            }
        }
    }

    private suspend fun extractYouTubeStream(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = NewPipe.getService(0).getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()
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
                stopPositionTracking()
            } else {
                it.play()
                startPositionTracking()
            }
        }
    }

    fun pause() {
        controller?.pause()
        stopPositionTracking()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _playbackState.update { it.copy(playbackPositionMs = positionMs) }
    }

    fun seekToRatio(ratio: Float) {
        val duration = controller?.duration?.takeIf { it > 0 }
            ?: (playbackState.value.currentSong?.durationSeconds?.toLong()?.times(1000L) ?: 0L)
        if (duration > 0) {
            val targetMs = (duration * ratio.coerceIn(0f, 1f)).toLong()
            seekTo(targetMs)
        }
    }

    fun skipToNext() {
        if (_isRepeatOne.value) {
            seekTo(0)
            controller?.play()
            return
        }

        val q = _queue.value
        val nextIdx = _currentIndex.value + 1
        val src = sourcePlaylist

        if (nextIdx in q.indices) {
            _currentIndex.value = nextIdx
            loadAndPlay(q[nextIdx])

            // If there are more songs in a large playlist source, append the next chunk
            if (src != null && nextSourceIndex < src.size) {
                if (q.size - nextIdx <= 5) {
                    val chunkEnd = (nextSourceIndex + QUEUE_PAGE_CHUNK).coerceAtMost(src.size)
                    val moreSongs = src.subList(nextSourceIndex, chunkEnd)
                    nextSourceIndex = chunkEnd
                    _queue.update { it + moreSongs }
                    if (nextSourceIndex >= src.size) {
                        sourcePlaylist = null
                    }
                }
            } else if (activeRadioArtist == null && q.size - nextIdx <= 2) {
                prefetchAutoplayRecommendations(q[nextIdx])
            }
        } else {
            // Queue ended:
            if (activeRadioArtist != null) {
                // Radio should only play songs of the artist: loop & reshuffle artist tracks
                if (q.isNotEmpty()) {
                    val reshuffled = q.shuffled()
                    _queue.value = reshuffled
                    _currentIndex.value = 0
                    loadAndPlay(reshuffled.first())
                    return
                }
            }

            // Check if more songs remain in the source playlist
            if (src != null && nextSourceIndex < src.size) {
                val chunkEnd = (nextSourceIndex + QUEUE_PAGE_CHUNK).coerceAtMost(src.size)
                val moreSongs = src.subList(nextSourceIndex, chunkEnd)
                nextSourceIndex = chunkEnd
                _queue.update { it + moreSongs }
                if (nextSourceIndex >= src.size) {
                    sourcePlaylist = null
                }
                if (moreSongs.isNotEmpty()) {
                    _currentIndex.value = nextIdx
                    loadAndPlay(moreSongs.first())
                    return
                }
            }

            // Playlist completely ended, fall back to autoplay recommendations
            val curr = playbackState.value.currentSong
            if (curr != null && activeRadioArtist == null) {
                prefetchAutoplayRecommendations(curr)
            }
        }
    }

    fun skipToPrevious() {
        val currentPos = controller?.currentPosition ?: 0L
        if (currentPos > 3000L) {
            seekTo(0)
            return
        }

        val prevIdx = _currentIndex.value - 1
        val q = _queue.value
        if (prevIdx >= 0 && prevIdx in q.indices) {
            _currentIndex.value = prevIdx
            loadAndPlay(q[prevIdx])
        } else {
            seekTo(0)
        }
    }

    fun playQueueIndex(index: Int) {
        val q = _queue.value
        if (index in q.indices) {
            _currentIndex.value = index
            loadAndPlay(q[index])

            val src = sourcePlaylist
            if (src != null && nextSourceIndex < src.size && q.size - index <= 5) {
                val chunkEnd = (nextSourceIndex + QUEUE_PAGE_CHUNK).coerceAtMost(src.size)
                val moreSongs = src.subList(nextSourceIndex, chunkEnd)
                nextSourceIndex = chunkEnd
                _queue.update { it + moreSongs }
                if (nextSourceIndex >= src.size) {
                    sourcePlaylist = null
                }
            } else if (activeRadioArtist == null && q.size - index <= 2) {
                prefetchAutoplayRecommendations(q[index])
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Queue modifications
    // ─────────────────────────────────────────────────────────────

    fun addToQueue(song: Song) {
        if (_queue.value.isEmpty() || playbackState.value.currentSong == null) {
            playSong(song)
            return
        }
        recommendedSongIds.remove(song.id)
        _queue.update { current ->
            val filtered = current.filterNot { SongDeduplicator.areSongsEquivalent(it, song) }.toMutableList()
            val currIdx = _currentIndex.value
            // Find the first recommended song after currentIndex so user queue plays first
            val firstRecIdx = filtered.indices
                .filter { it > currIdx }
                .firstOrNull { filtered[it].id in recommendedSongIds }

            val insertAt = firstRecIdx ?: filtered.size
            filtered.add(insertAt, song)
            filtered
        }
    }

    fun playNext(song: Song) {
        if (_queue.value.isEmpty() || playbackState.value.currentSong == null) {
            playSong(song)
            return
        }
        recommendedSongIds.remove(song.id)
        _queue.update { current ->
            val filtered = current.filterNot { SongDeduplicator.areSongsEquivalent(it, song) }.toMutableList()
            val insertAt = (_currentIndex.value + 1).coerceAtMost(filtered.size)
            filtered.add(insertAt, song)
            filtered
        }
    }

    fun removeQueueItem(index: Int) {
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _queue.value = list
        if (index < _currentIndex.value) {
            _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
        } else if (index == _currentIndex.value) {
            if (_currentIndex.value in list.indices) {
                loadAndPlay(list[_currentIndex.value])
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _queue.value = list

        val current = _currentIndex.value
        if (current == fromIndex) {
            _currentIndex.value = toIndex
        } else if (fromIndex < current && toIndex >= current) {
            _currentIndex.value = current - 1
        } else if (fromIndex > current && toIndex <= current) {
            _currentIndex.value = current + 1
        }
    }

    // Binary repeat mode: OFF vs ON
    fun toggleRepeat() {
        _isRepeatOne.update { !it }
    }

    fun toggleShuffle() {
        _isShuffle.update { !it }
    }

    // ─────────────────────────────────────────────────────────────
    // Queue Isolation for Jam Sessions
    // ─────────────────────────────────────────────────────────────

    fun stashPersonalQueue() {
        stashedPersonalQueue = _queue.value
        stashedPersonalIndex = _currentIndex.value
        stashedSourcePlaylist = sourcePlaylist
        stashedNextSourceIndex = nextSourceIndex
        stashedActiveRadioArtist = activeRadioArtist
        activeRadioArtist = null
    }

    fun restorePersonalQueue() {
        stashedPersonalQueue?.let { stashed ->
            _queue.value = stashed
            _currentIndex.value = stashedPersonalIndex?.coerceIn(0, (stashed.size - 1).coerceAtLeast(0)) ?: 0
            sourcePlaylist = stashedSourcePlaylist
            nextSourceIndex = stashedNextSourceIndex
            activeRadioArtist = stashedActiveRadioArtist
            stashedPersonalQueue = null
            stashedPersonalIndex = null
            stashedSourcePlaylist = null
            stashedNextSourceIndex = 0
            stashedActiveRadioArtist = null
        }
    }

    fun setJamQueue(jamQueue: List<Song>, targetSong: Song? = null) {
        _queue.value = jamQueue
        if (targetSong != null) {
            val idx = jamQueue.indexOfFirst { it.id == targetSong.id }
            if (idx >= 0) {
                _currentIndex.value = idx
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Position Tracker & ExoPlayer Listener
    // ─────────────────────────────────────────────────────────────

    private fun startPositionTracking() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    val pos = c.currentPosition
                    val dur = c.duration.takeIf { it > 0 }
                        ?: (playbackState.value.currentSong?.durationSeconds?.toLong()?.times(1000L) ?: 0L)
                    val buf = c.bufferedPosition
                    _playbackState.update { 
                        it.copy(
                            playbackPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buf,
                            isPlaying = true
                        ) 
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionTracking() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }

    fun release() {
        stopPositionTracking()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller?.removeListener(playerListener)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionTracking()
            } else {
                stopPositionTracking()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                val listener = onTrackEndedListener
                if (listener != null) {
                    onTrackEndedListener = null
                    listener.invoke()
                    return
                }
                if (_isRepeatOne.value) {
                    controller?.seekTo(0)
                    controller?.play()
                } else {
                    skipToNext()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playbackState.update { 
                it.copy(
                    isPlaying = false, 
                    error = error.message ?: "Unknown playback error"
                ) 
            }
            stopPositionTracking()
        }
    }
}
