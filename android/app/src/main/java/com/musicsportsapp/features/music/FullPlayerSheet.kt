package com.musicsportsapp.features.music

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicsportsapp.features.music.domain.model.Lyrics
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.jam.JamParticipantUi
import com.musicsportsapp.features.music.jam.JamState
import com.musicsportsapp.features.music.playback.PlaybackState
import com.musicsportsapp.ui.isAppDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Unified Pink Design Tokens ───
val BrandPink = Color(0xFFFF2A5F)
val Accent = Color(0xFFFF3366)

// Cohesive vibrant Pink Gradient used across all tabs & actions
private val PinkBrush = Brush.linearGradient(
    listOf(Color(0xFFFF2A5F), Color(0xFFFF5E8A), Color(0xFFE11D48))
)

private enum class PlayerPanel { Queue, Lyrics, Jam }

private val SleepOptions = listOf(
    listOf("Off", "15 mins"),
    listOf("30 mins", "45 mins"),
    listOf("60 mins", "End of track")
)

// ─────────────────────────────────────────────────────────────
// Full player
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playbackState: PlaybackState,
    queue: List<Song> = emptyList(),
    currentIndex: Int = 0,
    isRepeatOne: Boolean = false,
    isShuffle: Boolean = false,
    currentLyrics: Lyrics? = null,
    isLyricsLoading: Boolean = false,
    sleepSelected: String = "Off",
    sleepRemainingSeconds: Long? = null,
    jamState: JamState = JamState(),
    isLiked: Boolean = false,
    onLikeClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit = {},
    onPlayQueueIndex: (Int) -> Unit = {},
    onRemoveQueueItem: (Int) -> Unit = {},
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onSelectSleepOption: (String) -> Unit = {},
    onStartJam: () -> Unit = {},
    onJoinJam: (String) -> Unit = {},
    onLeaveJam: () -> Unit = {},
    onRemoveJamSong: (Int) -> Unit = {},
    onReorderJamQueue: (Int, Int) -> Unit = { _, _ -> },
    onToggleGuestReorder: (Boolean) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val song = playbackState.currentSong ?: return
    val isDark = isAppDark()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Dynamic Theme Tokens
    val containerBg = if (isDark) Color(0xFF131016) else Color(0xFFF9F7FA)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)
    val surfaceChip = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val surfaceChipBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    var openPanel by remember { mutableStateOf<PlayerPanel?>(null) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current



    // Remaining duration of current song in seconds for End of track sleep timer
    val trackRemainingSeconds = remember(playbackState.durationMs, playbackState.playbackPositionMs) {
        val remainingMs = (playbackState.durationMs - playbackState.playbackPositionMs).coerceAtLeast(0L)
        remainingMs / 1000L
    }

    val effectiveSleepRemainingSec = if (sleepSelected == "End of track") {
        trackRemainingSeconds
    } else {
        sleepRemainingSeconds
    }

    fun togglePanel(panel: PlayerPanel) {
        openPanel = if (openPanel == panel) null else panel
    }

    val depth by animateFloatAsState(
        targetValue = if (openPanel != null || showMoreOptions) 1f else 0f,
        animationSpec = tween(350),
        label = "contentDepth"
    )

    BackHandler(enabled = true) {
        if (showSleepTimer) {
            showSleepTimer = false
        } else if (showMoreOptions) {
            showMoreOptions = false
        } else if (openPanel != null) {
            openPanel = null
        } else {
            onDismiss()
        }
    }

    val isAnyTabOpen = openPanel != null || showSleepTimer || showMoreOptions
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBg)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .pointerInput(isAnyTabOpen) {
                if (!isAnyTabOpen) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > 160f) {
                                onDismiss()
                            }
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            if (dragOffsetY > 320f) {
                                onDismiss()
                                dragOffsetY = 0f
                            }
                        }
                    )
                }
            }
    ) {

            // Ambient background with Light / Dark support
            AmbientBackground(imageUrl = song.imageUrl, isDark = isDark)

            // ── Main player content ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.08f * depth
                        scaleX = s
                        scaleY = s
                        translationY = -15.dp.toPx() * depth
                    }
                    .then(if (depth > 0f) Modifier.blur((4 * depth).dp) else Modifier)
                    .drawWithContent {
                        drawContent()
                        if (depth > 0f) {
                            val dimColor = if (isDark) Color.Black.copy(alpha = 0.45f * depth) else Color.White.copy(alpha = 0.35f * depth)
                            drawRect(dimColor)
                        }
                    }
                    .navigationBarsPadding()
            ) {
                // Top bar with statusBarsPadding + breathing space
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TopBarButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        description = "Close",
                        isDark = isDark,
                        onClick = onDismiss
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (jamState.isInJam) "JAM SESSION • ${jamState.sessionCode}" else "NOW PLAYING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (jamState.isInJam) Accent else textSecondary,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Provider Info Badge (JioSaavn / YouTube Music) placed below NOW PLAYING
                        val isJioSaavn = song.providerId.contains("saavn", ignoreCase = true)
                        val providerName = if (isJioSaavn) "JioSaavn" else "YouTube Music"
                        val providerIcon = if (isJioSaavn) com.musicsportsapp.R.drawable.saavn else com.musicsportsapp.R.drawable.yt

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(surfaceChip)
                                .border(1.dp, surfaceChipBorder, RoundedCornerShape(100.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Image(
                                painter = painterResource(id = providerIcon),
                                contentDescription = providerName,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = providerName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                        }

                        if (effectiveSleepRemainingSec != null && sleepSelected != "Off") {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (sleepSelected == "End of track") {
                                    "Sleep: ${formatTimerRemaining(effectiveSleepRemainingSec)} (End of track)"
                                } else {
                                    "Sleep: ${formatTimerRemaining(effectiveSleepRemainingSec)}"
                                },
                                fontSize = 11.sp,
                                color = Accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TopBarButton(
                        icon = Icons.Default.MoreHoriz,
                        description = "More",
                        isDark = isDark,
                        onClick = { showMoreOptions = true }
                    )
                }

                // Main body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album art
                    val artScale by animateFloatAsState(
                        targetValue = if (playbackState.isPlaying) 1.02f else 1f,
                        animationSpec = tween(400),
                        label = "artScale"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val artShape = RoundedCornerShape(24.dp)
                        AsyncImage(
                            model = song.imageUrl,
                            contentDescription = "Album art for ${song.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxHeight(1f)
                                .aspectRatio(1f)
                                .graphicsLayer { scaleX = artScale; scaleY = artScale }
                                .shadow(
                                    elevation = 20.dp,
                                    shape = artShape,
                                    ambientColor = if (isDark) Color.Black else Color(0x33000000),
                                    spotColor = if (isDark) Color.Black else Color(0x33000000)
                                )
                                .clip(artShape)
                                .background(surfaceChip)
                        )
                    }

                    // Song info + like
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.primaryArtistName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = textSecondary,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }

                        IconButton(
                            onClick = onLikeClick,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                tint = if (isLiked) Accent else textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress bar
                    PlayerProgressBar(
                        progress = playbackState.progress,
                        onSeek = onSeek,
                        isDark = isDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(playbackState.playbackPositionMs),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = formatDuration(song.durationSeconds),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Controls (Shuffle, Previous, Play/Pause, Next, Repeat-One)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) Accent else textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = onPreviousClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = textPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = CircleShape,
                                    ambientColor = Accent.copy(alpha = 0.5f),
                                    spotColor = Accent.copy(alpha = 0.5f)
                                )
                                .clip(CircleShape)
                                .background(PinkBrush)
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset(x = if (playbackState.isPlaying) 0.dp else 2.dp)
                            )
                        }
                        IconButton(onClick = onNextClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = textPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        // Repeat toggle: binary OFF vs ON (loops current song)
                        IconButton(onClick = onToggleRepeat) {
                            Icon(
                                Icons.Default.RepeatOne,
                                contentDescription = "Repeat",
                                tint = if (isRepeatOne) Accent else textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Redesigned Floating Bottom Nav Dock (Lyrics, Sleep, Jam, Queue)
                    FloatingBottomDock(
                        isDark = isDark,
                        openPanel = openPanel,
                        showSleepTimer = showSleepTimer,
                        sleepRemainingSeconds = effectiveSleepRemainingSec,
                        jamState = jamState,
                        onLyricsClick = { togglePanel(PlayerPanel.Lyrics) },
                        onSleepClick = { showSleepTimer = !showSleepTimer },
                        onJamClick = { togglePanel(PlayerPanel.Jam) },
                        onQueueClick = { togglePanel(PlayerPanel.Queue) }
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // ── Queue panel (Glassmorphic, Swipeable Downwards & Light/Dark) ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Queue,
                title = "Queue (${queue.size} songs)",
                isDark = isDark,
                onClose = { openPanel = null }
            ) {
                if (queue.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Queue is empty", color = textSecondary, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = navBarBottom + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { index, item ->
                            val isPlayingThis = index == currentIndex
                            QueueItemRow(
                                song = item,
                                active = isPlayingThis,
                                isDark = isDark,
                                canMoveUp = index > 0,
                                canMoveDown = index < queue.size - 1,
                                onClick = { onPlayQueueIndex(index) },
                                onMoveUp = { onReorderQueue(index, index - 1) },
                                onMoveDown = { onReorderQueue(index, index + 1) },
                                onRemove = { onRemoveQueueItem(index) }
                            )
                        }
                    }
                }
            }

            // ── Lyrics panel (Glassmorphic, Swipeable Downwards & Light/Dark) ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Lyrics,
                title = "Lyrics",
                isDark = isDark,
                onClose = { openPanel = null }
            ) {
                RealLyricsContent(
                    lyrics = currentLyrics,
                    isLoading = isLyricsLoading,
                    currentPositionMs = playbackState.playbackPositionMs,
                    isDark = isDark,
                    onSeekTo = onSeekToMs,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Jam panel (Glassmorphic, Swipeable Downwards & Light/Dark) ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Jam,
                title = if (jamState.isInJam) "Jam Session • ${jamState.sessionCode}" else "Start a Jam",
                isDark = isDark,
                onClose = { openPanel = null }
            ) {
                if (!jamState.isInJam) {
                    RealJamIntro(
                        modifier = Modifier.weight(1f),
                        isDark = isDark,
                        onStart = onStartJam,
                        onJoin = onJoinJam
                    )
                } else {
                    RealJamActive(
                        modifier = Modifier.weight(1f),
                        jamState = jamState,
                        isDark = isDark,
                        onEndOrLeave = onLeaveJam,
                        onRemoveSong = onRemoveJamSong,
                        onReorderSong = onReorderJamQueue,
                        onToggleGuestReorder = onToggleGuestReorder
                    )
                }
            }

            // ── Sleep timer popup (Glassmorphic & Swipeable Downwards) ──
            androidx.compose.animation.AnimatedVisibility(
                visible = showSleepTimer,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.40f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showSleepTimer = false },
                    contentAlignment = Alignment.Center
                ) {
                    SleepTimerCard(
                        selected = sleepSelected,
                        remainingSeconds = effectiveSleepRemainingSec,
                        isDark = isDark,
                        onSelect = { label ->
                            onSelectSleepOption(label)
                            scope.launch {
                                delay(200)
                                showSleepTimer = false
                            }
                        },
                        onCancel = { showSleepTimer = false }
                    )
                }
            }

            // ── More Options Sheet (Accessible via 3 dots) ──
            androidx.compose.animation.AnimatedVisibility(
                visible = showMoreOptions,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.40f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showMoreOptions = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    PlayerMoreOptionsSheet(
                        song = song,
                        isDark = isDark,
                        isLiked = isLiked,
                        onPlayNext = {
                            onPlayNext(song)
                            Toast.makeText(context, "Added to play next", Toast.LENGTH_SHORT).show()
                            showMoreOptions = false
                        },
                        onAddToQueue = {
                            onAddToQueue(song)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                            showMoreOptions = false
                        },
                        onLikeClick = {
                            onLikeClick()
                            showMoreOptions = false
                        },
                        onStartJam = {
                            showMoreOptions = false
                            openPanel = PlayerPanel.Jam
                        },
                        onOpenSleepTimer = {
                            showMoreOptions = false
                            showSleepTimer = true
                        },
                        onDismiss = { showMoreOptions = false }
                    )
                }
            }
        }
    }

// ─────────────────────────────────────────────────────────────
// Redesigned Floating Bottom Dock (styled like AppBottomNavBar)
// ─────────────────────────────────────────────────────────────

@Composable
private fun FloatingBottomDock(
    isDark: Boolean,
    openPanel: PlayerPanel?,
    showSleepTimer: Boolean,
    sleepRemainingSeconds: Long?,
    jamState: JamState,
    onLyricsClick: () -> Unit,
    onSleepClick: () -> Unit,
    onJamClick: () -> Unit,
    onQueueClick: () -> Unit
) {
    val dockBg = if (isDark) {
        Color(0xFF1E1824).copy(alpha = 0.88f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.90f)
    }

    val dockBorder = if (isDark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)))
    } else {
        Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.04f)))
    }

    val dockShape = RoundedCornerShape(26.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 12.dp else 8.dp,
                shape = dockShape,
                ambientColor = if (isDark) Color.Black else Color(0x1A000000),
                spotColor = if (isDark) Color.Black else Color(0x1A000000)
            )
            .clip(dockShape)
            .background(dockBg)
            .border(1.dp, dockBorder, dockShape)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockNavItem(
            label = "LYRICS",
            icon = Icons.Default.FormatQuote,
            active = openPanel == PlayerPanel.Lyrics,
            isDark = isDark,
            onClick = onLyricsClick,
            modifier = Modifier.weight(1f)
        )

        DockNavItem(
            label = if (sleepRemainingSeconds != null) formatTimerRemaining(sleepRemainingSeconds) else "SLEEP",
            icon = Icons.Default.DarkMode,
            active = showSleepTimer || sleepRemainingSeconds != null,
            isDark = isDark,
            onClick = onSleepClick,
            modifier = Modifier.weight(1f)
        )

        DockNavItem(
            label = if (jamState.isInJam) jamState.sessionCode ?: "JAM" else "JAM",
            icon = Icons.Default.Groups,
            active = openPanel == PlayerPanel.Jam || jamState.isInJam,
            isDark = isDark,
            onClick = onJamClick,
            modifier = Modifier.weight(1f)
        )

        DockNavItem(
            label = "QUEUE",
            icon = Icons.Default.QueueMusic,
            active = openPanel == PlayerPanel.Queue,
            isDark = isDark,
            onClick = onQueueClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DockNavItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lift by animateDpAsState(
        targetValue = if (active) (-3).dp else 0.dp,
        label = "dockLift"
    )

    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (active) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.14f))
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Accent else inactiveColor,
                modifier = Modifier
                    .size(22.dp)
                    .offset(y = lift)
            )
        }
        Text(
            text = label,
            color = if (active) Accent else inactiveColor,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────
// More Options Sheet (Accessible via 3 Dots in Player)
// ─────────────────────────────────────────────────────────────

@Composable
private fun PlayerMoreOptionsSheet(
    song: Song,
    isDark: Boolean,
    isLiked: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onLikeClick: () -> Unit,
    onStartJam: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val bgBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF221728).copy(alpha = 0.96f), Color(0xFF130E17).copy(alpha = 0.98f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF).copy(alpha = 0.98f), Color(0xFFF9F5F8).copy(alpha = 0.98f)))
    }

    val borderBrush = if (isDark) {
        Brush.verticalGradient(listOf(Accent.copy(alpha = 0.40f), Color.White.copy(alpha = 0.08f)))
    } else {
        Brush.verticalGradient(listOf(Accent.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.08f)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 100f) onDismiss()
                        dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        if (dragOffsetY > 220f) {
                            onDismiss()
                            dragOffsetY = 0f
                        }
                    }
                )
            }
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Drag handle pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f)
                )
        )

        // Song header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.primaryArtistName,
                    fontSize = 13.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OptionRowItem(
            icon = Icons.Default.PlayArrow,
            title = "Play Next",
            subtitle = "Play immediately after current track",
            isDark = isDark,
            onClick = onPlayNext
        )

        OptionRowItem(
            icon = Icons.Default.List,
            title = "Add to Queue",
            subtitle = "Append to the end of playback queue",
            isDark = isDark,
            onClick = onAddToQueue
        )

        OptionRowItem(
            icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            title = if (isLiked) "Remove from Liked" else "Add to Liked Songs",
            subtitle = null,
            isDark = isDark,
            onClick = onLikeClick
        )

        OptionRowItem(
            icon = Icons.Default.Groups,
            title = "Start Jam Session",
            subtitle = "Listen together in real-time with friends",
            isDark = isDark,
            onClick = onStartJam
        )

        OptionRowItem(
            icon = Icons.Default.DarkMode,
            title = "Sleep Timer",
            subtitle = "Automatically pause playback after timer",
            isDark = isDark,
            onClick = onOpenSleepTimer
        )

        OptionRowItem(
            icon = Icons.Default.Share,
            title = "Share Song",
            subtitle = "Copy track details to clipboard",
            isDark = isDark,
            onClick = {
                clipboardManager.setText(AnnotatedString("${song.title} - ${song.primaryArtistName}"))
                Toast.makeText(context, "Copied track info to clipboard", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
                .clickable(onClick = onDismiss)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Close",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun OptionRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Real Lyrics Component with Pink Active Line Karaoke Highlight
// ─────────────────────────────────────────────────────────────

@Composable
private fun RealLyricsContent(
    lyrics: Lyrics?,
    isLoading: Boolean,
    currentPositionMs: Long,
    isDark: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B)

    if (isLoading) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    if (lyrics == null || (lyrics.lines.isEmpty() && lyrics.plainLyrics.isBlank())) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FormatQuote,
                    contentDescription = null,
                    tint = Accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No lyrics available for this song", color = textSecondary, fontSize = 15.sp)
            }
        }
        return
    }

    // Synced lyrics view
    if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
        val listState = rememberLazyListState()

        val activeIndex = remember(currentPositionMs, lyrics.lines) {
            val idx = lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        }

        LaunchedEffect(activeIndex) {
            if (activeIndex in lyrics.lines.indices) {
                listState.animateScrollToItem(activeIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.08f to Color.Black,
                            0.92f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = navBarBottom + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isActive = index == activeIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekTo(line.timeMs) }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = if (isActive) 22.sp else 18.sp,
                        lineHeight = 32.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (isActive) Accent else textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier
                            .graphicsLayer {
                                val s = if (isActive) 1.04f else 1f
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
        }
    } else {
        // Plain text lyrics view
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = lyrics.plainLyrics,
                fontSize = 17.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = textPrimary.copy(alpha = 0.88f)
            )
            Spacer(Modifier.height(navBarBottom + 24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Real Jam Components (Unified Pink Theme)
// ─────────────────────────────────────────────────────────────

@Composable
private fun RealJamIntro(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    onStart: () -> Unit,
    onJoin: (String) -> Unit
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var enterCode by remember { mutableStateOf("") }
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = navBarBottom + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(16.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
                .background(PinkBrush, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("Listen Together", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Invite friends to join a Jam. Everyone can add songs and enjoy synchronized playback.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Start Jam Button (Pink Gradient)
        val pill = RoundedCornerShape(100.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, pill, ambientColor = Accent, spotColor = Accent)
                .clip(pill)
                .background(PinkBrush)
                .clickable(onClick = onStart)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Start a Jam", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("OR JOIN WITH CODE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textSecondary, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = enterCode,
                onValueChange = { enterCode = it.take(6).uppercase() },
                placeholder = { Text("Enter 4-letter code", color = textSecondary.copy(alpha = 0.5f), fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
                    unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                ),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { if (enterCode.isNotBlank()) onJoin(enterCode) },
                enabled = enterCode.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Join", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun RealJamActive(
    modifier: Modifier = Modifier,
    jamState: JamState,
    isDark: Boolean,
    onEndOrLeave: () -> Unit,
    onRemoveSong: (Int) -> Unit,
    onReorderSong: (Int, Int) -> Unit,
    onToggleGuestReorder: (Boolean) -> Unit
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val canReorder = jamState.isHost || jamState.allowAttendeeReorder
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    Column(modifier = modifier.fillMaxWidth()) {
        // Invite code box
        val boxShape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(boxShape)
                .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                .border(1.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f), boxShape)
                .clickable {
                    jamState.sessionCode?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        Toast.makeText(context, "Code copied: $it", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TAP TO COPY INVITE CODE", fontSize = 11.sp, color = textSecondary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = jamState.sessionCode ?: "----",
                style = TextStyle(
                    brush = PinkBrush,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Participants row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("In this Jam", fontSize = 13.sp, color = textSecondary)
            Text(
                "${jamState.participants.size} listening",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            jamState.participants.forEach { user ->
                RealJamUserBubble(user = user, isDark = isDark)
            }
        }

        // Host settings: Allow guest reordering toggle
        if (jamState.isHost) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Allow guests to reorder queue",
                    fontSize = 13.sp,
                    color = textPrimary
                )
                Switch(
                    checked = jamState.allowAttendeeReorder,
                    onCheckedChange = { onToggleGuestReorder(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Shared Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Jam Queue (${jamState.queue.size})", fontSize = 13.sp, color = textSecondary)
            if (!canReorder) {
                Text("Host controls order", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.6f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (jamState.queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Queue is empty. Add songs to listen together!", color = textSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(jamState.queue) { index, s ->
                    QueueItemRow(
                        song = s,
                        active = false,
                        isDark = isDark,
                        canMoveUp = canReorder && index > 0,
                        canMoveDown = canReorder && index < jamState.queue.size - 1,
                        showReorderControls = canReorder,
                        onClick = { /* In jam, host manages play */ },
                        onMoveUp = { onReorderSong(index, index - 1) },
                        onMoveDown = { onReorderSong(index, index + 1) },
                        onRemove = { onRemoveSong(index) }
                    )
                }
            }
        }

        // Leave / End Jam button
        val pill = RoundedCornerShape(100.dp)
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(pill)
                .background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.35f), pill)
                .clickable(onClick = onEndOrLeave)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (jamState.isHost) "End Jam" else "Leave Jam",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent
            )
        }
        Spacer(Modifier.height(navBarBottom + 24.dp))
    }
}

@Composable
private fun RealJamUserBubble(user: JamParticipantUi, isDark: Boolean) {
    Column(
        modifier = Modifier.widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(if (user.isHost) Accent else Accent.copy(alpha = 0.7f), CircleShape)
                .padding(2.dp)
        ) {
            if (user.avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E1824) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 15.sp
                    )
                }
            } else {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
        Text(
            text = if (user.isHost) "${user.username} (Host)" else user.username,
            fontSize = 11.sp,
            color = if (user.isHost) Accent else (if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Queue item row with Move Up, Move Down, and Dismiss
// ─────────────────────────────────────────────────────────────

@Composable
private fun QueueItemRow(
    song: Song,
    active: Boolean,
    isDark: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    showReorderControls: Boolean = true,
    onClick: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (active) Accent.copy(alpha = 0.38f) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Accent else textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.primaryArtistName,
                fontSize = 13.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (active) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = "Playing",
                tint = Accent,
                modifier = Modifier.size(22.dp)
            )
        } else if (showReorderControls) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canMoveUp) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = textSecondary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Sleep timer card with Glassmorphic finish & Swipe Downward Dismiss
// ─────────────────────────────────────────────────────────────

@Composable
private fun SleepTimerCard(
    selected: String,
    remainingSeconds: Long?,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(28.dp)

    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF241829).copy(alpha = 0.94f),
                Color(0xFF15101A).copy(alpha = 0.98f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.98f),
                Color(0xFFF9F5F8).copy(alpha = 0.98f)
            )
        )
    }

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Accent.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.12f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Accent.copy(alpha = 0.45f),
                Color.Black.copy(alpha = 0.08f)
            )
        )
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 100f) onCancel()
                        dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        if (dragOffsetY > 200f) {
                            onCancel()
                            dragOffsetY = 0f
                        }
                    }
                )
            }
            .fillMaxWidth(0.85f)
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)
    ) {
        // Drag handle pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 14.dp)
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f)
                )
        )

        Text(
            text = "Stop audio after",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (remainingSeconds != null && selected != "Off") {
            Spacer(modifier = Modifier.height(6.dp))
            val isEndOfTrack = selected == "End of track"
            Text(
                text = if (isEndOfTrack) {
                    "Active: ${formatTimerRemaining(remainingSeconds)} remaining (End of track)"
                } else {
                    "Active: ${formatTimerRemaining(remainingSeconds)} remaining"
                },
                fontSize = 13.sp,
                color = Accent,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SleepOptions.forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { label ->
                    TimerButton(
                        label = label,
                        isSelected = selected == label,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Accent.copy(alpha = 0.15f))
                .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dismiss",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun TimerButton(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val unselectedBg = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
    val textColor = if (isSelected) Accent else (if (isDark) Color.White else Color(0xFF0F172A))

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) Accent.copy(alpha = 0.20f) else unselectedBg)
            .border(1.dp, if (isSelected) Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Ambient background with Light / Dark Mode Handling
// ─────────────────────────────────────────────────────────────

@Composable
private fun AmbientBackground(imageUrl: String?, isDark: Boolean) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val transition = rememberInfiniteTransition(label = "ambient")
    val driftScale by transition.animateFloat(
        initialValue = 1.3f,
        targetValue = 1.37f,
        animationSpec = infiniteRepeatable(tween(15000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftScale"
    )
    val driftAlpha by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(2.5f) }),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = driftScale
                    scaleY = driftScale
                    alpha = if (canBlur) driftAlpha else 0.25f
                }
                .then(if (canBlur) Modifier.blur(60.dp) else Modifier)
        )
        
        if (isDark) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF0F0F13).copy(alpha = 0.35f),
                            0.55f to Color(0xFF0F0F13).copy(alpha = 0.85f),
                            0.82f to Color(0xFF0F0F13),
                            1f to Color(0xFF0F0F13)
                        )
                    )
            )
        } else {
            // Light mode overlay with soft frosted pastel tint
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.70f)))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.50f),
                            0.55f to Color.White.copy(alpha = 0.85f),
                            0.82f to Color(0xFFF9F7FA),
                            1f to Color(0xFFF9F7FA)
                        )
                    )
            )
        }
    }
}

@Composable
private fun TopBarButton(
    icon: ImageVector,
    description: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (isDark) Color.White else Color(0xFF0F172A),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerProgressBar(
    progress: Float,
    isDark: Boolean,
    onSeek: (Float) -> Unit
) {
    val p = progress.coerceIn(0f, 1f)
    val trackBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackBg)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(p)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Accent)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Sliding Glassmorphic Panel with Swipe Downward to Dismiss
// ─────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.BottomPanel(
    visible: Boolean,
    title: String,
    isDark: Boolean,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (!visible) dragOffsetY = 0f
    }

    val glassShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    val panelBg = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF201625).copy(alpha = 0.94f),
                Color(0xFF131018).copy(alpha = 0.98f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.97f),
                Color(0xFFF9F5F8).copy(alpha = 0.98f)
            )
        )
    }

    val panelBorder = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Accent.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Accent.copy(alpha = 0.45f),
                Color.Black.copy(alpha = 0.08f)
            )
        )
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (dragOffsetY > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-dragOffsetY)
                    dragOffsetY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f) {
                    dragOffsetY = (dragOffsetY + available.y).coerceAtLeast(0f)
                    if (dragOffsetY > 200f) {
                        onClose()
                        dragOffsetY = 0f
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragOffsetY > 90f || available.y > 600f) {
                    onClose()
                }
                dragOffsetY = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.5f else 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, _ ->
                            change.consume()
                        }
                    )
                }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 100f) {
                            onClose()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Only allow dragging downwards
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        if (dragOffsetY > 240f) {
                            onClose()
                            dragOffsetY = 0f
                        }
                    }
                )
            },
        enter = slideInVertically(tween(350)) { it },
        exit = slideOutVertically(tween(350)) { it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(glassShape)
                .background(panelBg)
                .border(1.dp, panelBorder, glassShape)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp)
        ) {
            // Drag handle pill at the very top (swipeable downwards indicator)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.20f)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = if (isDark) Color.White else Color(0xFF0F172A),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    return formatDuration(totalSeconds)
}

private fun formatTimerRemaining(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}