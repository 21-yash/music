package com.synq.features.music

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.features.music.components.LocalAddToPlaylist
import com.synq.features.music.domain.model.Song
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    songId: String,
    provider: String? = null,
    viewModel: SongDetailViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isAppDark()
    val localAddToPlaylist = LocalAddToPlaylist.current

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

    LaunchedEffect(songId, provider) {
        viewModel.loadSong(songId, provider)
    }

    val song = uiState.song
    val isLiked by if (song != null) {
        viewModel.isSongLiked(song.id).collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Song Info",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
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
                },
                actions = {
                    if (song != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pressable(
                                    onClick = {
                                        val shareUrl = "https://21yash.online/track/${song.id}"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, "${song.title} by ${song.primaryArtistName}\n$shareUrl")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                                    },
                                    pressedScale = MotionTokens.ButtonPressScale
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                SynqLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    size = 48.dp,
                    barCount = 5
                )
            } else if (uiState.error != null && song == null) {
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
                        text = "Unable to load song details",
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
                        onClick = { viewModel.loadSong(songId) },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                        shape = CircleShape,
                        modifier = Modifier.pressable(pressedScale = MotionTokens.ButtonPressScale)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            } else if (song != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = lazyBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // 1. Hero Artwork with Ambient Glow & Title
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(210.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Ambient soft glow
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .blur(32.dp)
                                        .background(PinkPrimary.copy(alpha = 0.22f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            shape = MaterialTheme.shapes.extraLarge
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (song.album != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Album: ${song.album.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // 2. Action Bar (Play, Like, Add to Playlist, Add to Queue)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Big Play Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressable(
                                        onClick = { viewModel.playSong(song) },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(PinkGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            // Like Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pressable(
                                        onClick = {
                                            viewModel.toggleLike(song)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        if (isLiked) PinkPrimary.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isLiked) PinkPrimary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (isLiked) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Add to Playlist Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pressable(
                                        onClick = { localAddToPlaylist(song) },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddCircleOutline,
                                    contentDescription = "Add to playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Add to Queue Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pressable(
                                        onClick = {
                                            viewModel.addToQueue(song)
                                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                        },
                                        pressedScale = MotionTokens.ButtonPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = "Add to queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // 3. Credited Artists Row (Clickable with Fallback Avatar & Real Navigation)
                    if (song.artists.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "Artists (${song.artists.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(
                                        song.artists,
                                        key = { index, artist -> "${artist.id.ifBlank { artist.name }}_$index" }
                                    ) { _, artist ->
                                        ArtistChip(
                                            name = artist.name,
                                            imageUrl = artist.imageUrl,
                                            onClick = {
                                                val target = if (artist.id.isNotBlank()) artist.id else artist.name
                                                if (target.isNotBlank()) {
                                                    onNavigateToArtist(target)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Album Card (Clickable with Pink Affordance)
                    if (song.album != null) {
                        item {
                            Column {
                                Text(
                                    text = "Album",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable(
                                            onClick = {
                                                val targetAlbum = if (song.album.id.isNotBlank()) song.album.id else song.album.title
                                                if (targetAlbum.isNotBlank()) {
                                                    onNavigateToAlbum(targetAlbum)
                                                }
                                            },
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(song.album.imageUrl ?: song.imageUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = song.album.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.album.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "View Album",
                                                color = PinkPrimary,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = PinkPrimary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Complete Metadata Details Grid
                    item {
                        Column {
                            Text(
                                text = "Track Details",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    MetadataRow(
                                        icon = Icons.Outlined.Timer,
                                        label = "Duration",
                                        value = "${song.formattedDuration} (${song.durationSeconds}s)"
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    if (song.year.isNotBlank()) {
                                        MetadataRow(
                                            icon = Icons.Outlined.CalendarToday,
                                            label = "Release Year",
                                            value = song.year
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }

                                    if (song.language.isNotBlank()) {
                                        MetadataRow(
                                            icon = Icons.Outlined.Translate,
                                            label = "Language",
                                            value = song.language.replaceFirstChar { it.uppercase() }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }

                                    if (song.label.isNotBlank()) {
                                        MetadataRow(
                                            icon = Icons.Outlined.Business,
                                            label = "Record Label",
                                            value = song.label
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }

                                    if (song.playCount > 0) {
                                        val formattedPlays = remember(song.playCount) {
                                            NumberFormat.getNumberInstance(Locale.US).format(song.playCount)
                                        }
                                        MetadataRow(
                                            icon = Icons.Outlined.Headphones,
                                            label = "Play Count",
                                            value = "$formattedPlays plays"
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }

                                    // Lyrics row with custom pink badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Subtitles,
                                                contentDescription = null,
                                                tint = PinkPrimary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Lyrics",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        if (song.hasLyrics) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(PinkPrimary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "Synchronized LRC",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = PinkPrimary
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Searchable",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    MetadataRow(
                                        icon = Icons.Outlined.CloudQueue,
                                        label = "Audio Provider",
                                        value = song.providerId.replaceFirstChar { it.uppercase() }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Song ID Row with pink copy button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Fingerprint,
                                                contentDescription = null,
                                                tint = PinkPrimary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Song ID",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .pressable(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(song.id))
                                                        Toast.makeText(context, "Copied ID: ${song.id}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    pressedScale = MotionTokens.ButtonPressScale
                                                )
                                                .clip(MaterialTheme.shapes.small)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = song.id,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = PinkPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = PinkPrimary,
                                                modifier = Modifier.size(14.dp)
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

@Composable
private fun ArtistChip(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.ChipPressScale
            )
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = CircleShape
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PinkGradient),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "A",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PinkPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MetadataRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PinkPrimary.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
