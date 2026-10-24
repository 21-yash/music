package com.synq.features.music

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.synq.features.music.components.SongItem
import com.synq.features.music.jam.LocalIsInJam
import com.synq.ui.isAppDark

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
    var showAllTopSongs by remember { mutableStateOf(false) }
    var expandedBio by remember { mutableStateOf(false) }

    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = BrandPink
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.error ?: "Unknown error",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loadArtist(artistId) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPink)
                ) {
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val artist = uiState.artist
            if (artist != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 1. Immersive Hero Section with Pink Glow & Parallax Vibe
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                        ) {
                            if (!artist.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artist.imageUrl,
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
                                        fontWeight = FontWeight.Black,
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
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            // Subtle Pink Ambient Aura at bottom
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                BrandPink.copy(alpha = 0.18f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Top Controls Bar (Back & Share)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val shareUrl = "https://21yash.online/artist/${artist.id}"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, "Listen to ${artist.name} on MusicApp\n$shareUrl")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Artist"))
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Artist Title & Verified Badge at the bottom of hero
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BrandPink.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = BrandPink,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "VERIFIED ARTIST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPink,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = artist.name,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    lineHeight = 40.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (artist.followerCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${artist.followerCount.formatFollowers()} monthly listeners",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 2. Playback & Action Controls (Pink Play Button, Shuffle, Radio, Share)
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Big Pink Play Button
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(10.dp, CircleShape, spotColor = BrandPink.copy(alpha = 0.5f))
                                    .clip(CircleShape)
                                    .background(PinkGradient)
                                    .clickable {
                                        if (artist.topSongs.isNotEmpty()) {
                                            musicViewModel.setShuffle(false)
                                            musicViewModel.playQueue(artist.topSongs, 0)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Top Tracks",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Shuffle Button
                            FilledTonalIconButton(
                                onClick = {
                                    if (artist.topSongs.isNotEmpty()) {
                                        musicViewModel.setShuffle(true)
                                        musicViewModel.playQueue(artist.topSongs.shuffled(), 0)
                                    }
                                },
                                modifier = Modifier.size(46.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isDark) Color(0xFF1E1724) else Color(0xFFF3E8EE)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Start Artist Radio Button
                            Button(
                                onClick = { musicViewModel.startArtistRadio(artist) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPink.copy(alpha = 0.12f),
                                    contentColor = BrandPink
                                ),
                                border = BorderStroke(1.dp, BrandPink.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(23.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Artist Radio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // 3. Top Songs Section (Numbered Ranking 1-5 or 1-10)
                    if (artist.topSongs.isNotEmpty()) {
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
                                    text = "Popular Tracks",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (artist.topSongs.size > 5) {
                                    TextButton(onClick = { showAllTopSongs = !showAllTopSongs }) {
                                        Text(
                                            text = if (showAllTopSongs) "Show Less" else "See All (${artist.topSongs.size})",
                                            color = BrandPink,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            val displayedSongs = if (showAllTopSongs) artist.topSongs else artist.topSongs.take(5)
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                displayedSongs.forEachIndexed { index, song ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 14.sp,
                                            fontWeight = if (index < 3) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (index < 3) BrandPink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.width(28.dp)
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
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
                                                onAddToQueue = { musicViewModel.addToQueue(song) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Discography / Top Albums
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
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${artist.albums.size} releases",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(artist.albums.size) { index ->
                                    val album = artist.albums[index]
                                    Card(
                                        modifier = Modifier
                                            .width(148.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onNavigateToAlbum(album.id) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF1B1622) else Color(0xFFFBF5F8)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(128.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isDark) Color(0xFF261E2E) else Color(0xFFEBE0E6)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!album.imageUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = album.imageUrl,
                                                        contentDescription = album.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Album,
                                                        contentDescription = null,
                                                        tint = BrandPink.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = album.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
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

                    // 5. About / Bio Section
                    if (artist.bio.isNotBlank()) {
                        item {
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "About",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1B1622) else Color(0xFFFBF5F8)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(BrandPink.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = BrandPink,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = artist.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            if (artist.followerCount > 0) {
                                                Text(
                                                    text = "${artist.followerCount.formatFollowers()} monthly listeners",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = artist.bio,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expandedBio) Int.MAX_VALUE else 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (artist.bio.length > 180) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (expandedBio) "Show Less" else "Read More",
                                            color = BrandPink,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.clickable { expandedBio = !expandedBio }
                                        )
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
