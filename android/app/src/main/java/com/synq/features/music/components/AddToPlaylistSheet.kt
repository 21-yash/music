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
import com.synq.ui.theme.PinkGradientHero
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

private enum class PlaylistSortMode(val label: String) {
    DEFAULT("Default"),
    NAME("Alphabetical (A-Z)"),
    SONG_COUNT("Most Songs"),
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_CREATED("Recently Created")
}

/**
 * Add-to-Playlist bottom sheet (§3.2, §5: Reference visual north star).
 * Token swap only — appearance remains visually equivalent / pixel-similar to baseline.
 */
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
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlaylistSortMode.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val bgCard = MaterialTheme.colorScheme.surfaceContainerHigh
    val searchBg = MaterialTheme.colorScheme.surfaceContainerHighest
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
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.s)
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
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(textSecondary.copy(alpha = 0.4f))
                )

                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Done",
                        color = PinkPrimary,
                        style = MaterialTheme.typography.labelLarge
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = textPrimary
                )

                TextButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "New playlist",
                        color = PinkPrimary,
                        style = MaterialTheme.typography.labelLarge
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
                                    .clip(MaterialTheme.shapes.medium)
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
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textPrimary),
                                        singleLine = true,
                                        cursorBrush = SolidColor(PinkPrimary),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Find a playlist",
                                                    color = textSecondary.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort button
                            Box {
                                TextButton(
                                    onClick = { showSortMenu = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Sort",
                                        color = textSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    containerColor = bgCard
                                ) {
                                    PlaylistSortMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = mode.label,
                                                    color = if (sortMode == mode) PinkPrimary else textPrimary,
                                                    style = MaterialTheme.typography.bodyMedium
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
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // ─── 3. SEARCH RESULTS OR UNSAVED ITEMS ───
                if (searchQuery.isNotBlank()) {
                    // Search mode: show matching Liked Songs if applicable
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

                    // Search mode: matching playlists
                    if (matchingSearchResults.isEmpty() && !matchesLikedSongs) {
                        item(key = "no_search_results") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No playlists found",
                                    color = textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
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
                                    if (isSaved) {
                                        onRemoveFromPlaylist(playlist)
                                    } else {
                                        onSelectPlaylist(playlist)
                                    }
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }
                } else {
                    // Default mode: Unsaved Liked Songs row (if not already liked)
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

                    // Default mode: Unsaved playlists
                    items(unsavedPlaylists, key = { "unsaved_${it.serverId}" }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            isSaved = false,
                            onToggle = { onSelectPlaylist(playlist) },
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    if (playlists.isEmpty() && !isLiked) {
                        item(key = "empty_playlists") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No playlists yet. Create one above!",
                                    color = textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                .clip(MaterialTheme.shapes.small)
                .background(PinkGradientHero),
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
                style = MaterialTheme.typography.titleMedium,
                color = textPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Auto playlist",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
        }

        if (isLiked) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PinkPrimary),
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
                    .clip(MaterialTheme.shapes.small)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(PinkGradientHero),
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
                style = MaterialTheme.typography.titleMedium,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${playlist.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
        }

        if (isSaved) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PinkPrimary),
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
