package com.synq.features.music

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.features.music.components.SongItem
import com.synq.features.music.jam.LocalIsInJam
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary

@Composable
fun ArtistScreen(
    artistId: String,
    viewModel: ArtistViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isInJam = LocalIsInJam.current
    val isDark = isAppDark()
    val listState = rememberLazyListState()
    var showAllTopSongs by remember { mutableStateOf(false) }
    var expandedBio by remember { mutableStateOf(false) }
    val bioArrowRotation by animateFloatAsState(
        targetValue = if (expandedBio) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bioArrowRotation"
    )

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
    var titleTopY by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var playButtonTopY by remember { mutableFloatStateOf(Float.MAX_VALUE) }
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

    // Dynamic cinematic effects for hero:
    // 1. Receding zoom/scale
    val artworkScale by remember {
        derivedStateOf {
            1.0f - (scrollProgress * 0.14f)
        }
    }

    // 2. Parallax: hero glides at ~42% scroll speed
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

    // 4. Scrollable top buttons fade as they scroll towards status bar
    val scrollableButtonsAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                (1f - (listState.firstVisibleItemScrollOffset.toFloat() / (artworkFadeDistancePx * 0.45f))).coerceIn(0f, 1f)
            }
        }
    }

    // Sticky header appears when the on-screen artist name reaches the status bar zone
    val isHeaderVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || titleTopY <= headerTriggerZonePx
        }
    }

    // Play button in sticky header appears when on-screen play button goes past status bar
    val isHeaderPlayButtonVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || playButtonTopY <= headerTriggerZonePx
        }
    }

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Failed to load artist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.error ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loadArtist(artistId) },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = CircleShape,
                    modifier = Modifier.pressable(pressedScale = MotionTokens.ButtonPressScale)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        } else {
            val artist = uiState.artist
            if (artist != null) {
                // Sticky Top Bar (appears dynamically when on-screen title reaches status bar)
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
                                    .pressable(
                                        onClick = onBackClick,
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape),
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

                            // Artist Name in sticky header
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Play / Artist Radio button in sticky header
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
                                            onClick = { musicViewModel.startArtistRadio(artist) },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(PinkGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Artist Radio",
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
                    // 1. Immersive Hero Section
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(370.dp)
                        ) {
                            // Background artwork container with parallax, scale, and dissolve
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
                                if (!artist.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(artist.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = artist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PinkGradient),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = artist.name.firstOrNull()?.uppercase() ?: "A",
                                            fontSize = 80.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }

                                // Dark fading vignette from bottom
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.Black.copy(alpha = 0.35f),
                                                    0.45f to Color.Transparent,
                                                    0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                                    1.0f to MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                    )

                                // Subtle Pink Ambient Aura at bottom
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    PinkPrimary.copy(alpha = 0.16f),
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )
                            }

                            // Scrollable Top Bar (Back & Share buttons that fade on scroll)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        alpha = scrollableButtonsAlpha
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Scrollable Back Arrow
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .pressable(
                                            onClick = onBackClick,
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Scrollable Share Button
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(48.dp)
                                        .pressable(
                                            onClick = {
                                                val shareUrl = "https://21yash.online/artist/${artist.id}"
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    putExtra(Intent.EXTRA_TEXT, "Listen to ${artist.name} on Synq\n$shareUrl")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Share Artist"))
                                            },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Artist",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Artist Title, Verified Badge, and Play/Radio Button at the bottom of hero
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(PinkPrimary.copy(alpha = 0.18f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Verified",
                                            tint = PinkPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "VERIFIED ARTIST",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.4.sp
                                            ),
                                            color = PinkPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        lineHeight = 38.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                            titleTopY = coordinates.positionInWindow().y
                                        }
                                    )

                                    if (artist.followerCount > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${artist.followerCount.formatFollowers()} monthly listeners",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Big Pink Play Button (Artist Radio) moved to right of artist name
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .onGloballyPositioned { coordinates ->
                                            playButtonTopY = coordinates.positionInWindow().y
                                        }
                                        .pressable(
                                            onClick = { musicViewModel.startArtistRadio(artist) },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(PinkGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Artist Radio",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Popular Tracks Section (No numbering, clean full-width SongItem rows)
                    if (artist.topSongs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Popular Tracks",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (artist.topSongs.size > 5) {
                                    Box(
                                        modifier = Modifier
                                            .pressable(
                                                onClick = { showAllTopSongs = !showAllTopSongs },
                                                pressedScale = MotionTokens.ChipPressScale
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (showAllTopSongs) "Show Less" else "See All (${artist.topSongs.size})",
                                            color = PinkPrimary,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            val displayedSongs = if (showAllTopSongs) artist.topSongs else artist.topSongs.take(5)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                            ) {
                                displayedSongs.forEachIndexed { index, song ->
                                    SongItem(
                                        song = song,
                                        onClick = {
                                            if (isInJam) {
                                                musicViewModel.playSong(song)
                                            } else {
                                                musicViewModel.playQueue(artist.topSongs, index)
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

                    // 3. Discography / Top Albums
                    if (artist.albums.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(28.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Discography",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${artist.albums.size} releases",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(artist.albums.size, key = { index -> "${artist.albums[index].id}_$index" }) { index ->
                                    val album = artist.albums[index]
                                    Card(
                                        modifier = Modifier
                                            .width(148.dp)
                                            .pressable(
                                                onClick = { onNavigateToAlbum(album.id) },
                                                pressedScale = MotionTokens.CardPressScale
                                            ),
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(128.dp)
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!album.imageUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(album.imageUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = album.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Album,
                                                        contentDescription = null,
                                                        tint = PinkPrimary.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = album.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. About / Bio Section
                    if (artist.bio.isNotBlank()) {
                        item {
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(PinkPrimary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = PinkPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            if (artist.followerCount > 0) {
                                                Text(
                                                    text = "${artist.followerCount.formatFollowers()} monthly listeners",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = artist.bio,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 22.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expandedBio) Int.MAX_VALUE else 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (artist.bio.length > 180) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .pressable(
                                                    onClick = { expandedBio = !expandedBio },
                                                    pressedScale = MotionTokens.ChipPressScale
                                                )
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (expandedBio) "Show Less" else "Read More",
                                                color = PinkPrimary,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = PinkPrimary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .graphicsLayer {
                                                        rotationZ = bioArrowRotation
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

private fun Int.formatFollowers(): String {
    if (this < 1000) return this.toString()
    if (this < 1000000) return String.format("%.1fK", this / 1000f)
    return String.format("%.1fM", this / 1000000f)
}
