package com.synq.features.music

import android.os.Build
import androidx.activity.compose.BackHandler
import com.synq.features.music.components.SongOptionsSheet
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
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
import com.synq.features.music.domain.model.Lyrics
import com.synq.features.music.domain.model.Song
import com.synq.features.music.jam.JamParticipantUi
import com.synq.features.music.jam.JamState
import com.synq.features.music.playback.PlaybackState
import android.content.Intent
import android.widget.Toast
import com.synq.features.music.components.AnimatedEqualizerBars
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.isAppDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Unified Pink Design Tokens ───
val BrandPink = Color(0xFFFF2A5F)
val Accent = Color(0xFFFF3366)

// Cohesive vibrant Pink Gradient used across all tabs & actions
val PinkGradient = Brush.linearGradient(
    listOf(Color(0xFFFF2A5F), Color(0xFFFF5E8A), Color(0xFFE11D48))
)
val PinkBrush = PinkGradient

private enum class PlayerPanel { Queue, Lyrics }

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
    jamUiState: com.synq.features.music.jam.JamUiState = com.synq.features.music.jam.JamUiState(),
    localPlaybackState: com.synq.features.music.jam.LocalPlaybackState = com.synq.features.music.jam.LocalPlaybackState.Synchronized,
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
    onStepOutLocally: () -> Unit = {},
    onResumeFromLocalStepOut: () -> Unit = {},
    onPlayItemByQid: (String) -> Unit = {},
    onPromoteRecommended: (String) -> Unit = {},
    onRemoveByQid: (String) -> Unit = {},
    onMoveByQid: (String, Int) -> Unit = { _, _ -> },
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    recommendedSongIds: Set<String> = emptySet(),
    onAddToPlaylist: (Song) -> Unit = {},
    onSongLike: (Song) -> Unit = {},
    onNavigateToJam: () -> Unit = {},
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
    var showJamIntro by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var selectedQueueSongForOptions by remember { mutableStateOf<Song?>(null) }
    val hostName = jamUiState.hostName.ifBlank { jamState.hostName.ifBlank { "Host" } }
    val canControlPlayback = !jamState.isInJam || jamUiState.canControlPlayback

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
                            text = if (jamState.isInJam) "JAM • ${jamState.sessionCode ?: "SESSION"}" else "NOW PLAYING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (jamState.isInJam) Accent else textSecondary,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Provider Info Badge (JioSaavn / YouTube Music) placed below NOW PLAYING
                        val isJioSaavn = song.providerId.contains("saavn", ignoreCase = true)
                        val providerName = if (isJioSaavn) "JioSaavn" else "YouTube Music"
                        val providerIcon = if (isJioSaavn) com.synq.R.drawable.saavn else com.synq.R.drawable.yt

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

                    val canShuffle = !jamState.isInJam
                    val canRepeat = !jamState.isInJam
                    val isStepOut = localPlaybackState is com.synq.features.music.jam.LocalPlaybackState.LocalStepOut

                    // Progress bar
                    PlayerProgressBar(
                        progress = playbackState.progress,
                        onSeek = { ratio ->
                            if (!canControlPlayback) {
                                Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                            } else {
                                onSeek(ratio)
                            }
                        },
                        isDark = isDark,
                        enabled = canControlPlayback,
                        onDisabledClick = {
                            Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                        }
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
                        IconButton(
                            onClick = {
                                if (!canShuffle) {
                                    Toast.makeText(context, "Shuffle is managed by the Jam session", Toast.LENGTH_SHORT).show()
                                } else {
                                    val willBeShuffle = !isShuffle
                                    onToggleShuffle()
                                    Toast.makeText(context, if (willBeShuffle) "Shuffle on" else "Shuffle off", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.alpha(if (canShuffle) 1f else 0.38f)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle && canShuffle) Accent else textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!canControlPlayback) {
                                    Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                } else {
                                    onPreviousClick()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .alpha(if (canControlPlayback) 1f else 0.38f)
                        ) {
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
                                .clickable {
                                    if (canControlPlayback) {
                                        onPlayPauseClick()
                                    } else {
                                        if (isStepOut) {
                                            onResumeFromLocalStepOut()
                                        } else {
                                            onStepOutLocally()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val displayPlaying = if (isStepOut) false else playbackState.isPlaying
                            Icon(
                                imageVector = if (displayPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (displayPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(36.dp)
                                    .offset(x = if (displayPlaying) 0.dp else 2.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!canControlPlayback) {
                                    Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                } else {
                                    onNextClick()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .alpha(if (canControlPlayback) 1f else 0.38f)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = textPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        // Repeat toggle: binary OFF vs ON (loops current song)
                        IconButton(
                            onClick = {
                                if (!canRepeat) {
                                    Toast.makeText(context, "Repeat is disabled during a Jam session", Toast.LENGTH_SHORT).show()
                                } else {
                                    val willBeRepeat = !isRepeatOne
                                    onToggleRepeat()
                                    Toast.makeText(context, if (willBeRepeat) "Repeat track on" else "Repeat off", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.alpha(if (canRepeat) 1f else 0.38f)
                        ) {
                            Icon(
                                Icons.Default.RepeatOne,
                                contentDescription = "Repeat",
                                tint = if (isRepeatOne && canRepeat) Accent else textSecondary,
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
                        onJamClick = {
                            if (jamState.isInJam) {
                                onDismiss()
                                onNavigateToJam()
                            } else {
                                showJamIntro = true
                            }
                        },
                        onQueueClick = { togglePanel(PlayerPanel.Queue) }
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // ── Queue panel (Glassmorphic, Swipeable Downwards & Light/Dark) ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Queue,
                title = if (jamState.isInJam) "Jam Queue (${jamUiState.queue.size} songs)" else "Queue (${queue.size} songs)",
                isDark = isDark,
                onClose = { openPanel = null }
            ) {
                if (jamState.isInJam) {
                    if (jamUiState.queue.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Jam queue is empty", color = textSecondary, fontSize = 15.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = navBarBottom + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // User queue items
                            itemsIndexed(jamUiState.userQueue, key = { _, item -> item.qid }) { index, item ->
                                val isPlayingThis = item.qid == jamUiState.nowPlaying.item?.qid
                                val canReorder = jamUiState.canManageQueue
                                val canRemove = item.addedByUserId == jamUiState.myUserId || jamUiState.canManageQueue

                                QueueItemRow(
                                    song = item.song,
                                    active = isPlayingThis,
                                    isDark = isDark,
                                    isPlaying = isPlayingThis && playbackState.isPlaying,
                                    isRecommended = false,
                                    canMoveUp = canReorder && index > 0,
                                    canMoveDown = canReorder && index < jamUiState.userQueue.size - 1,
                                    onClick = {
                                        if (canControlPlayback) {
                                            onPlayItemByQid(item.qid)
                                        } else {
                                            Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onMoveUp = { onMoveByQid(item.qid, index - 1) },
                                    onMoveDown = { onMoveByQid(item.qid, index + 1) },
                                    onRemove = {
                                        if (canRemove) {
                                            onRemoveByQid(item.qid)
                                        } else {
                                            Toast.makeText(context, "Ask $hostName for queue manage permission", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOptionsClick = { selectedQueueSongForOptions = item.song }
                                )
                            }

                            // Recommended section
                            if (jamUiState.recommendedQueue.isNotEmpty()) {
                                item(key = "rec_jam_separator") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RECOMMENDED FOR THIS JAM",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Accent,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                                        )
                                    }
                                }

                                items(jamUiState.recommendedQueue, key = { it.qid }) { item ->
                                    val isPlayingThis = item.qid == jamUiState.nowPlaying.item?.qid
                                    QueueItemRow(
                                        song = item.song,
                                        active = isPlayingThis,
                                        isDark = isDark,
                                        isPlaying = isPlayingThis && playbackState.isPlaying,
                                        isRecommended = true,
                                        canMoveUp = false,
                                        canMoveDown = false,
                                        onClick = {
                                            if (canControlPlayback) {
                                                onPlayItemByQid(item.qid)
                                            } else {
                                                Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onMoveUp = {},
                                        onMoveDown = {},
                                        onRemove = { onRemoveByQid(item.qid) },
                                        onOptionsClick = { selectedQueueSongForOptions = item.song }
                                    )
                                }
                            }
                        }
                    }
                } else if (queue.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Queue is empty", color = textSecondary, fontSize = 15.sp)
                    }
                } else {
                    val firstRecommendedIdx = queue.indexOfFirst { it.id in recommendedSongIds }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = navBarBottom + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { index, item ->
                            val isPlayingThis = index == currentIndex
                            val isRec = item.id in recommendedSongIds

                            // Visual separator before first recommended song
                            if (index == firstRecommendedIdx) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECOMMENDED",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Accent,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            QueueItemRow(
                                song = item,
                                active = isPlayingThis,
                                isDark = isDark,
                                isPlaying = isPlayingThis && playbackState.isPlaying,
                                isRecommended = isRec,
                                canMoveUp = index > 0 && queue[index - 1].id !in recommendedSongIds,
                                canMoveDown = index < queue.size - 1 && queue[index + 1].id !in recommendedSongIds,
                                onClick = { onPlayQueueIndex(index) },
                                onMoveUp = { onReorderQueue(index, index - 1) },
                                onMoveDown = { onReorderQueue(index, index + 1) },
                                onRemove = { onRemoveQueueItem(index) },
                                onOptionsClick = { selectedQueueSongForOptions = item }
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

            // ── Jam Intro Dialog (Popup) ──
            if (showJamIntro) {
                com.synq.features.music.jam.JamIntroDialog(
                    onDismiss = { showJamIntro = false },
                    onStartJam = { onComplete ->
                        onStartJam()
                        onComplete(Result.success(Unit))
                    },
                    onJoinJam = { code, onComplete ->
                        onJoinJam(code)
                        onComplete(Result.success(Unit))
                    },
                    onSuccessNav = {
                        showJamIntro = false
                        onDismiss()
                        onNavigateToJam()
                    }
                )
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
                            if (jamState.isInJam) {
                                onDismiss()
                                onNavigateToJam()
                            } else {
                                showJamIntro = true
                            }
                        },
                        onOpenSleepTimer = {
                            showMoreOptions = false
                            showSleepTimer = true
                        },
                        onAddToPlaylist = {
                            onAddToPlaylist(song)
                            showMoreOptions = false
                        },
                        onDismiss = { showMoreOptions = false }
                    )
                }
            }

            // Song options bottom sheet for recommended queue songs
            selectedQueueSongForOptions?.let { recSong ->
                SongOptionsSheet(
                    song = recSong,
                    isInJam = jamState.isInJam,
                    onPlayNext = {
                        onPlayNext(recSong)
                        selectedQueueSongForOptions = null
                    },
                    onAddToQueue = {
                        onAddToQueue(recSong)
                        selectedQueueSongForOptions = null
                    },
                    onAddToPlaylist = {
                        onAddToPlaylist(recSong)
                        selectedQueueSongForOptions = null
                    },
                    onLike = {
                        onSongLike(recSong)
                        selectedQueueSongForOptions = null
                    },
                    onDismiss = { selectedQueueSongForOptions = null }
                )
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
            active = jamState.isInJam,
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
    onAddToPlaylist: () -> Unit = {},
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
            icon = Icons.Default.PlaylistAdd,
            title = "Add to Playlist",
            subtitle = "Save track to your custom playlist",
            isDark = isDark,
            onClick = onAddToPlaylist
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
            subtitle = "Share via apps or copy link",
            isDark = isDark,
            onClick = {
                val shareUrl = "https://21yash.online/track/${song.id}"
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                onDismiss()
            }
        )

        val localOnNavigateToSongDetails = com.synq.features.music.components.LocalOnNavigateToSongDetails.current
        OptionRowItem(
            icon = Icons.Default.Info,
            title = "Song Details",
            subtitle = "View credits, lyrics & audio format info",
            isDark = isDark,
            onClick = {
                onDismiss()
                localOnNavigateToSongDetails(song.id, song.providerId)
            }
        )

        if (song.album != null && song.album.id.isNotBlank() && song.providerId != "youtube") {
            val localOnNavigateToAlbum = com.synq.features.music.components.LocalOnNavigateToAlbum.current
            OptionRowItem(
                icon = Icons.Default.Album,
                title = "Go to Album",
                subtitle = if (song.album.title.isNotBlank()) "View album \"${song.album.title}\"" else "View full album",
                isDark = isDark,
                onClick = {
                    onDismiss()
                    localOnNavigateToAlbum(song.album.id)
                }
            )
        }

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
            SynqLoadingIndicator(size = 40.dp, barCount = 5)
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
// Queue item row with Move Up, Move Down, and Dismiss
// ─────────────────────────────────────────────────────────────

@Composable
private fun QueueItemRow(
    song: Song,
    active: Boolean,
    isDark: Boolean,
    isPlaying: Boolean = true,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    showReorderControls: Boolean = true,
    isRecommended: Boolean = false,
    onClick: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemove: () -> Unit = {},
    onOptionsClick: (() -> Unit)? = null
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
            AnimatedEqualizerBars(
                color = Accent,
                isPlaying = isPlaying,
                barCount = 3,
                modifier = Modifier.padding(end = 4.dp)
            )
        } else if (isRecommended) {
            // Recommended song controls: Three dots menu + cross to remove (no reorder)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onOptionsClick?.invoke() }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove from Queue",
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
    onSeek: (Float) -> Unit,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null
) {
    val p = progress.coerceIn(0f, 1f)
    val trackBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (enabled) {
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    } else {
                        onDisabledClick?.invoke()
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures { change, _ ->
                        onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
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