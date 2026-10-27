package com.synq.features.library

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.synq.RootViewModel
import com.synq.ui.AppHeader
import coil.compose.AsyncImage
import com.synq.data.local.db.entity.FollowedTeamEntity
import com.synq.data.local.db.entity.LikedSongEntity
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.data.local.db.entity.RecentlyPlayedEntity
import com.synq.features.music.domain.model.Song
import com.synq.features.music.domain.model.AlbumRef
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.components.CreatePlaylistDialog

private val AccentGradient = Brush.linearGradient(
    listOf(Color(0xFF6366F1), Color(0xFFEC4899))
)

private val CardGradient = Brush.linearGradient(
    listOf(Color(0xFF1E1B4B).copy(alpha = 0.5f), Color(0xFF312E81).copy(alpha = 0.3f))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel(),
    onNavigateToHistory: (() -> Unit)? = null,
    onNavigateToAllPlaylists: (() -> Unit)? = null,
    onPlaylistClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val likedCount by viewModel.likedCount.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val followedTeams by viewModel.followedTeams.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showCreateDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    val likedSongIds = remember(likedSongs) { likedSongs.map { it.songId }.toSet() }

    if (playlistToDelete != null) {
        val targetPlaylist = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = {
                Text(
                    text = "Delete Playlist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${targetPlaylist.name}\"? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(targetPlaylist.serverId)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            defaultName = "Playlist #${playlists.size + 1}",
            onDismiss = { viewModel.setCreatePlaylistDialogVisible(false) },
            onCreate = { name, desc -> viewModel.createPlaylist(name, desc) }
        )
    }

    if (songToAddToPlaylist != null) {
        AddToPlaylistDialog(
            songTitle = songToAddToPlaylist!!.title,
            playlists = playlists,
            onDismiss = { songToAddToPlaylist = null },
            onCreateNewPlaylist = {
                songToAddToPlaylist = null
                viewModel.setCreatePlaylistDialogVisible(true)
            },
            onSelectPlaylist = { playlist ->
                val song = songToAddToPlaylist!!
                songToAddToPlaylist = null
                viewModel.addSongToPlaylist(playlist.serverId, song) { success ->
                    Toast.makeText(
                        context,
                        if (success) "Added to ${playlist.name}" else "Failed to add song",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppHeader(
                    title = "Your Library",
                    subtitle = "Playlists, liked songs & teams",
                    rootViewModel = rootViewModel,
                    navController = navController
                )

                // Filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 12.dp)
                ) {
                    items(LibraryTab.values()) { tab ->
                        val isSelected = selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setTab(tab) },
                            label = {
                                Text(
                                    when (tab) {
                                        LibraryTab.ALL -> "All"
                                        LibraryTab.PLAYLISTS -> "Playlists (${playlists.size})"
                                        LibraryTab.LIKED -> "Liked (${likedCount})"
                                        LibraryTab.HISTORY -> "History (${recentlyPlayed.size})"
                                        LibraryTab.TEAMS -> "Teams (${followedTeams.size})"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 1. Liked Songs Row (if All or Liked)
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.LIKED) {
                item {
                    SectionHeader("Liked Songs", subtitle = "$likedCount songs in cloud library")
                    Spacer(Modifier.height(8.dp))
                    LikedSongsCard(
                        count = likedCount,
                        onClick = {
                            if (onPlaylistClick != null) {
                                onPlaylistClick("liked")
                            } else {
                                viewModel.setTab(LibraryTab.LIKED)
                            }
                        },
                        onChevronClick = {
                            onPlaylistClick?.invoke("liked")
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (selectedTab == LibraryTab.LIKED) {
                    if (likedSongs.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.FavoriteBorder,
                                title = "No liked songs yet",
                                subtitle = "Heart songs you enjoy to listen here anytime"
                            )
                        }
                    } else {
                        items(likedSongs) { song ->
                            val songModel = remember(song) {
                                Song(
                                    id = song.songId,
                                    title = song.title,
                                    artists = listOf(ArtistCredit(id = song.artist, name = song.artist, imageUrl = null)),
                                    album = song.albumId?.takeIf { it.isNotBlank() }?.let { AlbumRef(id = it) },
                                    durationSeconds = song.durationSeconds,
                                    imageUrl = song.imageUrl,
                                    year = "",
                                    language = "",
                                    hasLyrics = false,
                                    playCount = 0,
                                    label = "",
                                    streamRef = song.songId,
                                    providerId = song.providerId,
                                    addedAt = song.likedAt
                                )
                            }
                            LikedSongItem(
                                song = song,
                                onPlay = { viewModel.playLikedSong(song) },
                                onUnlike = { viewModel.unlikeSong(song.songId, song.providerId) },
                                onPlayNext = { rootViewModel.playNext(songModel) },
                                onAddToQueue = { rootViewModel.addToQueue(songModel) },
                                onAddToPlaylist = { rootViewModel.showAddToPlaylist(songModel) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // 2. User Playlists (if All or Playlists)
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.PLAYLISTS) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Your Playlists",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${playlists.size} created",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setCreatePlaylistDialogVisible(true) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (playlists.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                TextButton(
                                    onClick = {
                                        if (onNavigateToAllPlaylists != null) {
                                            onNavigateToAllPlaylists()
                                        } else {
                                            navController.navigate(com.synq.navigation.Screen.AllPlaylists.route)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "See all",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (playlists.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.PlaylistPlay,
                            title = "No playlists created",
                            subtitle = "Create customized playlists to organize your favorite music"
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else {
                    val displayPlaylists = if (selectedTab == LibraryTab.ALL) playlists.take(5) else playlists
                    items(displayPlaylists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick?.invoke(playlist.serverId) },
                            onDelete = { playlistToDelete = playlist }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (selectedTab == LibraryTab.ALL && playlists.size > 5) {
                        item {
                            TextButton(
                                onClick = {
                                    if (onNavigateToAllPlaylists != null) {
                                        onNavigateToAllPlaylists()
                                    } else {
                                        navController.navigate(com.synq.navigation.Screen.AllPlaylists.route)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View all ${playlists.size} playlists")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // 3. Followed Teams (if All or Teams)
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.TEAMS) {
                item {
                    SectionHeader("Followed Teams", subtitle = "Cricket notifications and updates")
                    Spacer(Modifier.height(8.dp))
                }

                if (followedTeams.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.SportsCricket,
                            title = "No teams followed",
                            subtitle = "Follow cricket teams from match screens to stay informed"
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else {
                    items(followedTeams) { team ->
                        FollowedTeamItem(
                            team = team,
                            onUnfollow = { viewModel.unfollowTeam(team.teamId) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // 4. Recently Played History (Available offline and for guests!) - Below Playlists and Teams
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.HISTORY) {
                item {
                    SectionHeader(
                        title = "Recently Played",
                        actionText = "See all",
                        onAction = { onNavigateToHistory?.invoke() }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (recentlyPlayed.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.History,
                            title = "No playback history",
                            subtitle = "Songs you play will appear here automatically"
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else {
                    val displayList = recentlyPlayed.take(5)
                    items(displayList) { item ->
                        val isLiked = likedSongIds.contains(item.songId)
                        val songModel = Song(
                            id = item.songId,
                            title = item.title,
                            artists = listOf(ArtistCredit(id = item.artist, name = item.artist, imageUrl = null)),
                            album = item.albumId?.takeIf { it.isNotBlank() }?.let { AlbumRef(id = it, title = "") },
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
                        RecentlyPlayedItem(
                            item = item,
                            isLiked = isLiked,
                            onPlay = { viewModel.playRecentlyPlayed(item) },
                            onToggleLike = {
                                viewModel.toggleLike(songModel)
                                Toast.makeText(
                                    context,
                                    if (isLiked) "Removed from Liked Songs" else "Added to Liked Songs",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onAddToPlaylist = {
                                rootViewModel.showAddToPlaylist(songModel)
                            },
                            onPlayNext = { rootViewModel.playNext(songModel) },
                            onAddToQueue = { rootViewModel.addToQueue(songModel) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (recentlyPlayed.size > 5) {
                        item {
                            TextButton(
                                onClick = { onNavigateToHistory?.invoke() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View all ${recentlyPlayed.size} played songs")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // Bottom padding for player bar & bottom nav
            item {
                Spacer(Modifier.height(180.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
private fun LikedSongsCard(
    count: Int,
    onClick: () -> Unit,
    onChevronClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Liked Songs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$count tracks saved", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { (onChevronClick ?: onClick).invoke() }) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open as playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LikedSongItem(
    song: LikedSongEntity,
    onPlay: () -> Unit,
    onUnlike: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    val songModel = remember(song) {
        Song(
            id = song.songId,
            title = song.title,
            artists = listOf(ArtistCredit(id = song.artist, name = song.artist, imageUrl = null)),
            album = null,
            durationSeconds = song.durationSeconds,
            imageUrl = song.imageUrl,
            year = "",
            language = "",
            hasLyrics = false,
            playCount = 0,
            label = "",
            streamRef = song.songId,
            providerId = song.providerId,
            addedAt = song.likedAt
        )
    }

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
                model = song.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onUnlike) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Unlike",
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { showOptionsSheet = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showOptionsSheet) {
        com.synq.features.music.components.SongOptionsSheet(
            song = songModel,
            isLiked = true,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onLike = onUnlike,
            onDismiss = { showOptionsSheet = false }
        )
    }
}

@Composable
private fun RecentlyPlayedItem(
    item: RecentlyPlayedEntity,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
) {
    val context = LocalContext.current
    var showOptionsSheet by remember { mutableStateOf(false) }
    val songModel = remember(item) {
        Song(
            id = item.songId,
            title = item.title,
            artists = listOf(ArtistCredit(id = item.artist, name = item.artist, imageUrl = null)),
            album = null,
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
                    .size(48.dp)
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
            IconButton(
                onClick = { showOptionsSheet = true }
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (showOptionsSheet) {
        com.synq.features.music.components.SongOptionsSheet(
            song = songModel,
            isLiked = isLiked,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onLike = onToggleLike,
            onDismiss = { showOptionsSheet = false }
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    songTitle: String,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Add to Playlist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = songTitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            if (playlists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You don't have any playlists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onCreateNewPlaylist()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Playlist")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlaylist(playlist) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.songCount} songs",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PlaylistItem(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.imageUrl != null) {
                AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} songs",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FollowedTeamItem(
    team: FollowedTeamEntity,
    onUnfollow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (team.imageUrl != null) {
                    AsyncImage(
                        model = team.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(team.shortName.take(3), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(team.teamName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(team.sport.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = onUnfollow,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Following", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

