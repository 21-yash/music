package com.musicsportsapp.features.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicsportsapp.features.music.components.SongItem
import coil.compose.AsyncImage

@Composable
fun MusicScreen(
    viewModel: MusicViewModel = hiltViewModel(),
    onNavigateToSearch: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                .height(44.dp), // Same height as Avatar to align vertically
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Discover Music",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp, top = 24.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    // Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .clickable { onNavigateToSearch() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Search JioSaavn or YouTube...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Filter Pills
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterPill("Pop", Color(0xFFEC4899))
                        FilterPill("Hip-Hop", Color(0xFF6366F1))
                        FilterPill("Bollywood", Color(0xFF10B981))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterPill("Punjabi", Color(0xFFF59E0B))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            val featured = uiState.homeData?.featuredReleases
            if (!featured.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(0.dp)) // Already added 32.dp spacer above
                    SectionTitle("Featured Releases")
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(featured.size) { index ->
                            val album = featured[index]
                            SquareCard(
                                title = album.title,
                                imageUrl = album.imageUrl ?: "",
                                badgeText = "NEW",
                                onClick = { onNavigateToAlbum(album.id) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(0.dp))
                    SectionTitle("Featured Releases (Mock)")
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            SquareCard(
                                title = "Making Memories",
                                imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80",
                                badgeText = "NEW"
                            )
                        }
                        item {
                            SquareCard(
                                title = "Late Night Chill Vibes",
                                imageUrl = "https://images.unsplash.com/photo-1516280440502-861111617266?w=400&q=80",
                                badgeText = "NEW"
                            )
                        }
                    }
                }
            }

            val popularArtists = uiState.homeData?.popularArtists
            if (!popularArtists.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("Popular Artists")
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(popularArtists.size) { index ->
                            val artist = popularArtists[index]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(90.dp)
                                    .clickable {
                                        onNavigateToArtist(artist.id)
                                    }
                            ) {
                                AsyncImage(
                                    model = artist.imageUrl,
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("Popular Artists (Mock)")
                    Spacer(modifier = Modifier.height(16.dp))
                    val artists = listOf(
                        Pair("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_002_20230323062147_500x500.jpg"),
                        Pair("Karan Aujla", "https://c.saavncdn.com/artists/Karan_Aujla_005_20231025062147_500x500.jpg"),
                        Pair("Badshah", "https://c.saavncdn.com/artists/Badshah_005_20230608062147_500x500.jpg"),
                        Pair("Shreya Ghoshal", "https://c.saavncdn.com/artists/Shreya_Ghoshal_002_20230323062147_500x500.jpg")
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(artists.size) { index ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(90.dp)
                            ) {
                                AsyncImage(
                                    model = artists[index].second,
                                    contentDescription = artists[index].first,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = artists[index].first,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            val bestOfList = uiState.homeData?.bestOf
            if (!bestOfList.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("Best Of")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(bestOfList.size) { index ->
                            val playlist = bestOfList[index]
                            SquareCard(
                                title = playlist.title,
                                imageUrl = playlist.imageUrl ?: "",
                                onClick = { onNavigateToPlaylist(playlist.id) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("Best Of (Mock)")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val mockBestOf = listOf(
                        Pair("Best of Romance", "https://c.saavncdn.com/editorial/BestOfRomanceHindi_20260901103522_500x500.jpg"),
                        Pair("Best of Retro", "https://c.saavncdn.com/editorial/BestOfRetroHindi_20230913103522_500x500.jpg"),
                        Pair("Best of 2000s", "https://c.saavncdn.com/editorial/BestOf2000sHindi_20230913103522_500x500.jpg")
                    )
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(mockBestOf.size) { index ->
                            val playlist = mockBestOf[index]
                            SquareCard(
                                title = playlist.first,
                                imageUrl = playlist.second
                            )
                        }
                    }
                }
            }

            val topPlaylists = uiState.homeData?.topPlaylists
            if (!topPlaylists.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("Top Playlists")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        topPlaylists.take(5).forEach { playlist ->
                            PlaylistCard(
                                title = playlist.title,
                                subtitle = "JioSaavn",
                                imageUrl = playlist.imageUrl ?: "",
                                onClick = { onNavigateToPlaylist(playlist.id) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("Top Playlists (Mock)")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlaylistCard(
                            title = "Top JioSaavn Hindi",
                            subtitle = "Weekly Top 50",
                            imageUrl = "https://c.saavncdn.com/editorial/TopJioSaavnHindi_20240322135008.jpg"
                        )
                        PlaylistCard(
                            title = "Punjabi Hits",
                            subtitle = "Ultimate Punjabi Bangers",
                            imageUrl = "https://c.saavncdn.com/editorial/PunjabiHits_20240322135008.jpg"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(title: String, subtitle: String, imageUrl: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun FilterPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.1f))
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SquareCard(title: String, imageUrl: String, badgeText: String? = null, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier.width(140.dp).clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.size(140.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
            
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Fixed height ensures 1-line and 2-line texts take exactly the same space!
        Box(modifier = Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
