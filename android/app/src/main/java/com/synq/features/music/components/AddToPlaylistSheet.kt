package com.synq.features.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.features.music.domain.model.Song
import com.synq.ui.SynqPink
import com.synq.ui.SynqPinkGradient
import com.synq.ui.isAppDark

private enum class PlaylistSortMode(val label: String) {
    DEFAULT("Default"),
    NAME("Alphabetical (A-Z)"),
    SONG_COUNT("Most Songs"),
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_CREATED("Recently Created")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<PlaylistEntity>,
    savedPlaylistIds: Set<String> = emptySet(),
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onRemoveFromPlaylist: (PlaylistEntity) -> Unit = {},
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isAppDark()
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlaylistSortMode.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val bgCard = if (isDark) Color(0xFF18131B) else MaterialTheme.colorScheme.surface
    val searchBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // Sorted and filtered playlists
    val sortedPlaylists = remember(playlists, sortMode) {
        when (sortMode) {
            PlaylistSortMode.DEFAULT -> playlists
            PlaylistSortMode.NAME -> playlists.sortedBy { it.name.lowercase() }
            PlaylistSortMode.SONG_COUNT -> playlists.sortedByDescending { it.songCount }
            PlaylistSortMode.RECENTLY_UPDATED -> playlists.sortedByDescending { it.updatedAt }
            PlaylistSortMode.RECENTLY_CREATED -> playlists.sortedByDescending { it.createdAt }
        }
    }

    val savedPlaylists = remember(sortedPlaylists, savedPlaylistIds) {
        sortedPlaylists.filter { savedPlaylistIds.contains(it.serverId) }
    }

    val unsavedPlaylists = remember(sortedPlaylists, savedPlaylistIds) {
        sortedPlaylists.filter { !savedPlaylistIds.contains(it.serverId) }
    }

    val matchesLikedSongs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            false
        } else {
            "liked songs".contains(searchQuery.trim(), ignoreCase = true) ||
                "liked".contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val matchingSearchResults = remember(sortedPlaylists, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            sortedPlaylists.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: Cancel (left), drag handle (center), Done (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = textSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f))
                )

                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Done",
                        color = SynqPink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title row: "Saved in" + "New playlist"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved in",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                TextButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "New playlist",
                        color = SynqPink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ─── 1. SAVED ITEMS (Shown ABOVE the search bar) ───
                if (searchQuery.isBlank()) {
                    // Liked Songs row if already liked
                    if (isLiked) {
                        item(key = "saved_liked_songs") {
                            LikedSongRow(
                                isLiked = true,
                                onToggle = onToggleLike,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }

                    // Playlists where the song is already saved
                    items(savedPlaylists, key = { "saved_${it.serverId}" }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            isSaved = true,
                            onToggle = { onRemoveFromPlaylist(playlist) },
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }

                // ─── 2. SEARCH & SORT BAR ───
                item(key = "search_and_sort_bar") {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(searchBg)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = TextStyle(
                                            color = textPrimary,
                                            fontSize = 14.sp
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(SynqPink),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Find playlist",
                                                    color = textSecondary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = textSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(19.dp),
                                    color = searchBg,
                                    modifier = Modifier
                                        .height(38.dp)
                                        .clickable { showSortMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Sort",
                                            color = textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(bgCard)
                                ) {
                                    PlaylistSortMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    mode.label,
                                                    color = if (sortMode == mode) SynqPink else textPrimary,
                                                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                sortMode = mode
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // ─── 3. ITEMS BELOW SEARCH BAR ───
                if (searchQuery.isBlank()) {
                    // Liked Songs row if NOT liked yet
                    if (!isLiked) {
                        item(key = "unsaved_liked_songs") {
                            LikedSongRow(
                                isLiked = false,
                                onToggle = onToggleLike,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }

                    // Remaining playlists where the song is not yet saved
                    items(unsavedPlaylists, key = { "unsaved_${it.serverId}" }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            isSaved = false,
                            onToggle = { onSelectPlaylist(playlist) },
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    // "+ New playlist" row item
                    item(key = "new_playlist_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(searchBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SynqPink,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = "New playlist",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = textPrimary
                            )
                        }
                    }
                } else {
                    // When searching: display all matching results
                    if (matchesLikedSongs) {
                        item(key = "search_liked_songs") {
                            LikedSongRow(
                                isLiked = isLiked,
                                onToggle = onToggleLike,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }

                    if (!matchesLikedSongs && matchingSearchResults.isEmpty()) {
                        item(key = "no_search_results") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No playlists found for \"$searchQuery\"",
                                    color = textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(matchingSearchResults, key = { "search_${it.serverId}" }) { playlist ->
                            val isSaved = savedPlaylistIds.contains(playlist.serverId)
                            PlaylistRow(
                                playlist = playlist,
                                isSaved = isSaved,
                                onToggle = {
                                    if (isSaved) onRemoveFromPlaylist(playlist)
                                    else onSelectPlaylist(playlist)
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Synq Pink Done Button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SynqPink,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Existing App Create Playlist Dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            defaultName = "Playlist #${playlists.size + 1}",
            onDismiss = { showCreateDialog = false },
            onCreate = { name, _ ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun LikedSongRow(
    isLiked: Boolean,
    onToggle: () -> Unit,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SynqPinkGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked Songs",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = textPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Auto playlist",
                fontSize = 12.sp,
                color = textSecondary
            )
        }

        if (isLiked) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(SynqPink),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Saved in Liked Songs",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, textSecondary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Liked Songs",
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    isSaved: Boolean,
    onToggle: () -> Unit,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!playlist.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = playlist.name,
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
                    .background(SynqPinkGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${playlist.songCount} songs",
                fontSize = 12.sp,
                color = textSecondary
            )
        }

        if (isSaved) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(SynqPink),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Saved in playlist",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, textSecondary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to playlist",
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

val LocalAddToPlaylist = androidx.compose.runtime.compositionLocalOf<(Song) -> Unit> { {} }
