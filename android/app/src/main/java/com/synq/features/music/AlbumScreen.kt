package com.synq.features.music

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.synq.R
import com.synq.features.music.components.SongItem
import com.synq.features.music.jam.LocalIsInJam
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary

@Composable
fun AlbumScreen(
    albumId: String,
    viewModel: AlbumViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isInJam = LocalIsInJam.current
    val isDark = isAppDark()
    val listState = rememberLazyListState()

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

    val density = LocalDensity.current
    val artworkFadeDistancePx = remember(density) { with(density) { 320.dp.toPx() } }
    // The title text is ~40dp into Item 1 (after badge row + spacer), and Item 1 has offset(y = -20.dp)
    // So the title is at roughly artworkHeight(380) - 20(offset) + badgeRow(~20) + spacer(10) ≈ 390dp from list top
    // The sticky header sits under the status bar (~24dp) + padding (~6dp) ≈ 30dp
    // Header should appear when: totalScroll ≈ titlePositionInList - statusBarArea
    // We track titleY via onGloballyPositioned for pixel-perfect timing
    var titleTopY by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    // Status bar height + header padding zone
    val headerTriggerZonePx = remember(density) { with(density) { 56.dp.toPx() } }

    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / artworkFadeDistancePx).coerceIn(0f, 1f)
            }
        }
    }

    // Dynamic cinematic effects for artwork:
    // 1. Zoom/Scale: recedes smoothly from 1.0f to 0.86f as user scrolls down
    val artworkScale by remember {
        derivedStateOf {
            1.0f - (scrollProgress * 0.14f)
        }
    }

    // 2. Parallax: artwork glides at ~42% scroll speed for multi-plane depth
    val artworkParallaxY by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.42f
            } else {
                0f
            }
        }
    }

    // 3. Cinematic non-linear dissolve curve
    val artworkAlpha by remember {
        derivedStateOf {
            (1f - scrollProgress * scrollProgress).coerceIn(0f, 1f)
        }
    }

    // 4. Scrollable top buttons fade as they scroll up towards the status bar
    val scrollableButtonsAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                (1f - (listState.firstVisibleItemScrollOffset.toFloat() / (artworkFadeDistancePx * 0.45f))).coerceIn(0f, 1f)
            }
        }
    }

    // Sticky header appears when the on-screen title reaches the status bar zone
    // titleTopY is the absolute Y position of the title text on screen, updated via onGloballyPositioned
    val isHeaderVisible by remember {
        derivedStateOf {
            titleTopY <= headerTriggerZonePx
        }
    }

    // Play button in the sticky header appears when item 2 (playback controls) starts scrolling off
    val isHeaderPlayButtonVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
            (listState.firstVisibleItemIndex == 2 && listState.firstVisibleItemScrollOffset > 0)
        }
    }

    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            SynqLoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                size = 48.dp,
                barCount = 5
            )
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = PinkPrimary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Unable to load album",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error ?: "Unknown error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.loadAlbum(albumId) },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = CircleShape,
                    modifier = Modifier.pressable(pressedScale = MotionTokens.ButtonPressScale)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        } else {
            val album = uiState.album
            if (album != null) {
                // Calculate total duration
                val totalSeconds = album.songs.sumOf { it.durationSeconds }
                val durationFormatted = if (totalSeconds > 0) {
                    val minutes = totalSeconds / 60
                    if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes mins"
                } else null

                // Sticky Top Bar (appears a bit early, with back arrow, title, and play button together)
                AnimatedVisibility(
                    visible = isHeaderVisible,
                    enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(150)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .zIndex(20f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Arrow in sticky header
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .pressable(
                                        onClick = onBackClick,
                                        pressedScale = MotionTokens.ButtonPressScale
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Album Name in sticky header
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Play button in sticky header appears later, when on-screen play button goes past above the screen
                            AnimatedVisibility(
                                visible = isHeaderPlayButtonVisible,
                                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(48.dp)
                                        .pressable(
                                            onClick = {
                                                if (album.songs.isNotEmpty()) {
                                                    if (isInJam) {
                                                        musicViewModel.playSong(album.songs.first())
                                                    } else {
                                                        musicViewModel.playQueue(album.songs, 0)
                                                    }
                                                }
                                            },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(PinkGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = lazyBottomPadding)
                ) {
                    // 1. Hero Artwork Header
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                        ) {
                            // Artwork container with cinematic scale, parallax glide, and non-linear dissolve
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = artworkScale
                                        scaleY = artworkScale
                                        translationY = artworkParallaxY
                                        alpha = artworkAlpha
                                    }
                            ) {
                                // Artwork Image
                                if (!album.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = album.imageUrl,
                                        contentDescription = album.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = PinkPrimary.copy(alpha = 0.4f),
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                }

                                // Dark Gradient Scrim Overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.Black.copy(alpha = 0.4f),
                                                    0.45f to Color.Transparent,
                                                    0.80f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                                    1.0f to MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )

                                // Subtle bottom ambient glow in PinkPrimary
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    PinkPrimary.copy(alpha = 0.12f),
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )
                            }

                            // Scrollable Back Arrow (fades as it scrolls away)
                            Box(
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(start = 8.dp, top = 6.dp)
                                    .align(Alignment.TopStart)
                                    .graphicsLayer {
                                        alpha = scrollableButtonsAlpha
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                        .pressable(
                                            onClick = onBackClick,
                                            pressedScale = MotionTokens.ButtonPressScale
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Album Details & Metadata
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .offset(y = (-20).dp)
                        ) {
                            // Badge Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(PinkPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, PinkPrimary.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "ALBUM",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = PinkPrimary
                                    )
                                }

                                if (album.language.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = album.language.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (album.year.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = album.year,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Title
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 34.sp,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    titleTopY = coordinates.positionInWindow().y
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Clickable Artist Credits
                            if (album.artists.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(album.artists.size) { index ->
                                        val artist = album.artists[index]
                                        Row(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), CircleShape)
                                                .pressable(
                                                    onClick = {
                                                        val navId = if (artist.id.isNotBlank()) artist.id else artist.name
                                                        onNavigateToArtist(navId)
                                                    },
                                                    pressedScale = MotionTokens.ChipPressScale
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!artist.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = artist.imageUrl,
                                                    contentDescription = artist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Song count & duration subtitle
                            val metaText = buildString {
                                append("${album.songCount} ${if (album.songCount == 1) "Song" else "Songs"}")
                                if (durationFormatted != null) {
                                    append(" • $durationFormatted")
                                }
                            }
                            Text(
                                text = metaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Playback Controls Dock
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Big Vibrant Pink Play Button
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .pressable(
                                        onClick = {
                                            if (album.songs.isNotEmpty()) {
                                                if (isInJam) {
                                                    musicViewModel.playSong(album.songs.first())
                                                } else {
                                                    musicViewModel.playQueue(album.songs, 0)
                                                }
                                            }
                                        },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(PinkGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play Album",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Shuffle Button
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .pressable(
                                        onClick = {
                                            if (album.songs.isNotEmpty()) {
                                                val shuffled = album.songs.shuffled()
                                                if (isInJam) {
                                                    musicViewModel.playSong(shuffled.first())
                                                } else {
                                                    musicViewModel.playQueue(shuffled, 0)
                                                }
                                            }
                                        },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), CircleShape)
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shuffle_rounded),
                                    contentDescription = "Shuffle",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Shuffle",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Share Action
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pressable(
                                        onClick = {
                                            val shareUrl = "https://21yash.online/album/${album.id}"
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_TEXT, "Listen to \"${album.title}\" on Synq\n$shareUrl")
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Album"))
                                        },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Album",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 4. Track List Section (Without track numbering, full width clean SongItem rows)
                    if (album.songs.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tracks",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${album.songs.size} total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(album.songs.size, key = { album.songs[it].id }) { index ->
                            val song = album.songs[index]
                            SongItem(
                                song = song,
                                onClick = {
                                    if (isInJam) {
                                        musicViewModel.playSong(song)
                                    } else {
                                        musicViewModel.playQueue(album.songs, index)
                                    }
                                },
                                onPlayNext = { musicViewModel.playNext(song) },
                                onAddToQueue = { musicViewModel.addToQueue(song) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                }
            }
        }
    }
}
