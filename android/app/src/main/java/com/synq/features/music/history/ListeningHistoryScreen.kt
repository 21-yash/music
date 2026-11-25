package com.synq.features.music.history

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.data.local.db.entity.RecentlyPlayedEntity
import com.synq.features.music.MusicViewModel
import com.synq.features.music.components.LocalAddToPlaylist
import com.synq.features.music.components.SongOptionsSheet
import com.synq.features.music.domain.model.AlbumRef
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.Song
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningHistoryScreen(
    viewModel: ListeningHistoryViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
) {
    val addToPlaylist = LocalAddToPlaylist.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()

    val playbackState by musicViewModel.playerManager.playbackState.collectAsState()
    val isMiniPlayerPresent = playbackState.currentSong != null
    val lazyBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerPresent) 160.dp else 100.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lazyBottomPadding"
    )

    val likedSongIds = remember(likedSongs) { likedSongs.map { it.songId }.toSet() }
    val groupedHistory = remember(recentlyPlayed) {
        recentlyPlayed.groupBy { formatDateHeader(it.playedAt) }
    }
    var showClearDialog by remember { mutableStateOf(false) }

    val backScale = remember { Animatable(1f) }
    val dustbinScale = remember { Animatable(1f) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Clear History",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear your entire listening history?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearHistory()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = CircleShape
                ) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    shape = CircleShape
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pressable(
                            pressedScale = 0.80f,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${recentlyPlayed.size} songs played",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = recentlyPlayed.isNotEmpty(),
                    enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f),
                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.7f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pressable(
                                pressedScale = 0.80f,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showClearDialog = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedContent(
            targetState = recentlyPlayed.isEmpty(),
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(400, delayMillis = 100)) + scaleIn(
                        initialScale = 0.90f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )).togetherWith(fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { -it / 8 }))
                } else {
                    (fadeIn(tween(350)) + slideInVertically(initialOffsetY = { it / 8 }))
                        .togetherWith(fadeOut(tween(200)))
                }
            },
            label = "HistoryContentAnimation",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { isEmpty ->
            if (isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PinkPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = PinkPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "No listening history yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Songs you listen to will be recorded here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedHistory.forEach { (dateHeader, itemsForDate) ->
                        item(key = "header_$dateHeader") {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        fadeOutSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    .padding(top = 16.dp, bottom = 4.dp)
                            )
                        }

                        items(itemsForDate, key = { it.id }) { item ->
                            val isLiked = likedSongIds.contains(item.songId)
                            val songModel = remember(item) {
                                Song(
                                    id = item.songId,
                                    title = item.title,
                                    artists = listOf(ArtistCredit(id = item.artist, name = item.artist, imageUrl = null)),
                                    album = item.albumId?.takeIf { it.isNotBlank() }?.let { AlbumRef(id = it) },
                                    durationSeconds = item.durationSeconds,
                                    imageUrl = item.imageUrl,
                                    year = "",
                                    language = "",
                                    hasLyrics = false,
                                    playCount = 0,
                                    label = "",
                                    streamRef = item.songId,
                                    providerId = item.providerId,
                                )
                            }

                            HistorySongItem(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                item = item,
                                song = songModel,
                                isLiked = isLiked,
                                onPlay = { viewModel.playRecentlyPlayed(item) },
                                onPlayNext = { viewModel.playNext(songModel) },
                                onAddToQueue = { viewModel.addToQueue(songModel) },
                                onToggleLike = {
                                    viewModel.toggleLike(songModel)
                                    Toast.makeText(
                                        context,
                                        if (isLiked) "Removed from Liked Songs" else "Added to Liked Songs",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onAddToPlaylist = {
                                    addToPlaylist(songModel)
                                },
                                onDelete = {
                                    viewModel.deleteEntry(item.id)
                                    Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(lazyBottomPadding))
                    }
                }
            }
        }
    }
}

private fun formatDateHeader(timestamp: Long): String {
    return try {
        val playDate = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)

        when (playDate) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")
                playDate.format(formatter)
            }
        }
    } catch (_: Exception) {
        "Recent"
    }
}

@Composable
private fun HistorySongItem(
    modifier: Modifier = Modifier,
    item: RecentlyPlayedEntity,
    song: Song,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showOptions by remember { mutableStateOf(false) }

    val crossScale = remember { Animatable(1f) }
    val dotsScale = remember { Animatable(1f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(
                onClick = onPlay,
                pressedScale = MotionTokens.RowPressScale
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Artwork for ${item.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pressable(
                        pressedScale = 0.80f,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pressable(
                        pressedScale = 0.80f,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showOptions = true
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showOptions) {
        SongOptionsSheet(
            song = song,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onRemoveFromHistory = onDelete,
            onAddToPlaylist = onAddToPlaylist,
            onLike = onToggleLike,
            onDismiss = { showOptions = false }
        )
    }
}
