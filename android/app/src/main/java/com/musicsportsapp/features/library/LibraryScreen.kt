package com.musicsportsapp.features.library

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
import com.musicsportsapp.RootViewModel
import com.musicsportsapp.ui.AppHeader
import coil.compose.AsyncImage
import com.musicsportsapp.data.local.db.entity.FollowedTeamEntity
import com.musicsportsapp.data.local.db.entity.LikedSongEntity
import com.musicsportsapp.data.local.db.entity.PlaylistEntity
import com.musicsportsapp.data.local.db.entity.RecentlyPlayedEntity
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.features.music.domain.model.ArtistCredit

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
    onNavigateToLogin: (() -> Unit)? = null,
    onNavigateToHistory: (() -> Unit)? = null,
    onPlaylistClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val likedCount by viewModel.likedCount.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val followedTeams by viewModel.followedTeams.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showCreateDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    val likedSongIds = remember(likedSongs) { likedSongs.map { it.songId }.toSet() }

    if (showCreateDialog) {
        CreatePlaylistDialog(
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
            // Guest login callout banner
            if (!isLoggedIn) {
                item {
                    GuestLoginBanner(onLoginClick = { onNavigateToLogin?.invoke() })
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Liked Songs Row (if All or Liked)
            if ((selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.LIKED) && isLoggedIn) {
                item {
                    SectionHeader("Liked Songs", subtitle = "$likedCount songs in cloud library")
                    Spacer(Modifier.height(8.dp))
                    LikedSongsCard(
                        count = likedCount,
                        onClick = { viewModel.setTab(LibraryTab.LIKED) }
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
                            LikedSongItem(
                                song = song,
                                onPlay = { viewModel.playLikedSong(song) },
                                onUnlike = { viewModel.unlikeSong(song.songId, song.providerId) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Recently Played History (Available offline and for guests!)
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
                        RecentlyPlayedItem(
                            item = item,
                            isLoggedIn = isLoggedIn,
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
                                songToAddToPlaylist = songModel
                            }
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

            // User Playlists (if All or Playlists)
            if ((selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.PLAYLISTS) && isLoggedIn) {
                item {
                    SectionHeader(
                        title = "Your Playlists",
                        subtitle = "${playlists.size} created",
                        actionText = "New Playlist",
                        onAction = { viewModel.setCreatePlaylistDialogVisible(true) }
                    )
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
                    items(playlists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick?.invoke(playlist.serverId) },
                            onDelete = { viewModel.deletePlaylist(playlist.serverId) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            // Followed Teams (if All or Teams)
            if ((selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.TEAMS) && isLoggedIn) {
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

            // Bottom padding for player bar
            item {
                Spacer(Modifier.height(100.dp))
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
private fun GuestLoginBanner(onLoginClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = AccentGradient)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sign in to sync your library",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Access your liked songs, custom playlists, and followed teams across all devices.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onLoginClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LikedSongsCard(count: Int, onClick: () -> Unit) {
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
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LikedSongItem(
    song: LikedSongEntity,
    onPlay: () -> Unit,
    onUnlike: () -> Unit
) {
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
        }
    }
}

@Composable
private fun RecentlyPlayedItem(
    item: RecentlyPlayedEntity,
    isLoggedIn: Boolean,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

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
            Box {
                IconButton(
                    onClick = {
                        if (!isLoggedIn) {
                            Toast.makeText(
                                context,
                                "Please log in to like songs or add to playlists",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showMenu = true
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isLiked) "Unlike song" else "Like song") },
                        leadingIcon = {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isLiked) Color(0xFFEC4899) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleLike()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                }
            }
        }
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

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
