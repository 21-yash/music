package com.musicsportsapp.features.music

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicsportsapp.features.music.playback.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Kept public because other files in this package may reference them.
val BrandPink = Color(0xFFFF2A5F)
val DarkBackground = Color(0xFF18181A)
val SheetBackground = Color(0xFF222226)

// ─── Design tokens from player-prototype.html ───
private val BgColor = Color(0xFF0F0F13)                      // --bg-color
private val DeviceBg = Color(0xFF121216)                     // .device-container
private val Accent = Color(0xFFFF3366)                       // --primary-accent
private val SurfaceColor = Color.White.copy(alpha = 0.05f)   // --surface-color
private val GlassBorder = Color.White.copy(alpha = 0.10f)    // --glass-border
private val TextSecondary = Color.White.copy(alpha = 0.60f)  // --text-secondary
private val TimerModalBg = Color(0xFF1C1C24)
private val JamBlue = Color(0xFF3B82F6)
private val JamBrush = Brush.linearGradient(
    listOf(Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFFA855F7))
)

private enum class PlayerPanel { Queue, Lyrics, Jam }

// ─── Placeholder data (same as the prototype) ───
private data class QueueEntry(val title: String, val artist: String, val imageUrl: String)
private data class JamUserUi(val name: String, val imageUrl: String, val color: Color)
private data class JamSongUi(val title: String, val artist: String, val imageUrl: String, val addedBy: String)

private val MockQueue = listOf(
    QueueEntry("Softly", "Karan Aujla",
        "https://c.saavncdn.com/581/Softly-Punjabi-2023-20230810234125-150x150.jpg"),
    QueueEntry("Tauba Tauba", "Karan Aujla, Badshah",
        "https://c.saavncdn.com/284/Tauba-Tauba-From-Bad-Newz-Hindi-2024-20240702133827-150x150.jpg"),
    QueueEntry("O Maahi", "Arijit Singh",
        "https://c.saavncdn.com/955/O-Maahi-From-Dunki-Hindi-2023-20231211171007-150x150.jpg"),
    QueueEntry("Chaleya", "Arijit Singh, Shilpa Rao",
        "https://c.saavncdn.com/191/Chaleya-From-Jawan-Hindi-2023-20230814014337-150x150.jpg")
)

private val MockJamUsers = listOf(
    JamUserUi("You", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80", Color(0xFFEC4899)),
    JamUserUi("Alex", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=100&q=80", Color(0xFF3B82F6)),
    JamUserUi("Sam", "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?w=100&q=80", Color(0xFF10B981))
)

private val MockJamSongs = listOf(
    JamSongUi("Starboy", "The Weeknd",
        "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=150&q=80", "Alex"),
    JamSongUi("Blinding Lights", "The Weeknd",
        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=150&q=80", "Sam"),
    JamSongUi("Softly", "Karan Aujla",
        "https://c.saavncdn.com/581/Softly-Punjabi-2023-20230810234125-150x150.jpg", "You")
)

private val MockLyrics = listOf(
    "Kyun mere hatho mein khinche",
    "Lakeer tere naam ki",
    "Jeet ke bhi haari hui",
    "Baazi mere kaam ki",
    "Tu mila hai jabse mujhe",
    "Main khud ko bhi bhul gaya",
    "Dil ye mera tere hawaale"
)

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
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val song = playbackState.currentSong ?: return

    // Which in-player panel is open (Queue / Lyrics / Jam), plus the sleep-timer popup
    var openPanel by remember { mutableStateOf<PlayerPanel?>(null) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var sleepSelected by remember { mutableStateOf("Off") }

    // Jam state (UI-only)
    var isJamActive by remember { mutableStateOf(false) }
    var jamCode by remember { mutableStateOf("X7K9") }

    // Other UI-only toggles
    var liked by remember { mutableStateOf(false) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatOn by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun togglePanel(panel: PlayerPanel) {
        openPanel = if (openPanel == panel) null else panel
    }

    // Main content is pushed back (scale / blur / dim) while a panel is open
    val depth by animateFloatAsState(
        targetValue = if (openPanel != null) 1f else 0f,
        animationSpec = tween(400),
        label = "contentDepth"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxSize(),
        containerColor = DeviceBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            AmbientBackground(imageUrl = song.imageUrl)

            // ── Main player content ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.1f * depth
                        scaleX = s
                        scaleY = s
                        translationY = -20.dp.toPx() * depth
                    }
                    .then(if (depth > 0f) Modifier.blur((5 * depth).dp) else Modifier)
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.4f * depth)) // brightness(0.6)
                    }
                    .navigationBarsPadding()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TopBarButton(Icons.Default.KeyboardArrowDown, "Close", onDismiss)
                    Text(
                        text = "NOW PLAYING",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        letterSpacing = 2.sp
                    )
                    TopBarButton(Icons.Default.MoreHoriz, "More") { /* TODO */ }
                }

                // Everything below hugs the bottom, like `justify-content: flex-end`
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album art: fills leftover height, always square, grows slightly while playing
                    val artScale by animateFloatAsState(
                        targetValue = if (playbackState.isPlaying) 1.02f else 1f,
                        animationSpec = tween(400),
                        label = "artScale"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 40.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val artShape = RoundedCornerShape(24.dp)
                        AsyncImage(
                            model = song.imageUrl,
                            contentDescription = "Album art for ${song.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .graphicsLayer { scaleX = artScale; scaleY = artScale }
                                .shadow(20.dp, artShape, ambientColor = Color.Black, spotColor = Color.Black)
                                .clip(artShape)
                                .background(SurfaceColor)
                        )
                    }

                    // Song info + like
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = song.primaryArtistName,
                                fontSize = 18.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        IconButton(onClick = { liked = !liked }) {
                            Icon(
                                imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (liked) "Unlike" else "Like",
                                tint = Accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Progress
                    PlayerProgressBar(progress = playbackState.progress, onSeek = onSeek)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration((playbackState.progress * song.durationSeconds).toInt()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = formatDuration(song.durationSeconds),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { shuffleOn = !shuffleOn }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleOn) Accent else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onPreviousClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(
                                    16.dp, CircleShape,
                                    ambientColor = Accent.copy(alpha = 0.4f),
                                    spotColor = Accent.copy(alpha = 0.4f)
                                )
                                .clip(CircleShape)
                                .background(Accent)
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(38.dp)
                                    // visual centering for the play triangle, like margin-left: 4px
                                    .offset(x = if (playbackState.isPlaying) 0.dp else 2.dp)
                            )
                        }
                        IconButton(onClick = onNextClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = { repeatOn = !repeatOn }) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatOn) Accent else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Bottom actions (top hairline, no card)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.05f),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BottomActionItem(
                            icon = Icons.Default.FormatQuote, label = "LYRICS",
                            active = openPanel == PlayerPanel.Lyrics
                        ) { togglePanel(PlayerPanel.Lyrics) }
                        BottomActionItem(
                            icon = Icons.Default.DarkMode, label = "SLEEP",
                            active = showSleepTimer
                        ) { showSleepTimer = !showSleepTimer }
                        BottomActionItem(
                            icon = Icons.Default.Groups, label = "JAM",
                            active = openPanel == PlayerPanel.Jam,
                            gradient = isJamActive
                        ) { togglePanel(PlayerPanel.Jam) }
                        BottomActionItem(
                            icon = Icons.Default.QueueMusic, label = "QUEUE",
                            active = openPanel == PlayerPanel.Queue
                        ) { togglePanel(PlayerPanel.Queue) }
                    }
                }
            }

            // ── Queue panel ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Queue,
                title = "Up Next",
                onClose = { openPanel = null }
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        QueueRow(
                            title = song.title,
                            subtitle = song.primaryArtistName,
                            imageUrl = song.imageUrl,
                            active = true
                        )
                    }
                    items(MockQueue) { entry ->
                        QueueRow(
                            title = entry.title,
                            subtitle = entry.artist,
                            imageUrl = entry.imageUrl,
                            active = false
                        )
                    }
                }
            }

            // ── Lyrics panel ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Lyrics,
                title = "Lyrics",
                onClose = { openPanel = null }
            ) {
                LyricsContent(modifier = Modifier.weight(1f))
            }

            // ── Jam panel ──
            BottomPanel(
                visible = openPanel == PlayerPanel.Jam,
                title = if (isJamActive) "Active Jam" else "Start a Jam",
                onClose = { openPanel = null }
            ) {
                if (!isJamActive) {
                    JamIntro(
                        modifier = Modifier.weight(1f),
                        onStart = {
                            val chars = ('A'..'Z') + ('0'..'9')
                            jamCode = List(4) { chars.random() }.joinToString("")
                            isJamActive = true
                        }
                    )
                } else {
                    JamActive(
                        modifier = Modifier.weight(1f),
                        code = jamCode,
                        onEnd = {
                            isJamActive = false
                            openPanel = null
                        }
                    )
                }
            }

            // ── Sleep timer popup ──
            // Fully qualified: inside ModalBottomSheet's ColumnScope the plain name resolves to
            // the ColumnScope overload and fails with "cannot be called in this context".
            androidx.compose.animation.AnimatedVisibility(
                visible = showSleepTimer,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showSleepTimer = false },
                    contentAlignment = Alignment.Center
                ) {
                    SleepTimerCard(
                        selected = sleepSelected,
                        onSelect = { label ->
                            sleepSelected = label
                            scope.launch {
                                delay(300)
                                showSleepTimer = false
                            }
                        },
                        onCancel = { showSleepTimer = false }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Background + top bar + progress
// ─────────────────────────────────────────────────────────────

/** Blurred, saturated album art that slowly drifts, fading into the base color. */
@Composable
private fun AmbientBackground(imageUrl: String?) {
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
            // saturate(250%)
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(2.5f) }),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = driftScale
                    scaleY = driftScale
                    // blur() needs Android 12+, so on older versions keep the image very faint
                    alpha = if (canBlur) driftAlpha else 0.25f
                }
                .then(if (canBlur) Modifier.blur(60.dp) else Modifier)
        )
        // brightness(0.7)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        // overlay: top 0.3 → 60% 0.85 → bottom 1.0
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to BgColor.copy(alpha = 0.3f),
                        0.6f to BgColor.copy(alpha = 0.85f),
                        1f to BgColor
                    )
                )
        )
    }
}

@Composable
private fun TopBarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** 6dp white seek bar. Supports tap and drag. */
@Composable
private fun PlayerProgressBar(progress: Float, onSeek: (Float) -> Unit) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp) // larger touch target around the 6dp bar
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
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(p)
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Bottom action items
// ─────────────────────────────────────────────────────────────

@Composable
private fun BottomActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    gradient: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (active) Color.White else TextSecondary
    val labelStyle = if (gradient) {
        TextStyle(brush = JamBrush, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    } else {
        TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // the label below already describes it
            tint = if (gradient) Color.White else color,
            modifier = Modifier
                .size(22.dp)
                .then(if (gradient) Modifier.gradientTint(JamBrush) else Modifier)
        )
        Text(text = label, style = labelStyle)
    }
}

/** Paints a gradient over an icon's opaque pixels (used for the active Jam button). */
private fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

// ─────────────────────────────────────────────────────────────
// Sliding panels (Queue / Lyrics / Jam)
// ─────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.BottomPanel(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.75f),
        enter = slideInVertically(tween(400)) { it },
        exit = slideOutVertically(tween(400)) { it }
    ) {
        val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                // no backdrop blur in Compose, so the glass is a bit more opaque
                .background(Color(0xFF121216).copy(alpha = 0.94f))
                .border(1.dp, GlassBorder, shape)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceColor)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun QueueRow(
    title: String,
    imageUrl: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleAnnotated: AnnotatedString? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Accent.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (active) Accent.copy(alpha = 0.2f) else Color.Transparent, shape)
            .clickable { /* TODO */ }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Accent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (subtitleAnnotated != null) {
                Text(
                    text = subtitleAnnotated,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = if (active) Icons.Default.BarChart else Icons.Default.DragHandle,
            contentDescription = null,
            tint = if (active) Accent else TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun LyricsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // fade the top and bottom 10% like the prototype's mask-image
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.1f to Color.Black,
                        0.9f to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MockLyrics.forEachIndexed { index, line ->
            val isActive = index == 2 // placeholder until live lyrics are wired
            Text(
                text = line,
                fontSize = 22.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .graphicsLayer {
                        val s = if (isActive) 1.05f else 1f
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Jam
// ─────────────────────────────────────────────────────────────

@Composable
private fun JamIntro(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(16.dp, CircleShape, ambientColor = JamBlue, spotColor = JamBlue)
                .background(JamBrush, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Listen Together", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Invite friends to join a Jam. Everyone can add songs and control the music together.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        val pill = RoundedCornerShape(100.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, pill, ambientColor = JamBlue, spotColor = JamBlue)
                .clip(pill)
                .background(JamBrush)
                .clickable(onClick = onStart)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Start a Jam", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun JamActive(modifier: Modifier = Modifier, code: String, onEnd: () -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Invite code
        val boxShape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(boxShape)
                .background(SurfaceColor)
                .border(1.dp, GlassBorder, boxShape)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("INVITE CODE", fontSize = 14.sp, color = TextSecondary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = code,
                style = TextStyle(
                    brush = JamBrush,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Users
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("In this Jam", fontSize = 15.sp, color = TextSecondary)
            Text("3 friends joined", fontSize = 15.sp, color = Color(0xFF10B981))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MockJamUsers.forEach { user -> JamUserBubble(user) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Songs added by others
        Text("Added by others", fontSize = 15.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MockJamSongs) { s ->
                QueueRow(
                    title = s.title,
                    imageUrl = s.imageUrl,
                    active = false,
                    subtitleAnnotated = buildAnnotatedString {
                        append(s.artist)
                        append(" • ")
                        withStyle(SpanStyle(color = JamBlue)) { append("Added by ${s.addedBy}") }
                    }
                )
            }
        }

        // End Jam
        val pill = RoundedCornerShape(100.dp)
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(pill)
                .background(Accent.copy(alpha = 0.1f))
                .border(1.dp, Accent.copy(alpha = 0.3f), pill)
                .clickable(onClick = onEnd)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("End Jam", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        }
    }
}

@Composable
private fun JamUserBubble(user: JamUserUi) {
    Column(
        modifier = Modifier.widthIn(min = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(user.color, CircleShape)
                .padding(2.dp)
        ) {
            AsyncImage(
                model = user.imageUrl,
                contentDescription = user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(2.dp, BgColor, CircleShape)
            )
        }
        Text(user.name, fontSize = 11.sp, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────
// Sleep timer popup
// ─────────────────────────────────────────────────────────────

@Composable
private fun SleepTimerCard(
    selected: String,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .clip(shape)
            .background(TimerModalBg)
            .border(1.dp, GlassBorder, shape)
            // swallow taps so they don't reach the dimmed backdrop (which closes the popup)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .padding(24.dp)
    ) {
        Text(
            text = "Stop audio after",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )

        SleepOptions.forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { label ->
                    TimerButton(
                        label = label,
                        isSelected = selected == label,
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clickable(onClick = onCancel)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BgColor)
        }
    }
}

@Composable
private fun TimerButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) Accent.copy(alpha = 0.15f) else SurfaceColor)
            .border(1.dp, if (isSelected) Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Accent else Color.White
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}