package com.synq.features.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import coil.compose.AsyncImage
import com.synq.RootViewModel
import com.synq.data.local.db.entity.FollowedTeamEntity
import com.synq.data.local.db.entity.LikedSongEntity
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.data.local.db.entity.RecentlyPlayedEntity
import com.synq.features.music.components.CreatePlaylistDialog
import com.synq.features.music.domain.model.AlbumRef
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.Song
import com.synq.ui.AppHeader
import com.synq.ui.components.SectionHeader
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.*

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
    val isSportsEnabled by viewModel.isSportsEnabled.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val showCreateDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    val likedSongIds = remember(likedSongs) { likedSongs.map { it.songId }.toSet() }

    val lazyListState = rememberLazyListState()
    val chipsListState = rememberLazyListState()
    val visibleTabs = remember(isSportsEnabled) {
        if (isSportsEnabled) LibraryTab.entries else LibraryTab.entries.filter { it != LibraryTab.TEAMS }
    }
    LaunchedEffect(selectedTab, visibleTabs) {
        val idx = visibleTabs.indexOf(selectedTab)
        if (idx >= 0) {
            chipsListState.animateScrollToItem(idx)
        }
    }
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 24
        }
    }
    val borderAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.35f else 0f,
        label = "chipsBorderAlpha"
    )
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    if (playlistToDelete != null) {
        val targetPlaylist = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = {
                Text(
                    text = "Delete Playlist",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${targetPlaylist.name}\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
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
                    com.synq.ui.toast.SynqToastManager.show(
                        if (success) "Added to ${playlist.name}" else "Failed to add song",
                        if (success) com.synq.ui.toast.SynqToastType.SUCCESS else com.synq.ui.toast.SynqToastType.ERROR
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 240.dp)
        ) {
            // ─── 1. App Header (Scrolls naturally with content, identical to MusicScreen) ───
            item(key = "library_app_header") {
                AppHeader(
                    title = "Your Library",
                    subtitle = if (isSportsEnabled) "Playlists, liked songs & teams" else "Playlists & liked songs",
                    rootViewModel = rootViewModel,
                    navController = navController
                )
            }

            // ─── 2. Filter Chips (stickyHeader, pins to top on scroll) ───
            stickyHeader(key = "sticky_library_chips") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .drawBehind {
                            if (borderAlpha > 0f) {
                                drawLine(
                                    color = outlineVariantColor.copy(alpha = outlineVariantColor.alpha * borderAlpha),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    LazyRow(
                        state = chipsListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(visibleTabs, key = { it.name }) { tab ->
                            val isSelected = selectedTab == tab
                            val countText = when (tab) {
                                LibraryTab.ALL -> "All"
                                LibraryTab.PLAYLISTS -> "Playlists (${playlists.size})"
                                LibraryTab.LIKED -> "Liked ($likedCount)"
                                LibraryTab.HISTORY -> "History (${recentlyPlayed.size})"
                                LibraryTab.TEAMS -> "Teams (${followedTeams.size})"
                            }
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .pressable(
                                        onClick = { viewModel.setTab(tab) },
                                        pressedScale = MotionTokens.ChipPressScale
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) PinkPrimary else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ─── 3. Liked Songs Section (if All or Liked) ───
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.LIKED) {
                item(key = "liked_songs_header") {
                    SectionHeader(
                        title = "Liked Songs",
                        subtitle = "$likedCount songs in cloud library",
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = Spacing.headerToContent)
                    )
                }

                item(key = "liked_songs_card") {
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
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                if (selectedTab == LibraryTab.LIKED) {
                    if (likedSongs.isEmpty()) {
                        item(key = "empty_liked") {
                            EmptyState(
                                icon = Icons.Default.FavoriteBorder,
                                title = "No liked songs yet",
                                subtitle = "Heart songs you enjoy to listen here anytime",
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    } else {
                        items(likedSongs, key = { "liked_${it.id}" }) { song ->
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
                                onAddToPlaylist = { rootViewModel.showAddToPlaylist(songModel) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }

            // ─── 4. User Playlists Section (if All or Playlists) ───
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.PLAYLISTS) {
                item(key = "playlists_header") {
                    SectionHeader(
                        title = "Your Playlists",
                        subtitle = if (playlists.isNotEmpty()) "${playlists.size} created" else null,
                        actionText = if (playlists.isNotEmpty()) "See all" else null,
                        onActionClick = {
                            if (onNavigateToAllPlaylists != null) {
                                onNavigateToAllPlaylists()
                            } else {
                                navController.navigate(com.synq.navigation.Screen.AllPlaylists.route)
                            }
                        },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = if (selectedTab == LibraryTab.ALL) Spacing.sectionGap else 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }

                val displayPlaylists = if (selectedTab == LibraryTab.ALL) playlists.take(5) else playlists
                items(displayPlaylists, key = { "pl_${it.serverId}" }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick?.invoke(playlist.serverId) },
                        onDelete = { playlistToDelete = playlist },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }

                // Create Playlist Row (below existing playlists; also acts as empty state when 0 playlists)
                item(key = "create_playlist_row") {
                    CreatePlaylistRow(
                        onClick = { viewModel.setCreatePlaylistDialogVisible(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }

                if (selectedTab == LibraryTab.ALL && playlists.size > 5) {
                    item(key = "view_all_playlists_btn") {
                        TextButton(
                            onClick = {
                                if (onNavigateToAllPlaylists != null) {
                                    onNavigateToAllPlaylists()
                                } else {
                                    navController.navigate(com.synq.navigation.Screen.AllPlaylists.route)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "View all ${playlists.size} playlists",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = PinkPrimary
                            )
                        }
                    }
                }
            }

            // ─── 5. Followed Teams (if Sports enabled and All or Teams) ───
            if (isSportsEnabled && (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.TEAMS)) {
                item(key = "teams_header") {
                    SectionHeader(
                        title = "Followed Teams",
                        subtitle = "Cricket notifications and updates",
                        actionText = if (selectedTab == LibraryTab.ALL && followedTeams.size > 5) "See more" else null,
                        onActionClick = { viewModel.setTab(LibraryTab.TEAMS) },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = if (selectedTab == LibraryTab.ALL) Spacing.sectionGap else 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }

                if (followedTeams.isEmpty()) {
                    item(key = "empty_teams") {
                        EmptyState(
                            icon = Icons.Default.SportsCricket,
                            title = "No teams followed",
                            subtitle = "Follow cricket teams from match screens to stay informed",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    val displayTeams = if (selectedTab == LibraryTab.ALL) followedTeams.take(5) else followedTeams
                    items(displayTeams, key = { "team_${it.teamId}" }) { team ->
                        FollowedTeamItem(
                            team = team,
                            onUnfollow = { viewModel.unfollowTeam(team.teamId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            // ─── 6. Recently Played History ───
            if (selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.HISTORY) {
                item(key = "recent_header") {
                    SectionHeader(
                        title = "Recently Played",
                        actionText = if (recentlyPlayed.isNotEmpty()) "See all" else null,
                        onActionClick = { onNavigateToHistory?.invoke() },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = if (selectedTab == LibraryTab.ALL) Spacing.sectionGap else 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }

                if (recentlyPlayed.isEmpty()) {
                    item(key = "empty_recent") {
                        EmptyState(
                            icon = Icons.Default.History,
                            title = "No playback history",
                            subtitle = "Songs you play will appear here automatically",
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    val displayList = if (selectedTab == LibraryTab.ALL) recentlyPlayed.take(5) else recentlyPlayed
                    itemsIndexed(displayList, key = { index, item -> "recent_${item.songId}_$index" }) { _, item ->
                        val isLiked = likedSongIds.contains(item.songId)
                        val songModel = remember(item) {
                            Song(
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
                        }
                        RecentlyPlayedItem(
                            item = item,
                            isLiked = isLiked,
                            onPlay = { viewModel.playRecentlyPlayed(item) },
                            onToggleLike = {
                                viewModel.toggleLike(songModel)
                                com.synq.ui.toast.SynqToastManager.show(
                                    if (isLiked) "Removed from Liked Songs" else "Added to Liked Songs",
                                    if (isLiked) com.synq.ui.toast.SynqToastType.INFO else com.synq.ui.toast.SynqToastType.SUCCESS
                                )
                            },
                            onAddToPlaylist = {
                                rootViewModel.showAddToPlaylist(songModel)
                            },
                            onPlayNext = { rootViewModel.playNext(songModel) },
                            onAddToQueue = { rootViewModel.addToQueue(songModel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )
                    }

                    if (selectedTab == LibraryTab.ALL && recentlyPlayed.size > 5) {
                        item(key = "view_all_history_btn") {
                            TextButton(
                                onClick = { onNavigateToHistory?.invoke() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "View all ${recentlyPlayed.size} played songs",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = PinkPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────

@Composable
private fun CreatePlaylistRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.RowPressScale
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(PinkPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create playlist",
                tint = PinkPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Create Playlist",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LikedSongsCard(
    count: Int,
    onClick: () -> Unit,
    onChevronClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.CardPressScale
            )
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(PinkGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$count tracks saved",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { (onChevronClick ?: onClick).invoke() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open as playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
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
    onAddToPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(onClick = onPlay, pressedScale = MotionTokens.RowPressScale)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(48.dp)
                .pressable(onClick = onUnlike, pressedScale = 0.88f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Unlike",
                tint = PinkPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(48.dp)
                .pressable(onClick = { showOptionsSheet = true }, pressedScale = 0.88f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Song options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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
    modifier: Modifier = Modifier
) {
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(onClick = onPlay, pressedScale = MotionTokens.RowPressScale)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
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
                    fontSize = 13.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable(onClick = { showOptionsSheet = true }, pressedScale = 0.88f),
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
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
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
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
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
                    items(playlists, key = { it.serverId }) { playlist ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable(
                                    onClick = { onSelectPlaylist(playlist) },
                                    pressedScale = MotionTokens.RowPressScale
                                )
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(PinkGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(onClick = onClick, pressedScale = MotionTokens.RowPressScale)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!playlist.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(PinkGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${playlist.songCount} songs",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable(onClick = onDelete, pressedScale = 0.88f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FollowedTeamItem(
    team: FollowedTeamEntity,
    onUnfollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (!team.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = team.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = team.shortName.take(3),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = team.teamName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = team.sport.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .height(32.dp)
                .pressable(onClick = onUnfollow, pressedScale = MotionTokens.ChipPressScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Following",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PinkPrimary.copy(alpha = 0.75f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
