package com.synq.features.music.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.synq.data.local.db.entity.RecentlyPlayedEntity
import com.synq.features.music.components.LocalAddToPlaylist
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.Song

private val AccentGradient = Brush.linearGradient(
    listOf(Color(0xFF6366F1), Color(0xFFEC4899))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningHistoryScreen(
    viewModel: ListeningHistoryViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
) {
    val addToPlaylist = LocalAddToPlaylist.current
    val context = LocalContext.current
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()

    val likedSongIds = remember(likedSongs) { likedSongs.map { it.songId }.toSet() }
    val groupedHistory = remember(recentlyPlayed) {
        recentlyPlayed.groupBy { formatDateHeader(it.playedAt) }
    }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Clear History", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to clear your entire listening history?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearHistory()
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
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
                IconButton(onClick = onBackClick) {
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${recentlyPlayed.size} songs played",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (recentlyPlayed.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
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
        if (recentlyPlayed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No listening history yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedHistory.forEach { (dateHeader, itemsForDate) ->
                    item(key = "header_$dateHeader") {
                        Text(
                            text = dateHeader,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.synq.ui.SynqPink,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                        )
                    }

                    items(itemsForDate, key = { it.id }) { item ->
                        val isLiked = likedSongIds.contains(item.songId)
                        val songModel = remember(item) {
                            Song(
                                id = item.songId,
                                title = item.title,
                                artists = listOf(ArtistCredit(id = item.artist, name = item.artist, imageUrl = null)),
                                album = item.albumId?.takeIf { it.isNotBlank() }?.let { com.synq.features.music.domain.model.AlbumRef(id = it) },
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
                    Spacer(Modifier.height(100.dp))
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
    val context = LocalContext.current
    var showOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showOptions = true },
                    modifier = Modifier.size(36.dp)
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
    }

    if (showOptions) {
        com.synq.features.music.components.SongOptionsSheet(
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

