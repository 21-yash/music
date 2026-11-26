package com.synq.features.sports.highlights

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.synq.features.sports.MatchDetailsSummaryCard
import com.synq.features.sports.MatchDetailsViewModel
import com.synq.features.sports.domain.model.HighlightSource
import com.synq.features.sports.domain.model.MatchHighlight
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.theme.OnSportsAccent
import com.synq.ui.theme.SportsAccent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val SPEED_OPTIONS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Dedicated full-screen-capable match highlight video player screen.
 *
 * - Pauses active music playback immediately on entry.
 * - MiniPlayer, BottomNavBar, and floating avatar remain hidden.
 * - Supports YouTube-style double-tap +/-10s seek, hold-for-2x speed, horizontal drag-seek,
 *   quality selection with live Auto resolution readout, and pinch-to-zoom fullscreen.
 * - Shows MatchDetailsSummaryCard above "More from this match" so single-clip matches remain balanced.
 */
@OptIn(UnstableApi::class)
@Composable
fun HighlightPlayerScreen(
    highlightId: String,
    viewModel: MatchDetailsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activeHighlight = uiState.selectedHighlight
        ?: uiState.highlights.firstOrNull { it.id == highlightId }

    LaunchedEffect(highlightId, uiState.highlights) {
        viewModel.pauseMusicForHighlight()
        val target = uiState.highlights.firstOrNull { it.id == highlightId }
        if (target != null && uiState.selectedHighlight?.id != target.id) {
            viewModel.selectHighlight(target)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectedHighlight()
        }
    }

    val exoPlayer = remember(context) {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var liveVideoHeight by remember { mutableIntStateOf(0) }
    var playerError by remember(activeHighlight?.id) { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isZoomedToFill by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

    var preparedStreamSignature by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    liveVideoHeight = videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Playback failed"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0L) durationMs = d
            delay(if (isPlaying) 250L else 600L)
        }
    }

    val playback = uiState.resolvedPlayback
    val activeQuality = playback?.activeQuality

    LaunchedEffect(playback, activeQuality) {
        if (playback == null || activeQuality == null) {
            preparedStreamSignature = null
            liveVideoHeight = 0
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        playerError = null

        val paramsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        if (activeQuality.isHls && activeQuality.height > 0) {
            paramsBuilder.setMaxVideoSize(Int.MAX_VALUE, activeQuality.height)
        } else {
            paramsBuilder.clearVideoSizeConstraints()
        }
        exoPlayer.trackSelectionParameters = paramsBuilder.build()

        val sig = "${activeQuality.videoUrl}|${activeQuality.audioUrl}|${activeQuality.isHls}"
        if (sig == preparedStreamSignature) {
            return@LaunchedEffect
        }

        val resumePosition = if (preparedStreamSignature != null) exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
        val shouldPlay = if (preparedStreamSignature != null) exoPlayer.playWhenReady else true
        preparedStreamSignature = sig
        liveVideoHeight = 0

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        if (playback.headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(playback.headers)
        }

        val mediaSource = when {
            activeQuality.isHls -> {
                HlsMediaSource.Factory(httpFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(activeQuality.videoUrl))
            }
            activeQuality.audioUrl != null -> {
                val mediaFactory = DefaultMediaSourceFactory(httpFactory)
                val videoSrc = mediaFactory.createMediaSource(MediaItem.fromUri(activeQuality.videoUrl))
                val audioSrc = mediaFactory.createMediaSource(MediaItem.fromUri(activeQuality.audioUrl))
                MergingMediaSource(true, videoSrc, audioSrc)
            }
            else -> {
                DefaultMediaSourceFactory(httpFactory)
                    .createMediaSource(MediaItem.fromUri(activeQuality.videoUrl))
            }
        }

        exoPlayer.setMediaSource(mediaSource)
        if (resumePosition > 0L) {
            exoPlayer.seekTo(resumePosition)
        }
        exoPlayer.prepare()
        exoPlayer.setPlaybackSpeed(selectedSpeed)
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.playWhenReady = shouldPlay
    }

    val effectiveError = uiState.streamError ?: playerError
    val summary = uiState.details?.summary
    val qualityDisplayLabel = remember(activeQuality, liveVideoHeight) {
        when {
            activeQuality == null -> null
            activeQuality.height == 0 && liveVideoHeight > 0 -> "Auto (${liveVideoHeight}p)"
            else -> activeQuality.label
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (summary != null) "${summary.team1.shortName} vs ${summary.team2.shortName}" else "Match Highlights",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary != null && summary.seriesName.isNotBlank()) {
                    Text(
                        text = summary.seriesName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (activeHighlight != null) {
                IconButton(
                    onClick = { runCatching { uriHandler.openUri(activeHighlight.externalUrl) } }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open externally",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val playerBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        HorizontalDivider(thickness = 1.dp, color = playerBorderColor)

        // Pinned 16:9 Video Viewport (Portrait)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (!isFullscreen) {
                HighlightPlayerViewport(
                    exoPlayer = exoPlayer,
                    highlight = activeHighlight,
                    playback = playback,
                    liveVideoHeight = liveVideoHeight,
                    isResolving = uiState.isResolvingStream,
                    isBuffering = isBuffering,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    error = effectiveError,
                    isFullscreen = false,
                    isZoomedToFill = false,
                    isMuted = isMuted,
                    selectedSpeed = selectedSpeed,
                    onTogglePlayPause = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onSeekTo = { target ->
                        val maxSeek = if (durationMs > 0L) durationMs else target
                        val clamped = target.coerceIn(0L, maxSeek)
                        exoPlayer.seekTo(clamped)
                        positionMs = clamped
                    },
                    onToggleMute = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    onCycleSpeed = {
                        val nextIdx = (SPEED_OPTIONS.indexOf(selectedSpeed) + 1) % SPEED_OPTIONS.size
                        selectedSpeed = SPEED_OPTIONS[nextIdx]
                        exoPlayer.setPlaybackSpeed(selectedSpeed)
                    },
                    onSelectQuality = viewModel::selectQuality,
                    onToggleFullscreen = { isFullscreen = true },
                    onZoomChange = {},
                    onBackClick = onBackClick,
                    onRetry = { activeHighlight?.let(viewModel::selectHighlight) },
                    onOpenExternal = {
                        activeHighlight?.let { runCatching { uriHandler.openUri(it.externalUrl) } }
                    }
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = playerBorderColor)

        // Scrollable Metadata + MatchCard + More from this match
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (activeHighlight != null) {
                item(key = "active_clip_meta") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = activeHighlight.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val badge = activeHighlight.publisher.ifBlank {
                                when (activeHighlight.source) {
                                    HighlightSource.BCCI -> "BCCI.TV"
                                    HighlightSource.IPLT20 -> "IPL"
                                    HighlightSource.YOUTUBE -> "YouTube"
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = badge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            val metaParts = listOfNotNull(
                                formatHighlightDate(activeHighlight.publishedAt),
                                activeHighlight.duration.takeIf { it.isNotBlank() && it != "0:00" },
                                qualityDisplayLabel
                            ).joinToString(" • ")
                            if (metaParts.isNotBlank()) {
                                Text(
                                    text = metaParts,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (summary != null) {
                item(key = "match_summary_card") {
                    MatchDetailsSummaryCard(summary = summary)
                }
            }

            if (uiState.highlights.isNotEmpty()) {
                item(key = "more_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MORE FROM THIS MATCH",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${uiState.highlights.size} ${if (uiState.highlights.size == 1) "clip" else "clips"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(uiState.highlights, key = { it.id }) { clip ->
                    val isSelected = activeHighlight?.id == clip.id
                    HighlightCard(
                        highlight = clip,
                        isSelected = isSelected,
                        onClick = { viewModel.selectHighlight(clip) }
                    )
                }
            }
        }
    }

    if (isFullscreen) {
        FullscreenHighlightDialog(
            onDismiss = {
                isFullscreen = false
                isZoomedToFill = false
            }
        ) {
            HighlightPlayerViewport(
                exoPlayer = exoPlayer,
                highlight = activeHighlight,
                playback = playback,
                liveVideoHeight = liveVideoHeight,
                isResolving = uiState.isResolvingStream,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                error = effectiveError,
                isFullscreen = true,
                isZoomedToFill = isZoomedToFill,
                isMuted = isMuted,
                selectedSpeed = selectedSpeed,
                onTogglePlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onSeekTo = { target ->
                    val maxSeek = if (durationMs > 0L) durationMs else target
                    val clamped = target.coerceIn(0L, maxSeek)
                    exoPlayer.seekTo(clamped)
                    positionMs = clamped
                },
                onToggleMute = {
                    isMuted = !isMuted
                    exoPlayer.volume = if (isMuted) 0f else 1f
                },
                onCycleSpeed = {
                    val nextIdx = (SPEED_OPTIONS.indexOf(selectedSpeed) + 1) % SPEED_OPTIONS.size
                    selectedSpeed = SPEED_OPTIONS[nextIdx]
                    exoPlayer.setPlaybackSpeed(selectedSpeed)
                },
                onSelectQuality = viewModel::selectQuality,
                onToggleFullscreen = {
                    isFullscreen = false
                    isZoomedToFill = false
                },
                onZoomChange = { zoomed -> isZoomedToFill = zoomed },
                onBackClick = {
                    isFullscreen = false
                    isZoomedToFill = false
                },
                onRetry = { activeHighlight?.let(viewModel::selectHighlight) },
                onOpenExternal = {
                    activeHighlight?.let { runCatching { uriHandler.openUri(it.externalUrl) } }
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HighlightPlayerViewport(
    exoPlayer: ExoPlayer,
    highlight: MatchHighlight?,
    playback: ResolvedHighlightPlayback?,
    liveVideoHeight: Int,
    isResolving: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    error: String?,
    isFullscreen: Boolean,
    isZoomedToFill: Boolean,
    isMuted: Boolean,
    selectedSpeed: Float,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onToggleFullscreen: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var isHolding2x by remember { mutableStateOf(false) }

    var seekBurstDeltaSec by remember { mutableIntStateOf(0) }
    var seekBurstNonce by remember { mutableIntStateOf(0) }

    var dragPreviewMs by remember { mutableStateOf<Long?>(null) }
    var dragDeltaSec by remember { mutableIntStateOf(0) }
    var sliderDragMs by remember { mutableStateOf<Long?>(null) }

    var zoomBadgeText by remember { mutableStateOf<String?>(null) }
    var zoomBadgeNonce by remember { mutableIntStateOf(0) }

    // Keep latest callbacks/values inside rememberUpdatedState so pointerInput never restarts mid-drag!
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentSpeed by rememberUpdatedState(selectedSpeed)
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentIsZoomed by rememberUpdatedState(isZoomedToFill)
    val currentPlayback by rememberUpdatedState(playback)
    val currentError by rememberUpdatedState(error)

    LaunchedEffect(controlsVisible, isPlaying, showQualityMenu, sliderDragMs, isHolding2x) {
        if (controlsVisible && isPlaying && !showQualityMenu && sliderDragMs == null && !isHolding2x) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekBurstNonce) {
        if (seekBurstNonce > 0) {
            delay(700)
            seekBurstDeltaSec = 0
        }
    }

    LaunchedEffect(zoomBadgeNonce) {
        if (zoomBadgeNonce > 0) {
            delay(900)
            zoomBadgeText = null
        }
    }

    val effectivePosMs = sliderDragMs ?: dragPreviewMs ?: positionMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Multi-touch pinch-to-zoom detector in fullscreen (only consumes >= 2 active pointers)
            .then(
                if (isFullscreen) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var zoomAccum = 1f
                            var triggeredInGesture = false
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2 && !triggeredInGesture) {
                                    val zoomChange = event.calculateZoom()
                                    zoomAccum *= zoomChange
                                    if (zoomAccum > 1.14f && !currentIsZoomed) {
                                        triggeredInGesture = true
                                        currentOnZoomChange(true)
                                        zoomBadgeText = "Zoomed to fill"
                                        zoomBadgeNonce++
                                    } else if (zoomAccum < 0.86f && currentIsZoomed) {
                                        triggeredInGesture = true
                                        currentOnZoomChange(false)
                                        zoomBadgeText = "Original"
                                        zoomBadgeNonce++
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else Modifier
            )
            // Horizontal drag-to-seek (NEVER keyed on positionMs so dragging doesn't cancel mid-gesture!)
            .pointerInput(exoPlayer) {
                var dragAccumX = 0f
                var dragStartPosMs = 0L
                var isDragging = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        val dur = currentDurationMs.takeIf { it > 0L } ?: exoPlayer.duration
                        if (dur <= 0L || isHolding2x) {
                            isDragging = false
                            return@detectHorizontalDragGestures
                        }
                        isDragging = true
                        dragAccumX = 0f
                        dragStartPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!isDragging || isHolding2x) return@detectHorizontalDragGestures
                        val dur = currentDurationMs.takeIf { it > 0L } ?: exoPlayer.duration
                        if (dur <= 0L) return@detectHorizontalDragGestures
                        change.consume()
                        dragAccumX += dragAmount
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val deltaMs = ((dragAccumX / widthPx) * 90_000f).roundToLong()
                        val target = (dragStartPosMs + deltaMs).coerceIn(0L, dur)
                        dragPreviewMs = target
                        dragDeltaSec = ((target - dragStartPosMs) / 1000L).toInt()
                    },
                    onDragEnd = {
                        val target = dragPreviewMs
                        dragPreviewMs = null
                        if (isDragging && target != null) {
                            currentOnSeekTo(target)
                        }
                        isDragging = false
                    },
                    onDragCancel = {
                        dragPreviewMs = null
                        isDragging = false
                    }
                )
            }
            // Tap, Double-Tap (±10s), and Press-and-Hold (2.0x)
            .pointerInput(exoPlayer) {
                detectTapGestures(
                    onTap = {
                        if (showQualityMenu) {
                            showQualityMenu = false
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2f
                        val deltaMs = if (isLeft) -10_000L else 10_000L
                        val current = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val dur = currentDurationMs.takeIf { it > 0L } ?: exoPlayer.duration
                        val maxDur = if (dur > 0L) dur else (current + 10_000L)
                        val target = (current + deltaMs).coerceIn(0L, maxDur)
                        currentOnSeekTo(target)

                        seekBurstDeltaSec = if (isLeft) {
                            if (seekBurstDeltaSec < 0) seekBurstDeltaSec - 10 else -10
                        } else {
                            if (seekBurstDeltaSec > 0) seekBurstDeltaSec + 10 else 10
                        }
                        seekBurstNonce++
                    },
                    onLongPress = {
                        if (currentPlayback != null && currentError == null) {
                            isHolding2x = true
                            exoPlayer.setPlaybackSpeed(2.0f)
                        }
                    },
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (isHolding2x) {
                                isHolding2x = false
                                exoPlayer.setPlaybackSpeed(currentSpeed)
                            }
                        }
                    }
                )
            }
    ) {
        if (playback != null && error == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = if (isZoomedToFill) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                    val targetResize = if (isZoomedToFill) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    if (view.resizeMode != targetResize) {
                        view.resizeMode = targetResize
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        when {
            isResolving || (isBuffering && playback != null && error == null) -> {
                SynqLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    size = 40.dp,
                    barCount = 5
                )
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to play stream directly",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SportsAccent,
                                contentColor = OnSportsAccent
                            )
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (highlight != null) {
                            OutlinedButton(
                                onClick = onOpenExternal,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch externally", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (isHolding2x) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("2.0x Speed »", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        zoomBadgeText?.let { badgeText ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isHolding2x) 48.dp else 14.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(badgeText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (seekBurstDeltaSec != 0) {
            val isBack = seekBurstDeltaSec < 0
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBack) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "« ${-seekBurstDeltaSec}s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isBack) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "+${seekBurstDeltaSec}s »",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (dragPreviewMs != null) {
            val sign = if (dragDeltaSec >= 0) "+" else "-"
            val absSec = kotlin.math.abs(dragDeltaSec)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${formatDurationMs(dragPreviewMs!!)} ($sign${absSec}s)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && playback != null && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            ) {
                if (isFullscreen && highlight != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit fullscreen",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = highlight.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (highlight.publisher.isNotBlank()) {
                                Text(
                                    text = highlight.publisher,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Center Transport Controls (-10s | White Play/Pause on Black Circle | +10s)
                if (!isBuffering && !isResolving) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                onSeekTo(target)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                        }

                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.56f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10_000L)
                                    .coerceAtMost(durationMs.coerceAtLeast(exoPlayer.currentPosition + 10_000L))
                                onSeekTo(target)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                        }
                    }
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val sliderFraction = if (durationMs > 0L) {
                        (effectivePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    ThinWhiteProgressBar(
                        fraction = sliderFraction,
                        isDragging = sliderDragMs != null,
                        onScrub = { frac ->
                            if (durationMs > 0L) {
                                sliderDragMs = (frac * durationMs).roundToLong()
                            }
                        },
                        onScrubEnd = {
                            sliderDragMs?.let(onSeekTo)
                            sliderDragMs = null
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDurationMs(effectivePosMs)} / ${formatDurationMs(durationMs)}",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quality Picker Pill + Compact Menu
                            val currentPlayback = playback
                            if (currentPlayback != null) {
                                val pillLabel = if (currentPlayback.activeQuality.height == 0 && liveVideoHeight > 0) {
                                    "Auto (${liveVideoHeight}p)"
                                } else {
                                    currentPlayback.activeQuality.label
                                }

                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .clickable { showQualityMenu = !showQualityMenu }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = pillLabel,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showQualityMenu,
                                        onDismissRequest = { showQualityMenu = false },
                                        containerColor = Color(0xF018151C)
                                    ) {
                                        currentPlayback.qualityOptions.forEach { option ->
                                            val isCurrent = option.label == currentPlayback.activeQuality.label
                                            val itemLabel = if (option.height == 0 && liveVideoHeight > 0) {
                                                "Auto (${liveVideoHeight}p)"
                                            } else {
                                                option.label
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .widthIn(min = 108.dp)
                                                    .clickable {
                                                        showQualityMenu = false
                                                        onSelectQuality(option)
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = itemLabel,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.78f)
                                                )
                                                if (isCurrent) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .clickable(onClick = onCycleSpeed)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                val speedLabel = if (selectedSpeed == 1.0f) "1.0x" else "${selectedSpeed}x"
                                Text(
                                    text = speedLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(onClick = onToggleMute, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thin YouTube-style white progress bar with a small circular thumb (no Material3 vertical indicator line).
 */
@Composable
private fun ThinWhiteProgressBar(
    fraction: Float,
    isDragging: Boolean,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val thumbSize = if (isDragging) 14.dp else 10.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val trackHeight = if (isDragging) 4.dp else 2.5.dp
    val clamped = fraction.coerceIn(0f, 1f)

    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                    currentOnScrub(frac)
                    currentOnScrubEnd()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val frac = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        currentOnScrub(frac)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        currentOnScrub(frac)
                    },
                    onDragEnd = { currentOnScrubEnd() },
                    onDragCancel = { currentOnScrubEnd() }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Unplayed track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.32f))
        )
        // Played track
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = clamped)
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White)
        )
        // Circular thumb
        val maxOffset = (widthPx - thumbPx).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .offset { IntOffset((clamped * maxOffset).roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun FullscreenHighlightDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current

    BackHandler(onBack = onDismiss)

    DisposableEffect(activity, view) {
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val activityWindow = activity?.window
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window

        listOfNotNull(activityWindow, dialogWindow).forEach { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activityWindow?.let { win ->
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = prevOrientation
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val win = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            content()
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
