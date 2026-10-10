package com.musicsportsapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicsportsapp.features.music.components.SongItem

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            ) {
                BasicTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    decorationBox = { innerTextField ->
                        if (uiState.query.isEmpty()) {
                            Text(
                                text = if (uiState.provider == "jiosaavn") "Searching JioSaavn..." else "Searching YouTube...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            val isJioSaavn = uiState.provider == "jiosaavn"
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(22.dp))
                    .clickable { 
                        viewModel.updateProvider(if (isJioSaavn) "youtube" else "jiosaavn") 
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isJioSaavn) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = com.musicsportsapp.R.drawable.saavn),
                            contentDescription = "JioSaavn",
                            modifier = Modifier
                                .size(if (isJioSaavn) 32.dp else 24.dp)
                                .clip(CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (!isJioSaavn) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = com.musicsportsapp.R.drawable.yt),
                            contentDescription = "YouTube",
                            modifier = Modifier
                                .size(if (!isJioSaavn) 32.dp else 24.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.query.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "Type to start searching...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        } else {
            SearchResultsContent(
                uiState = uiState,
                onSongClick = { viewModel.playSong(it) }
            )
        }
    }
}

@Composable
fun SearchResultsContent(
    uiState: SearchUiState,
    onSongClick: (com.musicsportsapp.features.music.domain.model.Song) -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = uiState.error, color = Color(0xFFEF4444))
        }
        return
    }

    val results = uiState.results
    if (results == null || (results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty())) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "No results found for \"${uiState.query}\"",
                fontSize = 15.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 32.dp)
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp) // Space for MiniPlayer
    ) {
        if (results.songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            items(results.songs) { song ->
                SongItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
fun BrowseCategories() {
    Text(
        text = "Browse Categories",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp),
    )

    Spacer(modifier = Modifier.height(12.dp))

    val categories = listOf(
        "Bollywood", "Pop", "Rock", "Hip-Hop",
        "Classical", "Jazz", "EDM", "Indie",
        "Cricket", "Football", "Tennis", "Basketball",
    )

    val categoryColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(categories.size) { index ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = categoryColors[index % categoryColors.size].copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = categories[index],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
