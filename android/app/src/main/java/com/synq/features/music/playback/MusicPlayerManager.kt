package com.synq.features.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.synq.core.domain.AppError
import com.synq.core.domain.AppResult
import com.synq.data.repository.RecentlyPlayedRepository
import com.synq.features.music.data.repository.MusicRepository
import com.synq.features.music.domain.model.AlbumRef
import com.synq.features.music.domain.model.Artist
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.Lyrics
import com.synq.features.music.domain.model.Song
import com.synq.features.music.domain.model.StreamInfo
import com.synq.features.music.util.SongDeduplicator
import com.synq.features.music.jam.JamPlayerBridge
import com.synq.features.music.sleeptimer.SleepTimerPlayerBridge
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

interface JamPlaybackInterceptor {
    fun onPlaySong(song: Song): Boolean
    fun onPlayNext(song: Song): Boolean
    fun onAddToQueue(song: Song): Boolean
    fun onPlayQueue(songs: List<Song>, startIndex: Int): Boolean
    fun onStartArtistRadio(artist: Artist): Boolean

    fun onTogglePlayPause(): Boolean = false
    fun onPlay(): Boolean = false
    fun onPause(): Boolean = false
    fun onSeekTo(positionMs: Long): Boolean = false
    fun onSkipToNext(): Boolean = false
    fun onSkipToPrevious(): Boolean = false
    fun onPlayQueueIndex(index: Int): Boolean = false
    fun onRemoveFromQueue(index: Int): Boolean = false
    fun onReorderQueue(fromIndex: Int, toIndex: Int): Boolean = false
    fun onToggleShuffle(): Boolean = false
    fun onToggleRepeat(): Boolean = false
    fun onTrackEnded(): Boolean = false
    fun onPlaybackStateChanged(state: Int) {}
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {}
    fun onPlayerError(error: androidx.media3.common.PlaybackException): Boolean = false
}

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val audioCacheProvider: AudioCacheProvider,
    private val streamCacheStorage: StreamCacheStorage,
    private val networkMonitor: com.synq.core.network.NetworkMonitor,
) : JamPlayerBridge, SleepTimerPlayerBridge {
    override var jamInterceptor: JamPlaybackInterceptor? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val playbackState: StateFlow<PlaybackState> get() = _playbackState.asStateFlow()
    private val _playbackState = MutableStateFlow(PlaybackState())

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
    override var onTrackEndedListener: (() -> Unit)? = null

    // Session-based deduplication tracker
    private val sessionPlayedIds = mutableSetOf<String>()

    // Tracks IDs of songs automatically appended as recommendations in personal queue
    private val _recommendedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val recommendedSongIds: StateFlow<Set<String>> = _recommendedSongIds.asStateFlow()

    // Mirror queue QIDs and Jam recommendations by QID for Jam sessions
    private val _jamQueueQids = MutableStateFlow<List<String>>(emptyList())
    val jamQueueQids: StateFlow<List<String>> = _jamQueueQids.asStateFlow()

    private val _jamRecommendedQids = MutableStateFlow<Set<String>>(emptySet())
    val jamRecommendedQids: StateFlow<Set<String>> = _jamRecommendedQids.asStateFlow()

    fun isItemRecommended(qid: String): Boolean = qid in _jamRecommendedQids.value

    // Personal queue isolation for Jam sessions
    private val personalQueueStash = PersonalQueueStash()

    // Artist Radio state tracking (ensures only this artist's songs play during radio)
    private var activeRadioArtist: String? = null

    // Lazy paging state for large playlists
    private var sourcePlaylist: List<Song>? = null
    private var nextSourceIndex: Int = 0

    // Tracks original unshuffled queue for restoring sequence when shuffle is disabled
    private var unshuffledQueue: List<Song>? = null

    companion object {
        private const val MAX_INITIAL_QUEUE_SIZE = 100
        private const val QUEUE_PAGE_CHUNK = 50
    }

    private var positionTrackerJob: Job? = null
    private var isFetchingRecommendations = false

    init {
        initializeController()
    }

    private val reResolveGuard = ReResolveGuard()
    private var playingFromFullCache: Boolean = false
    private val autoSkipPolicy = AutoSkipPolicy()

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                controller = controllerFuture?.get()
                controller?.let { ctrl ->
                    ctrl.addListener(playerListener)
                    syncStateFromController(ctrl)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun syncStateFromController(ctrl: MediaController) {
        val currentItem = ctrl.currentMediaItem
        val isPlaying = ctrl.isPlaying
        val position = ctrl.currentPosition
        val duration = ctrl.duration.takeIf { it > 0 } ?: 0L

        if (currentItem != null) {
            val songId = currentItem.mediaId
            val title = currentItem.mediaMetadata.title?.toString() ?: ""
            val artistName = currentItem.mediaMetadata.artist?.toString() ?: ""
            val artworkUri = currentItem.mediaMetadata.artworkUri?.toString()

            val existingSong = _playbackState.value.currentSong
            val songToSet = if (existingSong != null && existingSong.id == songId) {
                existingSong
            } else {
                Song(
                    id = songId,
                    title = title,
                    artists = listOf(ArtistCredit(id = "", name = artistName)),
                    album = AlbumRef(id = "", title = currentItem.mediaMetadata.albumTitle?.toString() ?: ""),
                    durationSeconds = (duration / 1000).toInt(),
                    imageUrl = artworkUri,
                    year = "",
                    language = "",
                    hasLyrics = false,
                    playCount = 0,
                    label = "",
                    streamRef = "",
                    providerId = ""
                )
            }

            if (_queue.value.isEmpty()) {
                _queue.value = listOf(songToSet)
                _currentIndex.value = 0
            }

            _playbackState.update {
                it.copy(
                    currentSong = songToSet,
                    isPlaying = isPlaying,
                    playbackPositionMs = position,
                    durationMs = duration
                )
            }
        } else {
            _playbackState.update {
                it.copy(
                    isPlaying = isPlaying,
                    playbackPositionMs = position,
                    durationMs = duration
                )
            }
        }

        if (isPlaying) {
            startPositionTracking()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Playback actions
    // ─────────────────────────────────────────────────────────────

    fun playSong(song: Song) {
        if (jamInterceptor?.onPlaySong(song) == true) return
        activeRadioArtist = null
        sourcePlaylist = null
        nextSourceIndex = 0
        unshuffledQueue = null
        _recommendedSongIds.value = emptySet()
        sessionPlayedIds.add(song.id)
        _queue.value = listOf(song)
        _currentIndex.value = 0
        loadAndPlay(song)

        // Instant recommendation pre-population (Spotify-style)
        prefetchAutoplayRecommendations(song)
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        if (jamInterceptor?.onPlayQueue(songs, startIndex) == true) return
        activeRadioArtist = null
        _recommendedSongIds.value = emptySet()
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        unshuffledQueue = songs

        val finalQueue: List<Song>
        val finalIndex: Int

        if (_isShuffle.value && songs.size > 1) {
            val target = songs[validIndex]
            val others = songs.filterIndexed { index, _ -> index != validIndex }.shuffled()
            finalQueue = listOf(target) + others
            finalIndex = 0
        } else {
            finalQueue = songs
            finalIndex = validIndex
        }

        if (finalQueue.size <= MAX_INITIAL_QUEUE_SIZE) {
            sourcePlaylist = null
            nextSourceIndex = 0
            _queue.value = finalQueue
            _currentIndex.value = finalIndex
            val target = finalQueue[finalIndex]
            sessionPlayedIds.add(target.id)
            loadAndPlay(target)

            if (finalQueue.size - finalIndex <= 2) {
                prefetchAutoplayRecommendations(target)
            }
        } else {
            // Safe windowing for large playlists to prevent memory spikes & UI lag
            sourcePlaylist = finalQueue
            val windowBefore = 15
            val fromIndex = (finalIndex - windowBefore).coerceAtLeast(0)
            val toIndex = (fromIndex + MAX_INITIAL_QUEUE_SIZE).coerceAtMost(finalQueue.size)
            val windowedList = finalQueue.subList(fromIndex, toIndex)
            val targetIndexInWindow = (finalIndex - fromIndex).coerceIn(0, windowedList.size - 1)

            nextSourceIndex = toIndex
            _queue.value = windowedList
            _currentIndex.value = targetIndexInWindow
            val target = windowedList[targetIndexInWindow]
            sessionPlayedIds.add(target.id)
            loadAndPlay(target)
        }
    }

    fun startArtistRadio(artist: Artist) {
        if (jamInterceptor?.onStartArtistRadio(artist) == true) return
        sourcePlaylist = null
        nextSourceIndex = 0
        _recommendedSongIds.value = emptySet()
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

    private fun loadAndPlay(song: Song, initialPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        reResolveGuard.onSongStarted(song.id)

        // If this song was part of recommended songs, push it above the separator (personal queue only)
        if (jamInterceptor == null && song.id in _recommendedSongIds.value) {
            _recommendedSongIds.update { recs ->
                val currentIdx = _currentIndex.value
                val q = _queue.value
                val idsToRemove = q.subList(0, (currentIdx + 1).coerceAtMost(q.size))
                    .map { it.id }
                    .toSet()
                recs - idsToRemove
            }
        }

        // Immediately stop existing playback so previous song never keeps playing
        controller?.stop()
        stopPositionTracking()

        _playbackState.update { 
            it.copy(
                currentSong = song, 
                error = null, 
                isPlaying = playWhenReady,
                playbackPositionMs = initialPositionMs,
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

            // 1. Check if song is already cached in local SimpleCache (Task F16 & G4: verify FULL cache)
            val isCached = audioCacheProvider.isFullyCached(song.id)
            val cachedStreamUri = streamCacheStorage.getStreamUri(song.id)
            val sourceChoice = PlaybackSourcePolicy.chooseSource(isCached, cachedStreamUri)

            if (sourceChoice != PlaybackSourcePolicy.SourceType.NETWORK) {
                playingFromFullCache = true
                val streamUri = if (sourceChoice == PlaybackSourcePolicy.SourceType.CACHE_WITH_SAVED_URI) {
                    cachedStreamUri!!
                } else {
                    "https://audio.cache/${song.id}.mp4"
                }
                playMediaUri(song, streamUri, initialPositionMs, playWhenReady)
                return@launch
            }

            playingFromFullCache = false

            // 2. If not cached, check if offline
            if (!networkMonitor.isOnline.value) {
                notifyOfflineUnavailable()
                return@launch
            }

            try {
                // 3. Resolve stream via backend with retry policy
                var finalUri: String? = null
                var lastError: AppError? = null
                var attempt = 1

                while (attempt <= 3) {
                    if (!networkMonitor.isOnline.value) {
                        notifyOfflineUnavailable()
                        return@launch
                    }

                    val streamResult = repository.resolveStream(song.id, song.providerId)
                    if (streamResult is AppResult.Success) {
                        when (val info = streamResult.data) {
                            is StreamInfo.JioSaavn -> {
                                finalUri = info.url
                                break
                            }
                            is StreamInfo.YouTube -> {
                                val ytUri = extractYouTubeStream(info.videoId)
                                if (ytUri.isNullOrBlank()) {
                                    val err = AppError.Server(StreamRetryPolicy.CODE_YOUTUBE_EXTRACTION_FAILED, "YouTube extraction failed")
                                    lastError = err
                                    val decision = StreamRetryPolicy.decide(err, attempt)
                                    if (decision is StreamRetryDecision.Retry) {
                                        attempt++
                                        delay(decision.delayMs)
                                        continue
                                    } else {
                                        break
                                    }
                                } else {
                                    finalUri = ytUri
                                    break
                                }
                            }
                            is StreamInfo.None -> {
                                val err = AppError.Server("STREAM_UNAVAILABLE", "No playable stream")
                                lastError = err
                                break
                            }
                        }
                    } else if (streamResult is AppResult.Error) {
                        lastError = streamResult.error
                        val decision = StreamRetryPolicy.decide(streamResult.error, attempt)
                        if (decision is StreamRetryDecision.Retry) {
                            attempt++
                            delay(decision.delayMs)
                            continue
                        } else {
                            break
                        }
                    }
                }

                if (finalUri.isNullOrBlank()) {
                    if (!networkMonitor.isOnline.value) {
                        notifyOfflineUnavailable()
                    } else {
                        android.util.Log.w(
                            "StreamResolve",
                            "Stream resolution failed: errorClass=${lastError?.javaClass?.simpleName} code=${lastError?.code} message=${lastError?.message} songId=${song.id} provider=${song.providerId}"
                        )
                        val userMessage = lastError?.let { StreamRetryPolicy.userFriendlyMessage(it) } ?: "This song can't be played right now"
                        controller?.stop()
                        controller?.clearMediaItems()
                        stopPositionTracking()
                        _playbackState.update { it.copy(error = userMessage, isPlaying = false) }
                        handleUnrecoverablePlaybackError(song, userMessage)
                    }
                    return@launch
                }

                // 5. Store resolved stream URI for future offline playback from SimpleCache
                streamCacheStorage.saveStreamUri(song.id, finalUri)

                // 6. Play media item
                playMediaUri(song, finalUri, initialPositionMs, playWhenReady)

            } catch (e: Exception) {
                if (!networkMonitor.isOnline.value) {
                    notifyOfflineUnavailable()
                } else {
                    controller?.stop()
                    controller?.clearMediaItems()
                    stopPositionTracking()
                    _playbackState.update { it.copy(error = e.message ?: "Unknown playback error", isPlaying = false) }
                }
            }
        }
    }

    private suspend fun notifyOfflineUnavailable() {
        controller?.stop()
        controller?.clearMediaItems()
        stopPositionTracking()
        val errorMsg = "This song is not available for offline playback"
        _playbackState.update { 
            it.copy(
                error = errorMsg, 
                isPlaying = false
            ) 
        }
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun playMediaUri(song: Song, uri: String, initialPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.primaryArtistName)
            .setArtworkUri(android.net.Uri.parse(song.imageUrl ?: ""))
            .build()

        val cacheKey = if (audioCacheProvider.hasSpansForKey(song.id)) {
            song.id
        } else if (audioCacheProvider.hasSpansForKey(uri)) {
            uri
        } else {
            song.id
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setCustomCacheKey(cacheKey)
            .setUri(uri)
            .setMediaMetadata(mediaMetadata)
            .build()

        controller?.apply {
            setMediaItem(mediaItem)
            if (initialPositionMs > 0L) {
                seekTo(initialPositionMs)
            }
            prepare()
            if (playWhenReady) {
                play()
            } else {
                pause()
            }
        }

        if (playWhenReady) {
            startPositionTracking()
        }
    }

    private fun fetchLyrics(song: Song) {
        scope.launch {
            _isLyricsLoading.value = true
            _currentLyrics.value = null
            try {
                val allArtists = if (song.artists.isNotEmpty()) {
                    song.artists.joinToString(", ") { it.name }
                } else {
                    song.primaryArtistName
                }
                val res = repository.getLyrics(
                    title = song.title,
                    artist = allArtists,
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
        if (jamInterceptor != null) return // Server owns recommendations during Jam
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
                        val wasAtEnd = _currentIndex.value >= currentList.size - 1 && !_playbackState.value.isPlaying
                        _recommendedSongIds.update { it + unique.map { s -> s.id } }
                        _queue.update { it + unique }
                        if (wasAtEnd && currentList.isNotEmpty()) {
                            val nextIdx = _currentIndex.value + 1
                            if (nextIdx in _queue.value.indices) {
                                _currentIndex.value = nextIdx
                                loadAndPlay(_queue.value[nextIdx])
                            }
                        }
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
            android.util.Log.w("NewPipeExtractor", "Failed to extract YouTube stream: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun togglePlayPause() {
        if (jamInterceptor?.onTogglePlayPause() == true) return
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

    fun play() {
        if (jamInterceptor?.onPlay() == true) return
        controller?.let {
            if (!it.isPlaying) {
                it.play()
                startPositionTracking()
            }
        }
    }

    override fun pause() {
        if (jamInterceptor?.onPause() == true) return
        controller?.pause()
        stopPositionTracking()
    }

    override fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        stopPositionTracking()
        _playbackState.update {
            it.copy(
                isPlaying = false,
                currentSong = null,
                playbackPositionMs = 0L,
                bufferedPositionMs = 0L,
                error = null
            )
        }
    }

    fun seekTo(positionMs: Long) {
        if (jamInterceptor?.onSeekTo(positionMs) == true) return
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

    fun skipToNext(isAutoSkip: Boolean = false) {
        if (jamInterceptor?.onSkipToNext() == true) return
        if (_isRepeatOne.value) {
            seekTo(0)
            controller?.play()
            return
        }

        val q = _queue.value
        val isOffline = !networkMonitor.isOnline.value

        if (isOffline) {
            // 1. Look for next cached song in current queue (forward or wrap-around unless auto-skipping)
            val nextCachedIdx = ((_currentIndex.value + 1) until q.size)
                .firstOrNull { audioCacheProvider.isSongCached(q[it].id) }
                ?: if (!isAutoSkip) {
                    (0 until _currentIndex.value)
                        .firstOrNull { audioCacheProvider.isSongCached(q[it].id) }
                } else null

            if (nextCachedIdx != null && nextCachedIdx != _currentIndex.value) {
                _currentIndex.value = nextCachedIdx
                loadAndPlay(q[nextCachedIdx])
                return
            }

            // 2. If no other cached song in current queue, look for any cached song in recent history
            scope.launch {
                val currentSongId = _playbackState.value.currentSong?.id
                val recentCached = try {
                    recentlyPlayedRepository.getRecentSongsList()
                        .firstOrNull { it.id != currentSongId && audioCacheProvider.isSongCached(it.id) }
                } catch (_: Exception) {
                    null
                }

                if (recentCached != null) {
                    _queue.update { current ->
                        val withoutDup = current.filterNot { it.id == recentCached.id }
                        withoutDup + recentCached
                    }
                    _currentIndex.value = _queue.value.size - 1
                    loadAndPlay(recentCached)
                } else {
                    notifyOfflineUnavailable()
                }
            }
            return
        }

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
            if (!isAutoSkip && activeRadioArtist != null) {
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
        if (jamInterceptor?.onSkipToPrevious() == true) return
        val currentPos = controller?.currentPosition ?: 0L
        if (currentPos > 3000L) {
            seekTo(0)
            return
        }

        val q = _queue.value
        val isOffline = !networkMonitor.isOnline.value

        if (isOffline) {
            val prevCachedIdx = ((_currentIndex.value - 1) downTo 0)
                .firstOrNull { audioCacheProvider.isSongCached(q[it].id) }
                ?: (q.size - 1 downTo _currentIndex.value + 1)
                    .firstOrNull { audioCacheProvider.isSongCached(q[it].id) }

            if (prevCachedIdx != null && prevCachedIdx != _currentIndex.value) {
                _currentIndex.value = prevCachedIdx
                loadAndPlay(q[prevCachedIdx])
                return
            }

            scope.launch {
                val currentSongId = _playbackState.value.currentSong?.id
                val recentCached = try {
                    recentlyPlayedRepository.getRecentSongsList()
                        .firstOrNull { it.id != currentSongId && audioCacheProvider.isSongCached(it.id) }
                } catch (_: Exception) {
                    null
                }

                if (recentCached != null) {
                    _queue.update { current ->
                        val withoutDup = current.filterNot { it.id == recentCached.id }
                        withoutDup + recentCached
                    }
                    _currentIndex.value = _queue.value.size - 1
                    loadAndPlay(recentCached)
                } else {
                    seekTo(0)
                }
            }
            return
        }

        val prevIdx = _currentIndex.value - 1
        if (prevIdx >= 0 && prevIdx in q.indices) {
            _currentIndex.value = prevIdx
            loadAndPlay(q[prevIdx])
        } else {
            seekTo(0)
        }
    }

    fun playQueueIndex(index: Int) {
        if (jamInterceptor?.onPlayQueueIndex(index) == true) return
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
        if (jamInterceptor?.onAddToQueue(song) == true) return
        if (_queue.value.isEmpty() || playbackState.value.currentSong == null) {
            playSong(song)
            return
        }
        _recommendedSongIds.update { it - song.id }
        _queue.update { current ->
            val filtered = current.filterNot { SongDeduplicator.areSongsEquivalent(it, song) }.toMutableList()
            val currIdx = _currentIndex.value
            // Find the first recommended song after currentIndex so user queue plays first
            val firstRecIdx = filtered.indices
                .filter { it > currIdx }
                .firstOrNull { filtered[it].id in _recommendedSongIds.value }

            val insertAt = firstRecIdx ?: filtered.size
            filtered.add(insertAt, song)
            filtered
        }
    }

    fun playNext(song: Song) {
        if (jamInterceptor?.onPlayNext(song) == true) return
        if (_queue.value.isEmpty() || playbackState.value.currentSong == null) {
            playSong(song)
            return
        }
        _recommendedSongIds.update { it - song.id }
        _queue.update { current ->
            val filtered = current.filterNot { SongDeduplicator.areSongsEquivalent(it, song) }.toMutableList()
            val insertAt = (_currentIndex.value + 1).coerceAtMost(filtered.size)
            filtered.add(insertAt, song)
            filtered
        }
    }

    fun removeQueueItem(index: Int) {
        if (jamInterceptor?.onRemoveFromQueue(index) == true) return
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        _recommendedSongIds.update { it - removed.id }
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
        if (jamInterceptor?.onReorderQueue(fromIndex, toIndex) == true) return
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
        if (jamInterceptor?.onToggleRepeat() == true) return
        _isRepeatOne.update { !it }
    }

    fun toggleShuffle() {
        if (jamInterceptor?.onToggleShuffle() == true) return
        val willBeShuffle = !_isShuffle.value
        _isShuffle.value = willBeShuffle

        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val currentIdx = _currentIndex.value
        if (currentIdx !in currentQueue.indices) return

        val playedHistoryAndCurrent = currentQueue.subList(0, currentIdx + 1)
        val recIds = _recommendedSongIds.value
        val firstRecIdx = currentQueue.indices
            .filter { it > currentIdx }
            .firstOrNull { currentQueue[it].id in recIds }

        val endOfUpcoming = firstRecIdx ?: currentQueue.size
        val upcomingUserSongs = currentQueue.subList(currentIdx + 1, endOfUpcoming)
        val recommendedPart = if (firstRecIdx != null) currentQueue.subList(firstRecIdx, currentQueue.size) else emptyList()

        if (willBeShuffle) {
            // Turning shuffle ON: stash current queue as unshuffled if not already stashed
            if (unshuffledQueue == null) {
                unshuffledQueue = currentQueue
            }
            if (upcomingUserSongs.size > 1) {
                val shuffledUpcoming = upcomingUserSongs.shuffled()
                _queue.value = playedHistoryAndCurrent + shuffledUpcoming + recommendedPart
            }
        } else {
            // Turning shuffle OFF: restore original sequence of upcoming songs
            val orig = unshuffledQueue
            if (orig != null && upcomingUserSongs.isNotEmpty()) {
                val playedIds = playedHistoryAndCurrent.map { it.id }.toSet()
                val restoredUpcoming = orig.filterNot { it.id in playedIds }
                if (restoredUpcoming.isNotEmpty()) {
                    _queue.value = playedHistoryAndCurrent + restoredUpcoming + recommendedPart
                }
            }
            unshuffledQueue = null
        }
    }

    fun setShuffle(enabled: Boolean) {
        if (_isShuffle.value != enabled) {
            toggleShuffle()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Queue Isolation for Jam Sessions
    // ─────────────────────────────────────────────────────────────

    override fun stashPersonalQueue() {
        if (personalQueueStash.isStashed) return
        personalQueueStash.stash(
            queue = _queue.value,
            currentIndex = _currentIndex.value,
            recommendedSongIds = _recommendedSongIds.value,
            sourcePlaylist = sourcePlaylist,
            nextSourceIndex = nextSourceIndex,
            activeRadioArtist = activeRadioArtist
        )
        activeRadioArtist = null
    }

    override fun restorePersonalQueue() {
        personalQueueStash.restore()?.let { stashed ->
            _queue.value = stashed.queue
            _currentIndex.value = stashed.currentIndex.coerceIn(0, (stashed.queue.size - 1).coerceAtLeast(0))
            _recommendedSongIds.value = stashed.recommendedSongIds
            sourcePlaylist = stashed.sourcePlaylist
            nextSourceIndex = stashed.nextSourceIndex
            activeRadioArtist = stashed.activeRadioArtist
        }
        _jamRecommendedQids.value = emptySet()
        _jamQueueQids.value = emptyList()
    }

    override fun setJamQueue(
        jamQueue: List<Song>,
        targetSong: Song?,
        targetQid: String?,
        queueQids: List<String>,
        recommendedQids: Set<String>
    ) {
        _queue.value = jamQueue
        _jamQueueQids.value = queueQids
        _jamRecommendedQids.value = recommendedQids
        if (targetQid != null && queueQids.isNotEmpty()) {
            val idx = queueQids.indexOf(targetQid)
            if (idx >= 0) {
                _currentIndex.value = idx
            }
        } else if (targetSong != null) {
            val idx = jamQueue.indexOfFirst { it.id == targetSong.id }
            if (idx >= 0) {
                _currentIndex.value = idx
            }
        }
    }

    override fun playJamSyncSong(
        song: Song,
        initialPositionMs: Long,
        playWhenReady: Boolean,
        targetQid: String?,
        forceRefresh: Boolean
    ) {
        activeRadioArtist = null
        sourcePlaylist = null
        nextSourceIndex = 0
        sessionPlayedIds.add(song.id)
        if (forceRefresh) {
            streamCacheStorage.invalidateStreamUri(song.id)
        }
        if (targetQid != null && _jamQueueQids.value.isNotEmpty()) {
            val idx = _jamQueueQids.value.indexOf(targetQid)
            if (idx >= 0) {
                _currentIndex.value = idx
            }
        } else {
            val idx = _queue.value.indexOfFirst { it.id == song.id }
            if (idx >= 0) {
                _currentIndex.value = idx
            }
        }
        loadAndPlay(song, initialPositionMs, playWhenReady)
    }

    override fun invalidateStreamCache(songId: String) {
        streamCacheStorage.invalidateStreamUri(songId)
    }

    override fun prefetchTrack(song: Song) {
        scope.launch(Dispatchers.IO) {
            try {
                if (audioCacheProvider.isSongCached(song.id)) return@launch
                val streamResult = repository.resolveStream(song.id, song.providerId)
                if (streamResult is AppResult.Success) {
                    val finalUri = when (val info = streamResult.data) {
                        is StreamInfo.JioSaavn -> info.url
                        is StreamInfo.YouTube -> extractYouTubeStream(info.videoId)
                        is StreamInfo.None -> null
                    }
                    if (!finalUri.isNullOrBlank()) {
                        streamCacheStorage.saveStreamUri(song.id, finalUri)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override fun getPlaybackSpeed(): Float {
        return controller?.playbackParameters?.speed ?: 1.0f
    }

    override fun getPlayerCurrentPosition(): Long {
        return controller?.currentPosition ?: _playbackState.value.playbackPositionMs
    }

    override fun getPlayerPlayWhenReady(): Boolean {
        return controller?.playWhenReady ?: false
    }

    override fun getPlayerPlaybackState(): Int {
        return controller?.playbackState ?: androidx.media3.common.Player.STATE_IDLE
    }

    override fun getPlayerDuration(): Long {
        return controller?.duration?.takeIf { it > 0 } ?: 0L
    }

    override fun applyJamPlay() {
        controller?.let {
            if (!it.isPlaying) {
                it.play()
                startPositionTracking()
            }
        }
    }

    override fun applyJamPause() {
        controller?.pause()
        stopPositionTracking()
    }

    override fun applyJamSeek(positionMs: Long) {
        controller?.seekTo(positionMs)
        _playbackState.update { it.copy(playbackPositionMs = positionMs) }
    }

    override fun applyJamSpeed(factor: Float) {
        controller?.setPlaybackSpeed(factor)
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
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            jamInterceptor?.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionTracking()
            } else {
                stopPositionTracking()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            jamInterceptor?.onPlaybackStateChanged(state)
            if (state == Player.STATE_READY) {
                autoSkipPolicy.onReady()
                reResolveGuard.onReady()
            }
            if (state == Player.STATE_ENDED) {
                val listener = onTrackEndedListener
                if (listener != null) {
                    onTrackEndedListener = null
                    listener.invoke()
                    return
                }
                if (jamInterceptor?.onTrackEnded() == true) {
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
            val song = _playbackState.value.currentSong

            if (jamInterceptor?.onPlayerError(error) == true) {
                return
            }

            if (song != null) {
                val alreadyRetried = !reResolveGuard.shouldReResolve(song.id)
                val action = PlaybackSourcePolicy.onErrorAction(
                    playingFromFullCache = playingFromFullCache,
                    alreadyRetried = alreadyRetried,
                    online = networkMonitor.isOnline.value
                )

                when (action) {
                    PlaybackSourcePolicy.ErrorAction.EVICT_AND_RETRY_NETWORK -> {
                        audioCacheProvider.remove(song.id)
                        streamCacheStorage.invalidateStreamUri(song.id)
                        playingFromFullCache = false
                        scope.launch {
                            val pos = getPlayerCurrentPosition()
                            loadAndPlay(song, initialPositionMs = pos, playWhenReady = true)
                        }
                        return
                    }
                    PlaybackSourcePolicy.ErrorAction.RE_RESOLVE_ONCE -> {
                        streamCacheStorage.invalidateStreamUri(song.id)
                        scope.launch {
                            val pos = getPlayerCurrentPosition()
                            loadAndPlay(song, initialPositionMs = pos, playWhenReady = true)
                        }
                        return
                    }
                    PlaybackSourcePolicy.ErrorAction.GIVE_UP -> {
                        streamCacheStorage.invalidateStreamUri(song.id)
                    }
                }
            }

            _playbackState.update { 
                it.copy(
                    isPlaying = false, 
                    error = error.message ?: "Unknown playback error"
                ) 
            }
            stopPositionTracking()

            if (song != null) {
                handleUnrecoverablePlaybackError(song, error.message ?: "Playback error")
            }
        }
    }

    private fun handleUnrecoverablePlaybackError(song: Song, message: String) {
        if (jamInterceptor == null) {
            val shouldSkip = autoSkipPolicy.onFailure()
            if (shouldSkip) {
                val q = _queue.value
                val hasNext = if (!networkMonitor.isOnline.value) {
                    ((_currentIndex.value + 1) until q.size).any { audioCacheProvider.isSongCached(q[it].id) }
                } else {
                    autoSkipPolicy.canSkipForward(_currentIndex.value, q.size) || (sourcePlaylist != null && nextSourceIndex < (sourcePlaylist?.size ?: 0))
                }

                if (hasNext) {
                    scope.launch {
                        skipToNext(isAutoSkip = true)
                    }
                } else {
                    autoSkipPolicy.onReady()
                    controller?.stop()
                }
            } else {
                controller?.stop()
            }
        }
    }
}
